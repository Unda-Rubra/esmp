package xdu.undarubra.esmp.plugin.settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

/** Vanilla action adapters. Resource worlds and unclaimed Main land retain normal behavior. */
public final class ClaimProtection implements Listener {
    private final SettlementRuntime runtime;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public ClaimProtection(org.bukkit.plugin.java.JavaPlugin plugin, SettlementRuntime runtime) {
        this.runtime = runtime;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player.hasActiveItem() && player.getActiveItem().getType() == Material.BRUSH
                        && !itemTargetAllowed(player, player.getActiveItem())) {
                    player.clearActiveItem();
                    denied(player);
                }
            }
        }, 1L, 1L);
    }

    private boolean allowed(Player player, Location location) {
        return runtime.allows(player == null ? null : player.getUniqueId(), location);
    }

    private boolean protectedAt(Location location) {
        return !runtime.ready() || (runtime.isMain(location.getWorld()) && (!runtime.available(location) || runtime.at(location) != null));
    }

    private void denied(Player player) {
        if (player == null) { return; }
        long now = System.nanoTime();
        Long previous = lastMessage.get(player.getUniqueId());
        if (previous == null || now - previous >= 1_000_000_000L) {
            lastMessage.put(player.getUniqueId(), now);
            player.sendActionBar(Component.text("This action is protected by a Settlement."));
        }
    }

    public static boolean publicControl(Material type) {
        return Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type) || type == Material.LEVER;
    }

    private static boolean worldChangingItem(ItemStack item) {
        if (item == null || item.getType().isAir()) { return false; }
        Material type = item.getType();
        if (type == Material.POTION && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion
                && potion.getBasePotionType() == org.bukkit.potion.PotionType.WATER) { return true; }
        String name = type.name();
        return type.isBlock() || name.endsWith("_BUCKET") || type == Material.BUCKET
                || name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")
                || name.endsWith("_SPAWN_EGG") || name.endsWith("_BOAT") || name.endsWith("_RAFT")
                || name.endsWith("MINECART") || name.endsWith("_DYE")
                || type == Material.BONE_MEAL || type == Material.FLINT_AND_STEEL || type == Material.FIRE_CHARGE
                || type == Material.BRUSH || type == Material.ENDER_EYE || type == Material.LEAD
                || type == Material.SHEARS || type == Material.HONEYCOMB || type == Material.ARMOR_STAND
                || type == Material.END_CRYSTAL || type == Material.GLOW_INK_SAC || type == Material.INK_SAC
                || type == Material.GLASS_BOTTLE || type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageBlock(BlockDamageEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        boolean permitted = allowed(event.getPlayer(), event.getBlockPlaced().getLocation());
        if (event instanceof BlockMultiPlaceEvent multi) {
            permitted &= multi.getReplacedBlockStates().stream().allMatch(block -> allowed(event.getPlayer(), block.getLocation()));
        }
        // Double containers must not bridge independently owned regions.
        if (event.getBlockPlaced().getType() == Material.CHEST || event.getBlockPlaced().getType() == Material.TRAPPED_CHEST) {
            for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                Block neighbor = event.getBlockPlaced().getRelative(face);
                if (neighbor.getType() == event.getBlockPlaced().getType()
                        && !runtime.sameRegion(event.getBlockPlaced().getLocation(), neighbor.getLocation())) { permitted = false; }
            }
        }
        if (!permitted) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            if (worldChangingItem(event.getItem()) && !itemTargetAllowed(event.getPlayer(), event.getItem())) {
                event.setUseItemInHand(Event.Result.DENY);
                denied(event.getPlayer());
            }
            return;
        }
        if (allowed(event.getPlayer(), block.getLocation())) { return; }
        boolean safe = publicControl(block.getType()) && runtime.available(block.getLocation());
        if (event.getAction() == Action.PHYSICAL) {
            if (!safe) { event.setCancelled(true); denied(event.getPlayer()); }
            return;
        }
        if (!safe) { event.setUseInteractedBlock(Event.Result.DENY); }
        if (worldChangingItem(event.getItem())) { event.setUseItemInHand(Event.Result.DENY); }
        if (!safe && (block.getState(false) instanceof InventoryHolder || worldChangingItem(event.getItem()))) { denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (!allowed(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!allowed(event.getPlayer(), event.getHarvestedBlock().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (!allowed(event.getPlayer(), event.getLectern().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player) && !allowed(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true); denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent event) { onEntityInteract(event); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (!allowed(event.getPlayer(), event.getRightClicked().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation()) || !allowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true); denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation()) || !allowed(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true); denied(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Location target = event.getEntity().getLocation();
        if (!protectedAt(target)) { return; }
        if (event instanceof HangingBreakByEntityEvent byEntity) {
            if (!runtime.allows(actor(byEntity.getRemover()), target)) { event.setCancelled(true); }
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation())
                || !allowed(event.getPlayer(), event.getLeashHolder().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        if (!allowed(event.getPlayer(), event.getEntity().getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFishing(PlayerFishEvent event) {
        Entity caught = event.getCaught();
        if (caught != null && !(caught instanceof Player) && !(caught instanceof Item)
                && !allowed(event.getPlayer(), caught.getLocation())) { event.setCancelled(true); denied(event.getPlayer()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!runtime.allows(event.getOwner().getUniqueId(), event.getEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player && !allowed(player, event.getEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && !allowed(player, event.getVehicle().getLocation())) { event.setCancelled(true); denied(player); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (!runtime.allows(actor(event.getAttacker()), event.getVehicle().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (!runtime.allows(actor(event.getAttacker()), event.getVehicle().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim instanceof Player || victim instanceof Item || !protectedAt(victim.getLocation())) { return; }
        Entity causing = event.getDamageSource().getCausingEntity();
        UUID actor = actor(causing);
        if (event instanceof EntityDamageByEntityEvent byEntity && actor == null) { actor = actor(byEntity.getDamager()); }
        if (actor != null) {
            if (!runtime.allows(actor, victim.getLocation())) { event.setCancelled(true); }
        } else if (event instanceof EntityDamageByEntityEvent byEntity) {
            Location source = projectileBlockSource(byEntity.getDamager());
            if (source == null || !runtime.sameRegion(source, victim.getLocation())) { event.setCancelled(true); }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            if (!runtime.sameRegion(event.getDamageSource().getSourceLocation(), victim.getLocation())) { event.setCancelled(true); }
        } else if (event instanceof EntityDamageByBlockEvent byBlock && byBlock.getDamager() != null
                && !runtime.sameRegion(byBlock.getDamager().getLocation(), victim.getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombustEntity(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) && !runtime.allows(actor(event.getCombuster()), event.getEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombustBlock(EntityCombustByBlockEvent event) {
        if (!(event.getEntity() instanceof Player) && event.getCombuster() != null
                && !runtime.sameRegion(event.getCombuster().getLocation(), event.getEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        UUID actor = actor(event.getPotion());
        for (LivingEntity target : event.getAffectedEntities()) {
            if (!(target instanceof Player) && !runtime.allows(actor, target.getLocation())) { event.setIntensity(target, 0); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        UUID actor = projectileActor(event.getEntity().getSource());
        event.getAffectedEntities().removeIf(target -> !(target instanceof Player) && !runtime.allows(actor, target.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity target = event.getHitEntity();
        if (target != null && !(target instanceof Player) && !(target instanceof Item)
                && !runtime.allows(actor(event.getEntity()), target.getLocation())) { event.setCancelled(true); return; }
        Block block = event.getHitBlock();
        Projectile projectile = event.getEntity();
        if (block != null && !runtime.allows(actor(projectile), block.getLocation())
                && !(publicControl(block.getType()) && runtime.available(block.getLocation()))) {
            event.setCancelled(true);
            projectile.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!inventoryAllowed(event.getPlayer().getUniqueId(), event.getInventory())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!inventoryAllowed(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!inventoryAllowed(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!inventoriesShareRegion(event.getSource(), event.getDestination())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        for (Location location : inventoryLocations(event.getInventory())) {
            if (!runtime.sameRegion(event.getItem().getLocation(), location)) { event.setCancelled(true); return; }
        }
    }

    private boolean inventoryAllowed(UUID actor, Inventory inventory) {
        return inventoryLocations(inventory).stream().allMatch(location -> runtime.allows(actor, location));
    }

    private boolean inventoriesShareRegion(Inventory from, Inventory to) {
        List<Location> source = inventoryLocations(from);
        List<Location> target = inventoryLocations(to);
        if (source.isEmpty()) { return target.stream().noneMatch(this::protectedAt); }
        if (target.isEmpty()) { return source.stream().noneMatch(this::protectedAt); }
        for (Location a : source) {
            for (Location b : target) { if (!runtime.sameRegion(a, b)) { return false; } }
        }
        return true;
    }

    public static List<Location> inventoryLocations(Inventory inventory) {
        if (inventory.getHolder() instanceof Player) { return List.of(); }
        var locations = new ArrayList<Location>(2);
        addHolder(locations, inventory.getHolder());
        if (inventory instanceof MerchantInventory merchant && merchant.getMerchant() instanceof Entity entity) {
            locations.add(entity.getLocation());
        }
        if (locations.isEmpty() && inventory.getLocation() != null) { locations.add(inventory.getLocation()); }
        return locations;
    }

    private static void addHolder(List<Location> locations, InventoryHolder holder) {
        if (holder instanceof DoubleChest chest) {
            addHolder(locations, chest.getLeftSide());
            addHolder(locations, chest.getRightSide());
        } else if (holder instanceof BlockState block) { locations.add(block.getLocation()); }
        else if (holder instanceof Entity entity && !(entity instanceof Player)) { locations.add(entity.getLocation()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        if (!runtime.sameRegion(event.getBlock().getLocation(), event.getToBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        // Paper supplies the blocks' push direction (opposite facing for a non-empty retraction).
        if (!pistonAllowed(event.getBlock(), event.getBlocks(), event.getDirection())) { event.setCancelled(true); }
    }

    private boolean pistonAllowed(Block piston, List<Block> blocks, BlockFace direction) {
        BlockFace facing = piston.getBlockData() instanceof Directional data ? data.getFacing() : direction;
        if (!runtime.available(piston.getLocation()) || !runtime.sameRegion(piston.getLocation(), piston.getRelative(facing).getLocation())) { return false; }
        for (Block block : blocks) {
            if (!runtime.sameRegion(piston.getLocation(), block.getLocation())
                    || (block.getPistonMoveReaction() != org.bukkit.block.PistonMoveReaction.BREAK
                    && !runtime.sameRegion(block.getLocation(), block.getRelative(direction).getLocation()))) { return false; }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        UUID actor = actor(event.getEntity());
        event.blockList().removeIf(block -> !runtime.allows(actor, block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !runtime.sameRegion(event.getBlock().getLocation(), block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        Location target = event.getBlock().getLocation();
        if (event.getPlayer() != null) {
            if (!allowed(event.getPlayer(), target)) { event.setCancelled(true); }
        } else if (event.getIgnitingEntity() != null && actor(event.getIgnitingEntity()) != null) {
            if (!runtime.allows(actor(event.getIgnitingEntity()), target)) { event.setCancelled(true); }
        } else {
            Block source = event.getIgnitingBlock();
            if (!runtime.sameRegion(source == null ? null : source.getLocation(), target)) { event.setCancelled(true); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!runtime.sameRegion(event.getSource().getLocation(), event.getBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        Block source = event.getIgnitingBlock();
        if (!runtime.sameRegion(source == null ? null : source.getLocation(), event.getBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (protectedAt(event.getBlock().getLocation())
                && !runtime.sameRegion(event.getSourceBlock().getLocation(), event.getBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Location target = event.getBlock().getLocation();
        if (event.getEntity() instanceof FallingBlock falling) {
            if (!runtime.sameRegion(falling.getOrigin(), target)) { event.setCancelled(true); }
        } else if (!runtime.allows(actor(event.getEntity()), target)) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPhysical(EntityInteractEvent event) {
        if (publicControl(event.getBlock().getType()) && runtime.available(event.getBlock().getLocation())) { return; }
        if (!runtime.allows(actor(event.getEntity()), event.getBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        Block source = event.getBlock();
        if (!runtime.available(source.getLocation())) { event.setCancelled(true); return; }
        if (source.getBlockData() instanceof Directional directional
                && !runtime.sameRegion(source.getLocation(), source.getRelative(directional.getFacing()).getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (!runtime.sameRegion(event.getBlock().getLocation(), event.getTargetEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockShear(BlockShearEntityEvent event) {
        if (!runtime.sameRegion(event.getBlock().getLocation(), event.getEntity().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        for (BlockState state : event.getBlocks()) {
            boolean permitted = event.getPlayer() == null ? runtime.sameRegion(event.getBlock().getLocation(), state.getLocation())
                    : allowed(event.getPlayer(), state.getLocation());
            if (!permitted) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTree(StructureGrowEvent event) {
        for (BlockState state : event.getBlocks()) {
            boolean permitted = event.getPlayer() == null ? runtime.sameRegion(event.getLocation(), state.getLocation())
                    : allowed(event.getPlayer(), state.getLocation());
            if (!permitted) { event.setCancelled(true); return; }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldron(CauldronLevelChangeEvent event) {
        if ((event.getEntity() != null && !runtime.allows(actor(event.getEntity()), event.getBlock().getLocation()))
                || !runtime.available(event.getBlock().getLocation())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(org.bukkit.event.world.PortalCreateEvent event) {
        UUID actor = actor(event.getEntity());
        if (event.getBlocks().stream().anyMatch(block -> !runtime.allows(actor, block.getLocation()))) { event.setCancelled(true); }
    }

    private boolean itemTargetAllowed(Player player, ItemStack item) {
        if (!runtime.ready()) { return false; }
        if (!runtime.isMain(player.getWorld())) { return true; }
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        double range = attribute == null ? 5.0 : attribute.getValue();
        if (!Double.isFinite(range) || range <= 0) { return false; }
        Location eye = player.getEyeLocation();
        var direction = eye.getDirection();
        for (double distance = 0; distance <= range; distance += 1.0) {
            int x = (int) Math.floor(eye.getX() + direction.getX() * distance);
            int z = (int) Math.floor(eye.getZ() + direction.getZ() * distance);
            if (!player.getWorld().isChunkLoaded(x >> 4, z >> 4)) { return false; }
        }
        var hit = player.rayTraceBlocks(range, item.getType() == Material.BRUSH
                ? org.bukkit.FluidCollisionMode.NEVER : org.bukkit.FluidCollisionMode.ALWAYS);
        return hit == null || hit.getHitBlock() == null || allowed(player, hit.getHitBlock().getLocation());
    }

    private static UUID projectileActor(ProjectileSource source) {
        return source instanceof Player player ? player.getUniqueId() : null;
    }

    private static UUID actor(Entity entity) {
        if (entity instanceof Player player) { return player.getUniqueId(); }
        if (entity instanceof Projectile projectile) { return projectileActor(projectile.getShooter()); }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) { return player.getUniqueId(); }
        return null;
    }

    private static Location projectileBlockSource(Entity entity) {
        return entity instanceof Projectile projectile && projectile.getShooter() instanceof BlockProjectileSource block
                ? block.getBlock().getLocation() : null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { lastMessage.remove(event.getPlayer().getUniqueId()); }
}
