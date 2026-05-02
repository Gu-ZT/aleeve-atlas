package dev.dubhe.map.client.cache;

public record BlockCache(ChunkCache chunkCache, byte x, short y, byte z, int color) {
    private static final int X_SHIFT = 28;
    private static final int Y_SHIFT = 16;
    private static final int Z_SHIFT = 12;

    private static final int X_MASK = 0xF;
    private static final int Y_MASK = 0xFFF;
    private static final int Z_MASK = 0xF;
    private static final int COLOR_MASK = 0xFFF;

    public static int pack(int x, int y, int z, int colorIndex) {
        return ((x & X_MASK) << X_SHIFT)
               | ((y & Y_MASK) << Y_SHIFT)
               | ((z & Z_MASK) << Z_SHIFT)
               | (colorIndex & COLOR_MASK);
    }

    public static byte x(int packed) {
        return (byte) ((packed >>> X_SHIFT) & X_MASK);
    }

    public static short y(int packed) {
        int raw = (packed >>> Y_SHIFT) & Y_MASK;
        // Sign-extend 12-bit y to keep negative world heights.
        return (short) ((raw << 20) >> 20);
    }

    public static byte z(int packed) {
        return (byte) ((packed >>> Z_SHIFT) & Z_MASK);
    }

    public static int colorIndex(int packed) {
        return packed & COLOR_MASK;
    }

    public int serialize() {
        int colorIndex = chunkCache.getRegionCache().getColors().indexOf(color);
        if (colorIndex < 0 || colorIndex > COLOR_MASK) {
            throw new IllegalStateException("Color palette index out of 12-bit range: " + colorIndex);
        }
        if (y < -2048 || y > 2047) {
            throw new IllegalStateException("Y out of 12-bit signed range: " + y);
        }
        return BlockCache.pack(x, y, z, colorIndex);
    }

    public static BlockCache deserialize(ChunkCache chunkCache, int packed) {
        int color = chunkCache.getRegionCache().getColors().get(colorIndex(packed));
        return new BlockCache(chunkCache, x(packed), y(packed), z(packed), color);
    }
}
