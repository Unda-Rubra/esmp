package xdu.undarubra.esmp.plugin.settlement;

import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import xdu.undarubra.esmp.civilization.settlement.SettlementEvent;

/** Post-commit, non-cancellable notification. Consumers must deduplicate using fact().id(). */
public final class SettlementCommittedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final SettlementEvent fact;

    public SettlementCommittedEvent(SettlementEvent fact) { this.fact = Objects.requireNonNull(fact); }
    public SettlementEvent fact() { return fact; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
