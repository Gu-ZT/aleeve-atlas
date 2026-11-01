package dev.dubhe.map.map;

import dev.dubhe.map.config.MapColorScheme;
import dev.dubhe.map.config.AtlasClientConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;

/// 按当前配色方案把方块状态色板解析为地图像素颜色。
public final class ChunkColorizer {
    private ChunkColorizer() {
    }

    /// 计算区块阴影；原版配色无需阴影数组。
    public static int[] lighting(ChunkMapData data, MapColorScheme scheme) {
        return scheme == MapColorScheme.BIOME_SHADE
                ? BiomeShadePalette.computeLighting(data.heights()) : null;
    }

    /// 解析一个像素；阴影值仅在群系配色方案下使用。
    public static int colorAt(ChunkMapData data, int localX, int localZ,
            MapColorScheme scheme, Level level, BlockPos.MutableBlockPos cursor, int shadow) {
        ChunkMapData.PixelState pixel = data.stateAt(localX, localZ);
        if (pixel.mapColor() == MapColor.NONE && pixel.vanillaMapColor() == MapColor.NONE) {
            return MapPalette.TRANSPARENT;
        }
        int height = data.heightAt(localX, localZ);
        int worldX = data.chunkX() * ChunkMapData.SIZE + localX;
        int worldZ = data.chunkZ() * ChunkMapData.SIZE + localZ;
        if (scheme == MapColorScheme.BIOME_SHADE) {
            if (level == null || cursor == null) {
                throw new IllegalArgumentException("Biome shading requires a level and cursor");
            }
            return caveShade(data, pixel, height, biomeColor(pixel, height, worldX, worldZ, level, cursor, shadow));
        }
        int referenceHeight = localZ == 0 ? height : data.heightAt(localX, localZ - 1);
        MapColor.Brightness brightness = pixel.vanillaMapColor() == MapColor.WATER
                ? MapPalette.brightnessForWater(pixel.fluidDepth(), worldX, worldZ)
                : MapPalette.brightnessForTerrain(height, referenceHeight, worldX, worldZ);
        return caveShade(data, pixel, height, MapPalette.colorOf(pixel.vanillaMapColor(), brightness));
    }

    private static int caveShade(ChunkMapData data, ChunkMapData.PixelState pixel, int height, int color) {
        MapSlice slice = data.slice();
        if (!slice.cave() || slice.fullCave() || !AtlasClientConfigs.CONFIG.caves.depthShading
                || pixel.state().getLightEmission() > 0) return color;
        float light = 1.0F - 0.65F * Math.clamp((slice.topY() - height) / (float) slice.depth(), 0.0F, 1.0F);
        return (color & 0xFF000000) | ((int) (((color >> 16) & 255) * light) << 16)
                | ((int) (((color >> 8) & 255) * light) << 8) | (int) ((color & 255) * light);
    }

    private static int biomeColor(ChunkMapData.PixelState pixel,
            int height, int worldX, int worldZ, Level level,
            BlockPos.MutableBlockPos cursor, int shadow) {
        Holder<Biome> biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .get(net.minecraft.resources.ResourceKey.create(Registries.BIOME, ResourceLocation.parse(pixel.biomeId())))
                .orElseThrow(() -> new IllegalArgumentException("Unknown biome " + pixel.biomeId()));
        cursor.set(worldX, height, worldZ);
        int color = BiomeShadePalette.colorWithBiome(pixel.mapColor().col,
                pixel.state(), biome, cursor);
        if (pixel.mapColor() == MapColor.WATER) {
            int waterColor = BiomeShadePalette.colorWithBiome(MapColor.WATER.col,
                    Blocks.WATER.defaultBlockState(), biome, cursor);
            if (pixel.fluidDepth() <= 15) {
                int groundColor = BiomeShadePalette.colorWithBiome(pixel.groundMapColor().col,
                        pixel.groundState(), biome, cursor);
                color = BiomeShadePalette.blendWaterColor(waterColor, groundColor,
                        pixel.fluidDepth() + 5);
            } else {
                color = waterColor;
            }
        } else if (pixel.state().getFluidState().getType() == Fluids.WATER) {
            int waterColor = BiomeShadePalette.colorWithBiome(MapColor.WATER.col,
                    Blocks.WATER.defaultBlockState(), biome, cursor);
            color = BiomeShadePalette.blendWaterColor(waterColor, color, 5);
        }
        return BiomeShadePalette.applyShadow(color, shadow);
    }
}
