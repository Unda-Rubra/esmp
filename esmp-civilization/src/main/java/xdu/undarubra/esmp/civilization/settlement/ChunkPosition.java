package xdu.undarubra.esmp.civilization.settlement;

public record ChunkPosition(int x, int z) {
    public long key() { return ((long) x << 32) | (z & 0xffffffffL); }
}
