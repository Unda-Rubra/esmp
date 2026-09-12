package xdu.undarubra.esmp.civilization.settlement;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure rules shared by Settlement persistence operations. */
public final class SettlementRules {
    private SettlementRules() {}

    public static String normalize(String name) {
        return LandName.normalize(name);
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new SettlementException(message);
    }

    public static List<ChunkPosition> neighbors(ChunkPosition chunk) {
        return List.of(
                new ChunkPosition(chunk.x() + 1, chunk.z()),
                new ChunkPosition(chunk.x() - 1, chunk.z()),
                new ChunkPosition(chunk.x(), chunk.z() + 1),
                new ChunkPosition(chunk.x(), chunk.z() - 1));
    }

    public static boolean connected(Set<ChunkPosition> chunks) {
        var queue = new ArrayDeque<ChunkPosition>();
        var visited = new HashSet<ChunkPosition>();
        queue.add(chunks.iterator().next());
        while (!queue.isEmpty()) {
            ChunkPosition current = queue.removeFirst();
            if (!visited.add(current)) continue;
            for (ChunkPosition next : neighbors(current)) {
                if (chunks.contains(next) && !visited.contains(next)) queue.addLast(next);
            }
        }
        return visited.size() == chunks.size();
    }

    public static String paymentDetail(ClaimQuote quote) {
        return ";charged=true;quote="
                + quote.id()
                + ";price="
                + quote.amount()
                + ";payer="
                + quote.payer()
                + ";payer_id="
                + quote.payerId()
                + ";assessed_owner="
                + quote.owner();
    }
}
