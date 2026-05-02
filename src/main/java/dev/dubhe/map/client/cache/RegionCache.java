package dev.dubhe.map.client.cache;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

@Getter
public class RegionCache {
    private final int x;
    private final int z;
    private final ChunkCache[][] chunks = new ChunkCache[64][64];
    private final List<Integer> colors = new ArrayList<>();

    public RegionCache(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public void addChunk(ChunkCache chunkCache) {
        chunks[chunkCache.getX()][chunkCache.getZ()] = chunkCache;
    }

    public ChunkCache getChunk(short x, short z) {
        return chunks[x][z];
    }

    public void addChunk(LevelChunk chunk) {
        this.addChunk(ChunkCache.create(this, chunk));
    }

    public void addColor(int color) {
        int index = colors.indexOf(color);
        if (index == -1) {
            colors.add(color);
        }
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        ListTag chunksTag = new ListTag();
        tag.put(
            "pos",
            new IntArrayTag(new int[]{
                this.x,
                this.z
            })
        );
        for (ChunkCache[] chunk : this.chunks) {
            for (ChunkCache chunkCache : chunk) {
                if (chunkCache == null) continue;
                chunksTag.add(chunkCache.serialize());
            }
        }
        tag.put("chunks", chunksTag);
        int[] colors = new int[this.colors.size()];
        for (int i = 0; i < this.colors.size(); i++) {
            colors[i] = this.colors.get(i);
        }
        IntArrayTag colorsTag = new IntArrayTag(colors);
        tag.put("colors", colorsTag);
        return tag;
    }

    public static RegionCache deserialize(CompoundTag tag) {
        int[] pos = tag.getIntArray("pos").orElseThrow();
        RegionCache regionCache = new RegionCache(pos[0], pos[1]);
        ListTag chunksTag = tag.getList("chunks").orElseThrow();
        for (Tag value : chunksTag) {
            if (!(value instanceof IntArrayTag)) continue;
            regionCache.addChunk(ChunkCache.deserialize(regionCache, (IntArrayTag) value));
        }
        int[] colors = tag.getIntArray("colors").orElseThrow();
        for (int color : colors) {
            regionCache.addColor(color);
        }
        return regionCache;
    }
}
