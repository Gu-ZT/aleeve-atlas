package dev.dubhe.map.client.hud;

import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.render.MinimapRenderAccumulator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.Locale;

@SuppressWarnings("unused")
public class MinimapTextGuiLayer implements GuiLayer {
    private static void renderHudInfo(Minecraft minecraft, GuiGraphicsExtractor graphics, int x, int y) {
        if (minecraft.level == null || minecraft.player == null) return;
        BlockPos playerPos = minecraft.player.blockPosition();
        ResourceKey<?> dimensionKey = minecraft.level.dimension();
        String dimension = dimensionKey.identifier().toString();
        Holder<Biome> biome = minecraft.level.getBiome(playerPos);
        String biomeName = biome.unwrapKey().map(key -> key.identifier().toString()).orElse("minecraft:unknown");
        String facing = facingText(minecraft.player.getYRot());
        int dayTime = (int) (minecraft.level.getOverworldClockTime() % 24000L);
        int light = Math.max(
            minecraft.level.getBrightness(LightLayer.SKY, playerPos),
            minecraft.level.getBrightness(LightLayer.BLOCK, playerPos)
        );
        int lineY = y;

        if (AtlasClientState.isCoordinateLineVisible()) {
            graphics.text(
                minecraft.font,
                Component.literal(String.format(
                    Locale.ROOT, "X:%d Y:%d Z:%d",
                    playerPos.getX(), playerPos.getY(), playerPos.getZ()
                )),
                x, lineY, 0xFFFFFFFF, true
            );
            lineY += 10;
        }

        if (AtlasClientState.isEnvironmentLineVisible()) {
            graphics.text(
                minecraft.font,
                Component.literal("Dir: " + facing + " | Zoom: " + AtlasClientState.getZoomLevel()),
                x, lineY, 0xFFFFFFFF, true
            );
            lineY += 10;
            graphics.text(minecraft.font, Component.literal("Biome: " + biomeName), x, lineY, 0xFFFFFFFF, false);
            lineY += 10;
            graphics.text(
                minecraft.font,
                Component.literal("Dim: " + dimension + " | Time: " + dayTime + " | Light: " + light),
                x, lineY, 0xFFFFFFFF, false
            );
            lineY += 10;
            String rotMode = AtlasClientState.isRotateWithPlayer() ? "FOLLOW" : "NORTH_UP";
            String cave = MinimapGuiLayer.shouldRenderCaves(minecraft) ? "CAVE" : "SURFACE";
            graphics.text(
                minecraft.font,
                Component.literal("Shape: " + AtlasClientState.getMinimapShape()
                                  + " | Rot: " + rotMode + " | Radar: "
                                  + (AtlasClientState.isRadarEnabled() ? "ON" : "OFF") + " | " + cave),
                x, lineY, 0xFFFFFFFF, false
            );
        }
    }

    private static String facingText(float yaw) {
        float normalized = (yaw % 360.0F + 360.0F) % 360.0F;
        if (normalized >= 45.0F && normalized < 135.0F) return "W";
        if (normalized >= 135.0F && normalized < 225.0F) return "N";
        if (normalized >= 225.0F && normalized < 315.0F) return "E";
        return "S";
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        MinimapRenderAccumulator.flush(guiGraphics);
        MinimapHudSupport.MinimapContext context = MinimapHudSupport.captureContext(guiGraphics);
        if (context == null) return;

        renderHudInfo(context.minecraft(), guiGraphics, context.mapX(), context.mapBottom() + 4);
    }
}
