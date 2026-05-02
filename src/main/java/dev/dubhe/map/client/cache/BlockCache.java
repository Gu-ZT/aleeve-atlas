package dev.dubhe.map.client.cache;

public record BlockCache(ChunkCache chunkCache, byte x, byte y, byte z, int color) {
    public static short packXyz(byte x, byte y, byte z) {
        return (short) (((x & 0xF) << 8) | ((y & 0xF) << 4) | (z & 0xF));
    }

    public static byte x(short xyz) {
        return (byte) ((xyz >>> 8) & 0xF);
    }

    public static byte y(short xyz) {
        return (byte) ((xyz >>> 4) & 0xF);
    }

    public static byte z(short xyz) {
        return (byte) (xyz & 0xF);
    }

    public static int pack(short xyz, short color) {
        return ((xyz & 0xFFFF) << 16) | (color & 0xFFFF);
    }

    public static short xyz(int pack) {
        return (short) ((pack >>> 16) & 0xFFFF);
    }

    public static short color(int pack) {
        return (short) (pack & 0xFFFF);
    }

    public int serialize() {
        int colorIndex = chunkCache.getRegionCache().getColors().indexOf(color);
        return BlockCache.pack(BlockCache.packXyz(x, y, z), (short) colorIndex);
    }

    public static BlockCache deserialize(ChunkCache chunkCache, int pack) {
        short colorIndex = color(pack);
        int color = chunkCache.getRegionCache().getColors().get(colorIndex);
        return new BlockCache(chunkCache, x(xyz(pack)), y(xyz(pack)), z(xyz(pack)), color);
    }
}
