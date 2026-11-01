package dev.dubhe.map.map;

import dev.dubhe.map.util.NbtValues;
import dev.dubhe.map.config.MapColorScheme;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// 单个区块的地图采样数据。
///
/// 像素保存色板索引和地表高度；色板记录方块状态及重算配色所需的元数据。
public final class ChunkMapData {
    public static final int SIZE = 16;
    public static final int PIXELS = SIZE * SIZE;
    private static final int FORMAT = 1;

    private final int chunkX;
    private final int chunkZ;
    private final List<PixelState> palette;
    private final int[] stateIndices;
    private final int[] heights;
    private long version;
    private MapSlice slice = MapSlice.SURFACE;

    public MapSlice slice() { return this.slice; }
    public void setSlice(MapSlice slice) { this.slice = slice; }

    public ChunkMapData(int chunkX, int chunkZ) {
        this(chunkX, chunkZ, new ArrayList<>(), new int[PIXELS], new int[PIXELS], 0L);
        this.palette.add(PixelState.EMPTY);
    }

    private ChunkMapData(int chunkX, int chunkZ, List<PixelState> palette,
            int[] stateIndices, int[] heights, long version) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.palette = palette;
        this.stateIndices = stateIndices;
        this.heights = heights;
        this.version = version;
    }

    public int chunkX() {
        return this.chunkX;
    }

    public int chunkZ() {
        return this.chunkZ;
    }

    public long version() {
        return this.version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /// 默认按原版配色解析当前像素；其它配色由渲染器显式指定。
    public int colorAt(int localX, int localZ) {
        return ChunkColorizer.colorAt(this, localX, localZ, MapColorScheme.VANILLA,
                null, null, 0);
    }

    public PixelState stateAt(int localX, int localZ) {
        return this.palette.get(this.stateIndices[localZ * SIZE + localX]);
    }

    public int[] heights() {
        return this.heights;
    }

    public int heightAt(int localX, int localZ) {
        return this.heights[localZ * SIZE + localX];
    }

    public int paletteSize() {
        return this.palette.size();
    }

    public List<PixelState> palette() {
        return Collections.unmodifiableList(this.palette);
    }

    /// 写入一列采样结果；相同状态复用同一个色板索引。
    public void setPixel(int localX, int localZ, PixelState state, int height) {
        int paletteIndex = this.palette.indexOf(state);
        if (paletteIndex < 0) {
            paletteIndex = this.palette.size();
            this.palette.add(state);
        }
        int index = localZ * SIZE + localX;
        this.stateIndices[index] = paletteIndex;
        this.heights[index] = height;
    }

    public boolean isEmpty() {
        for (int stateIndex : this.stateIndices) {
            if (stateIndex != 0) {
                return false;
            }
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("f", FORMAT);
        tag.putInt("x", this.chunkX);
        tag.putInt("z", this.chunkZ);
        tag.putLong("v", this.version);
        if (this.slice.cave()) {
            tag.putInt("slice_top", this.slice.topY());
            tag.putInt("slice_depth", this.slice.depth());
        }
        ListTag paletteTag = new ListTag();
        for (PixelState state : this.palette) {
            paletteTag.add(state.save());
        }
        tag.put("p", paletteTag);
        tag.putIntArray("i", this.stateIndices);
        tag.putIntArray("h", this.heights);
        return tag;
    }

    public static ChunkMapData load(CompoundTag tag) {
        if (tag.getInt("f") != FORMAT) {
            throw new IllegalArgumentException("Unsupported chunk map cache format");
        }
        List<PixelState> palette = new ArrayList<>();
        ListTag paletteTag = tag.getList("p", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (Tag entry : paletteTag) {
            if (entry instanceof CompoundTag stateTag) {
                palette.add(PixelState.load(stateTag));
            }
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("Chunk map palette is empty");
        }
        int[] indices = checkedPixels((tag.contains("i", net.minecraft.nbt.Tag.TAG_INT_ARRAY) ? tag.getIntArray("i") : new int[PIXELS]));
        int[] heights = checkedPixels((tag.contains("h", net.minecraft.nbt.Tag.TAG_INT_ARRAY) ? tag.getIntArray("h") : new int[PIXELS]));
        for (int index : indices) {
            if (index < 0 || index >= palette.size()) {
                throw new IllegalArgumentException("Chunk map palette index out of range");
            }
        }
        ChunkMapData data = new ChunkMapData(tag.getInt("x"), tag.getInt("z"),
                palette, indices, heights, NbtValues.getLong(tag, "v", 0L));
        int top = NbtValues.getInt(tag, "slice_top", Integer.MAX_VALUE);
        data.slice = top == Integer.MAX_VALUE ? MapSlice.SURFACE : top == Integer.MIN_VALUE ? MapSlice.FULL_CAVE
                : MapSlice.layered(top, NbtValues.getInt(tag, "slice_depth", 30));
        return data;
    }

    private static int[] checkedPixels(int[] values) {
        if (values.length != PIXELS) {
            throw new IllegalArgumentException("Chunk map pixel array must contain 256 entries");
        }
        return Arrays.copyOf(values, PIXELS);
    }

    public ChunkPos pos() {
        return new ChunkPos(this.chunkX, this.chunkZ);
    }

    /// 每列的方块状态及配色重算信息。
    public record PixelState(BlockState state, MapColor mapColor, MapColor vanillaMapColor,
            BlockState groundState, MapColor groundMapColor, String biomeId, int fluidDepth) {
        public static final PixelState EMPTY = new PixelState(Blocks.AIR.defaultBlockState(),
                MapColor.NONE, MapColor.NONE, Blocks.AIR.defaultBlockState(), MapColor.NONE, "", 0);

        /// 供测试和简单数据构造使用，采用方块的默认地图色。
        public static PixelState of(BlockState state) {
            MapColor mapColor = state.getBlock().defaultMapColor();
            return new PixelState(state, mapColor, mapColor, Blocks.AIR.defaultBlockState(),
                    MapColor.NONE, "minecraft:plains", 0);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("s", NbtUtils.writeBlockState(this.state));
            tag.putInt("m", this.mapColor.id);
            tag.putInt("v", this.vanillaMapColor.id);
            tag.put("g", NbtUtils.writeBlockState(this.groundState));
            tag.putInt("n", this.groundMapColor.id);
            tag.putString("b", this.biomeId);
            tag.putInt("d", this.fluidDepth);
            return tag;
        }

        private static PixelState load(CompoundTag tag) {
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound("s"));
            BlockState ground = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound("g"));
            return new PixelState(state, MapColor.byId(tag.getInt("m")),
                    MapColor.byId(tag.getInt("v")), ground,
                    MapColor.byId(tag.getInt("n")), NbtValues.getString(tag, "b", ""),
                    tag.getInt("d"));
        }
    }
}
