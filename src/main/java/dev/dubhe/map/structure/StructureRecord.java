package dev.dubhe.map.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/// 起始区块决定结构身份，显示坐标与包围盒只用于定位和途经判定。
public record StructureRecord(String dimension, StructureKind kind, int chunkX, int chunkZ,
        BlockPos position, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Source source) {
    public enum Source {
        SHARED, SEARCHED, DISCOVERED
    }

    public StructureRecord {
        ResourceLocation.parse(dimension);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(source);
        position = position.immutable();
        if (Math.abs((long) chunkX * 16) > 30_000_000 || Math.abs((long) chunkZ * 16) > 30_000_000
                || minX > maxX || minY > maxY || minZ > maxZ
                || minX < -30_000_000 || maxX > 30_000_000 || minZ < -30_000_000 || maxZ > 30_000_000
                || minY < -2032 || maxY > 2031 || Math.abs((long) position.getX()) > 30_000_000
                || Math.abs((long) position.getZ()) > 30_000_000 || position.getY() < -2032 || position.getY() > 2031) {
            throw new IllegalArgumentException("Invalid structure bounds");
        }
    }

    public String key() {
        return this.kind.structureId() + "/" + this.chunkX + "/" + this.chunkZ;
    }

    public StructureRecord withSource(Source value) {
        return new StructureRecord(this.dimension, this.kind, this.chunkX, this.chunkZ, this.position,
                this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ, value);
    }

    public boolean intersectsChunk(int x, int z) {
        return (long) x * 16 <= this.maxX && (long) x * 16 + 15 >= this.minX
                && (long) z * 16 <= this.maxZ && (long) z * 16 + 15 >= this.minZ;
    }

    public double distanceSquared(double x, double z) {
        double dx = Math.max(Math.max(this.minX - x, 0), x - this.maxX);
        double dz = Math.max(Math.max(this.minZ - z, 0), z - this.maxZ);
        return dx * dx + dz * dz;
    }

    public double positionDistanceSquared(double x, double z) {
        double dx = this.position.getX() - x;
        double dz = this.position.getZ() - z;
        return dx * dx + dz * dz;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", this.dimension);
        tag.putString("kind", this.kind.structureId().toString());
        tag.putInt("chunkX", this.chunkX);
        tag.putInt("chunkZ", this.chunkZ);
        tag.putLong("position", this.position.asLong());
        tag.putIntArray("bounds", new int[]{this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ});
        tag.putString("source", this.source.name());
        return tag;
    }

    public static StructureRecord load(CompoundTag tag) {
        if (!tag.contains("dimension", net.minecraft.nbt.Tag.TAG_STRING)
                || !tag.contains("chunkX", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                || !tag.contains("chunkZ", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)
                || !tag.contains("position", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            throw new IllegalArgumentException("Incomplete structure record");
        }
        int[] bounds = tag.getIntArray("bounds");
        if (bounds.length != 6) {
            throw new IllegalArgumentException("Invalid structure bounds length");
        }
        StructureKind kind = StructureKind.fromId(ResourceLocation.parse(tag.getString("kind")));
        return new StructureRecord(tag.getString("dimension"), Objects.requireNonNull(kind),
                tag.getInt("chunkX"), tag.getInt("chunkZ"),
                BlockPos.of(tag.getLong("position")), bounds[0], bounds[1], bounds[2],
                bounds[3], bounds[4], bounds[5], Source.valueOf(tag.getString("source")));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(this.dimension, 256);
        buffer.writeEnum(this.kind);
        buffer.writeInt(this.chunkX);
        buffer.writeInt(this.chunkZ);
        buffer.writeBlockPos(this.position);
        buffer.writeInt(this.minX);
        buffer.writeInt(this.minY);
        buffer.writeInt(this.minZ);
        buffer.writeInt(this.maxX);
        buffer.writeInt(this.maxY);
        buffer.writeInt(this.maxZ);
        buffer.writeEnum(this.source);
    }

    public static StructureRecord read(FriendlyByteBuf buffer) {
        return new StructureRecord(buffer.readUtf(256), buffer.readEnum(StructureKind.class),
                buffer.readInt(), buffer.readInt(), buffer.readBlockPos(), buffer.readInt(), buffer.readInt(),
                buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readEnum(Source.class));
    }
}
