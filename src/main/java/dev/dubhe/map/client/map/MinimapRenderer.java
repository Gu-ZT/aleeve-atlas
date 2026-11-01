package dev.dubhe.map.client.map;

import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.client.radar.RadarRenderer;
import dev.dubhe.map.client.territory.TerritoryRenderer;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.MinimapPosition;
import dev.dubhe.map.config.MinimapShape;
import dev.dubhe.map.ping.PingRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/// 小地图 HUD 渲染。
///
/// 绘制分两步：先铺整张地图纹理（玩家恒在纹理中心），再叠加
/// {@link AtlasRenderPipelines#MINIMAP_OVERLAY} 着色器层，由片元着色器解析式地
/// 画出圆形遮罩、边框与水滴形玩家指示器。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class MinimapRenderer {
    private static final ResourceLocation OVERLAY_TEXTURE =
            AleeveAtlas.id("textures/gui/white.png");
    private static final int BORDER_EXPANSION_PIXELS = 3;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private MinimapRenderer() {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(AleeveAtlas.id("minimap"), (graphics, partialTick) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        AtlasClientConfig config = AtlasClientConfigs.CONFIG;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (!config.minimap.enabled || minecraft.options.hideGui) {
            return;
        }
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager == null) {
            return;
        }
        MapTexture texture = manager.texture();
        if (!texture.ready()) {
            return;
        }
        final long startedAt = System.nanoTime();

        MinimapLayout layout = MinimapLayout.of(config, minecraft);
        if (layout == null) {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        int x = layout.x();
        int y = layout.y();
        int size = layout.size();

        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        LocalPlayer player = minecraft.player;
        // 北向锁定：地图固定北朝上、指示器随视角转；
        // 视角随动：地图绕中心旋转到玩家朝向、指示器固定朝上。
        float rotationDegrees = mapRotationDegrees(player.getYRot(), config.minimap.lockNorth);
        float centerX = x + size / 2.0F;
        float centerY = y + size / 2.0F;

        final Function<ResourceLocation, RenderType> mapPipeline = circular
                ? AtlasRenderPipelines.MINIMAP_CIRCLE
                : AtlasRenderPipelines.GUI_TEXTURED;

        // 方形形态旋转后四角会越出小地图边界，需用裁剪区限制。
        // 裁剪必须在旋转前启用：enableScissor 按当前 pose 变换矩形，
        // 旋转后传入的轴对齐矩形会被变换成负宽高或错位区域。
        boolean scissored = !circular;
        int clipInset = MinimapLayout.CLIP_INSET;
        if (scissored) {
            graphics.enableScissor(x + clipInset, y + clipInset, x + size - clipInset, y + size - clipInset);
        }
        graphics.pose().pushPose();
        if (rotationDegrees != 0.0F) {
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().mulPose(new org.joml.Quaternionf().rotationZ((float) Math.toRadians(rotationDegrees)));
            graphics.pose().translate(-centerX, -centerY, 0.0F);
        }

        // 方形按对角线尺寸绘制后由内缩 scissor 裁剪，圆形由 shader 按同一内缩半径裁剪。
        int drawSize = circular ? size : MinimapLayout.squareDrawSize(size);
        float drawX = centerX - drawSize / 2.0F;
        float drawY = centerY - drawSize / 2.0F;
        int uvSize = circular ? (int) layout.coveredBlocks()
                : MinimapLayout.squareDrawSize((int) layout.coveredBlocks());
        float uvStartX = (float) texture.uvStartX(layout.centerWorldX(), uvSize);
        float uvStartZ = (float) texture.uvStartZ(layout.centerWorldZ(), uvSize);
        float brightness = lightBrightness(minecraft, config);
        int mapTint = circular ? encodeCircleTint(brightness, clipInset, size)
                : brightnessColor(brightness);
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0.0F);
        AtlasGui.blit(graphics, mapPipeline, texture.textureId(), 0, 0, uvStartX, uvStartZ,
                drawSize, drawSize, uvSize, uvSize, MapTexture.PIXELS, MapTexture.PIXELS, mapTint);
        graphics.pose().popPose();
        TerritoryRenderer.renderOnMinimap(graphics, layout, circular);
        ChunkOverlayRenderer.renderOnMinimap(graphics, config, layout, circular);
        graphics.pose().popPose();
        if (scissored) {
            graphics.disableScissor();
        }

        // 标记位置随地图旋转，图标与文字在屏幕坐标中保持正向。
        if (scissored) {
            graphics.enableScissor(x + clipInset, y + clipInset,
                    x + size - clipInset, y + size - clipInset);
        }
        RadarRenderer.render(graphics, config, layout, minecraft, rotationDegrees);
        dev.dubhe.map.client.structure.StructureRenderer.minimap(graphics, layout, circular, rotationDegrees);
        WaypointRenderer.renderOnMinimap(graphics, layout, circular, rotationDegrees);
        PingRenderer.renderOnMinimap(graphics, layout, circular, rotationDegrees);
        if (scissored) {
            graphics.disableScissor();
        }

        int overlaySize = size + BORDER_EXPANSION_PIXELS * 2;
        int overlayX = x - BORDER_EXPANSION_PIXELS;
        int overlayY = y - BORDER_EXPANSION_PIXELS;
        int tint = encodeOverlayTint(circular, size,
                BORDER_EXPANSION_PIXELS);
        AtlasGui.blit(graphics, AtlasRenderPipelines.MINIMAP_OVERLAY, OVERLAY_TEXTURE,
                overlayX, overlayY, 0.0F, 0.0F, overlaySize, overlaySize,
                2, 2, 2, 2, tint);

        PlayerIndicatorRenderer.draw(graphics, config.minimap.playerIndicator,
                x + size / 2d, y + size / 2d, player.getYRot(), rotationDegrees,
                PlayerIndicatorRenderer.minimapScale(layout));

        CompassRenderer.renderOnMinimap(graphics, layout, rotationDegrees);
        TerritoryRenderer.renderLabelsOnMinimap(graphics, layout, circular, rotationDegrees);
        if (!MinimapLayout.focused(minecraft)) {
            drawInfo(graphics, config, minecraft, size, x, y);
        }
        graphics.flush();
        graphics.pose().popPose();
        // 记录渲染耗时，供性能验收读取。
        AtlasProfiling.recordMinimap(
                System.nanoTime() - startedAt);
    }

    /// 地图旋转角：视角随动时把玩家朝向旋转到屏幕上方。
    /// Minecraft yaw=0 朝南，因此需要包含 180° 的南北基准。
    static float mapRotationDegrees(float playerYaw, boolean lockNorth) {
        return lockNorth ? 0.0F : 180.0F - playerYaw;
    }

    /// 圆形遮罩由顶点局部坐标定位，顶点色只传亮度和内缩比例。
    private static int encodeCircleTint(float brightness, int clipInset,
            int drawSize) {
        int green = Math.round(brightness * 255.0F);
        int blue = (int) Math.ceil(2.0F * clipInset / drawSize * 255.0F);
        return 0xFFFF0000 | (green << 8) | blue;
    }

    private static int brightnessColor(float brightness) {
        int channel = Math.round(brightness * 255.0F);
        return 0xFF000000 | (channel << 16) | (channel << 8) | channel;
    }

    private static float lightBrightness(Minecraft minecraft, AtlasClientConfig config) {
        if (!config.minimap.lightBrightness || minecraft.level == null
                || minecraft.player == null) {
            return 1.0F;
        }
        int light = minecraft.level.getMaxLocalRawBrightness(minecraft.player.blockPosition());
        float normalized = Mth.clamp(light / 15.0F, 0.0F, 1.0F);
        return 0.35F + 0.65F * normalized;
    }

    /// 边框使用 G 通道传递圆形开关，A 通道传递向外扩展的比例。
    private static int encodeOverlayTint(boolean circular, int mapSize, int borderExpansion) {
        int green = circular ? 255 : 0;
        int alpha = Math.round(borderExpansion * 2.0F / mapSize * 255.0F);
        int blue = (int) Math.ceil((MinimapLayout.CLIP_INSET + 1) * 2.0F / mapSize * 255.0F);
        return (alpha << 24) | (green << 8) | blue;
    }

    /// 小地图是否贴屏幕底部：此时信息覆层改画在其上方。
    ///
    /// 按小地图底边到屏幕底部的距离判断，兼容四角预设与自定义偏移。
    private static boolean isBottomPosition(MinimapPosition position, int y, int size,
            Minecraft minecraft) {
        if (position == MinimapPosition.BOTTOM_LEFT || position == MinimapPosition.BOTTOM_RIGHT) {
            return true;
        }
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        return y + size + 4 + 10 > screenHeight;
    }

    /// 当前配置下将绘制多少行信息。
    private static int countInfoLines(AtlasClientConfig config, Minecraft minecraft) {
        int lines = 0;
        if (config.minimap.showCoords) {
            lines++;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            lines++;
        }
        if (config.minimap.showLight && minecraft.level != null) {
            lines++;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            lines++;
        }
        return lines;
    }

    private static void drawInfo(GuiGraphics graphics, AtlasClientConfig config, Minecraft minecraft,
            int size, int x, int y) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        // 小地图贴屏幕底部时把信息绘制在其上方，否则文字会超出屏幕下边界。
        boolean above = isBottomPosition(config.minimap.position, y, size, minecraft);
        int lineCount = countInfoLines(config, minecraft);
        int line = above
                ? y - 4 - lineCount * 10
                : y + size + 4;
        int textX = x + size / 2;
        if (config.minimap.showCoords) {
            Component text = Component.translatable("aleeve_atlas.hud.coords",
                    (int) Math.floor(player.getX()),
                    (int) Math.floor(player.getY()),
                    (int) Math.floor(player.getZ()));
            graphics.drawCenteredString(minecraft.font, text, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showBiome && minecraft.level != null) {
            var biome = minecraft.level.getBiome(player.blockPosition());
            Component name = biome.unwrapKey()
                    .<Component>map(key -> Component.translatable(
                            "biome." + key.location().toLanguageKey()))
                    .orElseGet(() -> Component.translatable(
                            "aleeve_atlas.hud.biome_unknown"));
            graphics.drawCenteredString(minecraft.font, name, textX, line, TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showLight && minecraft.level != null) {
            int light = minecraft.level.getMaxLocalRawBrightness(player.blockPosition());
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable("aleeve_atlas.hud.light", light), textX, line,
                    TEXT_COLOR);
            line += 10;
        }
        if (config.minimap.showTime && minecraft.level != null) {
            long dayTime = minecraft.level.getDayTime() % 24000L;
            int hours = (int) ((dayTime / 1000L + 6L) % 24L);
            int minutes = (int) ((dayTime % 1000L) * 60L / 1000L);
            graphics.drawCenteredString(minecraft.font,
                    Component.translatable("aleeve_atlas.hud.time",
                            "%02d".formatted(hours), "%02d".formatted(minutes)),
                    textX, line, TEXT_COLOR);
        }
    }
}
