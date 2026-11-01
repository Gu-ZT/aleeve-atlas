package dev.dubhe.map.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** NBT readers that preserve defaults for absent or incompatible values. */
public final class NbtValues {
    private NbtValues() {
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }

    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }

    public static long getLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getLong(key) : fallback;
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getBoolean(key) : fallback;
    }
}
