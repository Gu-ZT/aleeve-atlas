package dev.dubhe.map.cartography;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;

/// 原版地图的独立快照；不伪造方块状态或探索区块。
public record MapSnapshot(int mapId, String dimension, int centerX, int centerZ,
        int scale, int referenceHeight, byte[] colors, List<Target> targets, long importedAt) {
    public static final int PIXELS = 128 * 128;
    public static final int MAX_TARGETS = 256;

    public MapSnapshot {
        if (mapId < 0 || ResourceLocation.tryParse(dimension) == null || scale < 0 || scale > 4
                || Math.abs((long) centerX) > 30_000_000 || Math.abs((long) centerZ) > 30_000_000
                || targets.size() > MAX_TARGETS) {
            throw new IllegalArgumentException("Invalid map snapshot");
        }
        validateColors(colors);
        colors = colors.clone();
        targets = List.copyOf(targets);
    }

    @Override
    public byte[] colors() {
        return this.colors.clone();
    }

    public static void validateColors(byte[] colors) {
        if (colors.length != PIXELS) {
            throw new IllegalArgumentException("Map must contain 16384 pixels");
        }
        for (byte color : colors) {
            int id = Byte.toUnsignedInt(color) >> 2;
            if (id != 0 && MapColor.byId(id) == MapColor.NONE) {
                throw new IllegalArgumentException("Unknown map color");
            }
        }
    }

    public int size() { return 128 << this.scale; }
    public int minX() { return this.centerX - this.size() / 2; }
    public int minZ() { return this.centerZ - this.size() / 2; }

    public byte pixelAt(int x, int z) {
        long dx = (long) x - this.minX();
        long dz = (long) z - this.minZ();
        if (dx < 0 || dz < 0 || dx >= this.size() || dz >= this.size()) return 0;
        return this.colors[((int) dz >> this.scale) * 128 + ((int) dx >> this.scale)];
    }

    public MapSnapshot imported(long time) {
        return new MapSnapshot(this.mapId, this.dimension, this.centerX, this.centerZ,
                this.scale, this.referenceHeight, this.colors, this.targets, time);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.mapId);
        buf.writeUtf(this.dimension, 256);
        buf.writeInt(this.centerX);
        buf.writeInt(this.centerZ);
        buf.writeByte(this.scale);
        buf.writeInt(this.referenceHeight);
        buf.writeByteArray(this.colors);
        buf.writeVarInt(this.targets.size());
        for (Target target : this.targets) {
            buf.writeUtf(target.type, 128);
            buf.writeInt(target.x);
            buf.writeInt(target.z);
        }
    }

    public static MapSnapshot read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String dimension = buf.readUtf(256);
        int x = buf.readInt(), z = buf.readInt(), scale = buf.readUnsignedByte();
        int referenceHeight = buf.readInt();
        byte[] colors = buf.readByteArray(PIXELS);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("Too many map targets");
        List<Target> targets = new ArrayList<>();
        for (int i = 0; i < count; i++) targets.add(new Target(buf.readUtf(128), buf.readInt(), buf.readInt()));
        return new MapSnapshot(id, dimension, x, z, scale, referenceHeight, colors, targets, 0);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", 1);
        tag.putInt("id", this.mapId);
        tag.putString("dimension", this.dimension);
        tag.putInt("x", this.centerX);
        tag.putInt("z", this.centerZ);
        tag.putInt("scale", this.scale);
        tag.putInt("referenceHeight", this.referenceHeight);
        tag.putByteArray("colors", this.colors);
        tag.putLong("imported", this.importedAt);
        ListTag targets = new ListTag();
        for (Target target : this.targets) {
            CompoundTag item = new CompoundTag();
            item.putString("type", target.type);
            item.putInt("x", target.x);
            item.putInt("z", target.z);
            targets.add(item);
        }
        tag.put("targets", targets);
        return tag;
    }

    public static MapSnapshot load(CompoundTag tag) {
        if (tag.getIntOr("format", 0) != 1) throw new IllegalArgumentException("Unknown snapshot format");
        List<Target> targets = new ArrayList<>();
        for (var entry : tag.getListOrEmpty("targets")) {
            if (entry instanceof CompoundTag item) {
                targets.add(new Target(item.getStringOr("type", ""), item.getIntOr("x", 0), item.getIntOr("z", 0)));
            }
        }
        return new MapSnapshot(tag.getIntOr("id", -1), tag.getStringOr("dimension", ""),
                tag.getIntOr("x", 0), tag.getIntOr("z", 0), tag.getIntOr("scale", -1),
                tag.getIntOr("referenceHeight", 63),
                tag.getByteArray("colors").orElse(new byte[0]), targets, tag.getLongOr("imported", 0));
    }

    public record Target(String type, int x, int z) {
        public Target {
            if (ResourceLocation.tryParse(type) == null || Math.abs((long) x) > 30_000_000
                    || Math.abs((long) z) > 30_000_000) throw new IllegalArgumentException("Invalid target");
        }
    }
}
