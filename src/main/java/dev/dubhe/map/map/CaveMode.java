package dev.dubhe.map.map;

import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.CaveMapMode;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/// 检测 3×3 顶棚，不依赖海平面；每列的顶棚可以处在不同高度。
public final class CaveMode {
    private CaveMode() { }

    public static MapSlice detect(Level level, BlockPos player, AtlasClientConfig.Caves config) {
        CaveMapMode mode = level.dimensionType().hasCeiling() ? config.ceilingDimensionMode : config.mode;
        if (mode == CaveMapMode.OFF || player.getY() < level.getMinBuildHeight() || player.getY() >= level.getMaxBuildHeight()) {
            return MapSlice.SURFACE;
        }
        int ceiling = ceilingAbove(level, player);
        if (ceiling == Integer.MAX_VALUE) return MapSlice.SURFACE;
        return mode == CaveMapMode.FULL ? MapSlice.FULL_CAVE
                : MapSlice.layered(Math.min(player.getY() + 4, ceiling), config.depth);
    }

    private static int ceilingAbove(Level level, BlockPos player) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int centerCeiling = Integer.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            int x = player.getX() + dx, z = player.getZ() + dz;
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) return Integer.MAX_VALUE;
            int top = Math.min(level.getMaxBuildHeight() - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15));
            boolean covered = false;
            for (int y = player.getY() + 1; y <= top; y++) {
                cursor.set(x, y, z);
                if (!isRoof(chunk.getBlockState(cursor))) continue;
                covered = true;
                if (dx == 0 && dz == 0) centerCeiling = y;
                break;
            }
            if (!covered) return Integer.MAX_VALUE;
        }
        return centerCeiling;
    }

    /// 树叶、流体和可替换植物不能把露天区域误判成洞穴。
    public static boolean isRoof(BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)
                && !state.is(Blocks.BARRIER) && !state.canBeReplaced() && state.canOcclude();
    }

    /// 地表/洞穴切换至少间隔一秒，避免在洞口反复闪烁；洞内高度变化立即更新。
    public static final class Tracker {
        private MapSlice current;
        private long lastToggle;

        public MapSlice update(MapSlice detected, long tick) {
            if (this.current == null || this.current.cave() != detected.cave() && tick - this.lastToggle >= 20) {
                this.current = detected;
                this.lastToggle = tick;
            } else if (this.current.cave() == detected.cave()) {
                this.current = detected;
            }
            return this.current;
        }

        public void reset() { this.current = null; }
    }
}
