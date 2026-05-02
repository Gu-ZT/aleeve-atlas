package dev.dubhe.map.client.cache;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ChunkCache {
    private final RegionCache regionCache;
    private final short x;
    private final short z;
    private final BlockCache[][] blocks = new BlockCache[16][16];

    public ChunkCache(RegionCache regionCache, short x, short z) {
        this.regionCache = regionCache;
        this.x = x;
        this.z = z;
    }

    public void addBlock(BlockCache blockCache) {
        blocks[blockCache.x()][blockCache.z()] = blockCache;
    }

    public @Nullable BlockCache getBlock(int x, int z) {
        return blocks[x][z];
    }

    public static int packXz(short x, short z) {
        return ((x & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    public boolean sameData(@Nullable ChunkCache other) {
        if (other == null) {
            return false;
        }
        if (this.x != other.x || this.z != other.z) {
            return false;
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockCache a = this.blocks[x][z];
                BlockCache b = other.blocks[x][z];
                if (a == null && b == null) {
                    continue;
                }
                if (a == null || b == null) {
                    return false;
                }
                if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z() || a.color() != b.color()) {
                    return false;
                }
            }
        }
        return true;
    }

    public IntArrayTag serialize() {
        List<Integer> list = new ArrayList<>();
        list.add(ChunkCache.packXz(this.x, this.z));
        for (BlockCache[] block : this.blocks) {
            for (BlockCache cache : block) {
                if (cache == null) continue;
                list.add(cache.serialize());
            }
        }
        int[] blocks = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            blocks[i] = list.get(i);
        }
        return new IntArrayTag(blocks);
    }

    public static ChunkCache deserialize(RegionCache regionCache, IntArrayTag arrayTag) {
        int packXz = arrayTag.get(0).intValue();
        short x = (short) ((packXz >>> 16) & 0xFFFF);
        short z = (short) (packXz & 0xFFFF);
        ChunkCache chunkCache = new ChunkCache(regionCache, x, z);
        for (int i = 1; i < arrayTag.size(); i++) {
            chunkCache.addBlock(BlockCache.deserialize(chunkCache, arrayTag.get(i).intValue()));
        }
        return chunkCache;
    }

    public static ChunkCache create(RegionCache regionCache, LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        short chunkX = (short) Math.floorMod(chunkPos.x(), 64);
        short chunkZ = (short) Math.floorMod(chunkPos.z(), 64);
        ChunkCache chunkCache = new ChunkCache(regionCache, chunkX, chunkZ);
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos levelBlockPos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                blockPos.set(x, y, z);
                levelBlockPos.set(chunkPos.x() * 16 + x, y, chunkPos.z() * 16 + z);
                BlockState blockState = chunk.getBlockState(blockPos);
                int color = blockState.getMapColor(chunk.getLevel(), levelBlockPos).col;
                regionCache.addColor(color);
                chunkCache.addBlock(new BlockCache(chunkCache, (byte) x, (short) y, (byte) z, color));
            }
        }
        return chunkCache;
    }
}
