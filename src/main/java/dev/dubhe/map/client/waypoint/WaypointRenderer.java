package dev.dubhe.map.client.waypoint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AtlasClientState;
import dev.dubhe.map.client.hud.MinimapHudRenderer;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WaypointRenderer {
    private WaypointRenderer() {
    }

    public static void renderMinimap(Minecraft minecraft, GuiGraphics graphics, int mapX, int mapY, int mapSizePx, float rotationDeg) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        List<Waypoint> waypoints = WaypointManager.getWaypoints(dimension);
        if (waypoints.isEmpty()) {
            return;
        }

        float rotationRad = (float) Math.toRadians(rotationDeg);
        double cos = Math.cos(rotationRad);
        double sin = Math.sin(rotationRad);
        int centerX = mapX + mapSizePx / 2;
        int centerY = mapY + mapSizePx / 2;
        double mapRadius = mapSizePx / 2.0D - 4.0D;
        double range = Math.max(AtlasClientState.getRadarRangeBlocks(), AtlasClientState.getBlockStep() * 12.0D);

        for (Waypoint waypoint : waypoints) {
            double dx = waypoint.x + 0.5D - minecraft.player.getX();
            double dz = waypoint.z + 0.5D - minecraft.player.getZ();
            double mapLocalX = dx * cos + dz * sin;
            double mapLocalZ = -dx * sin + dz * cos;
            double pixelX = centerX + (mapLocalX / range) * mapRadius;
            double pixelY = centerY + (mapLocalZ / range) * mapRadius;
            int color = 0xFF000000 | waypoint.color;
            if (!MinimapHudRenderer.isPointInsideMinimap(pixelX, pixelY, minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight())) {
                double len = Math.max(1.0D, Math.sqrt(mapLocalX * mapLocalX + mapLocalZ * mapLocalZ));
                pixelX = centerX + (mapLocalX / len) * mapRadius;
                pixelY = centerY + (mapLocalZ / len) * mapRadius;
                graphics.fill((int) pixelX - 2, (int) pixelY - 2, (int) pixelX + 3, (int) pixelY + 3, color);
            } else {
                graphics.fill((int) pixelX - 1, (int) pixelY - 1, (int) pixelX + 2, (int) pixelY + 2, color);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent.AfterEntities event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Optional<Waypoint> active = WaypointManager.getActiveWaypoint(minecraft.level.dimension());
        if (active.isEmpty()) {
            return;
        }

        Waypoint waypoint = active.get();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        double x = waypoint.x + 0.5D - cameraPos.x;
        double y = waypoint.y - cameraPos.y;
        double z = waypoint.z + 0.5D - cameraPos.z;
        float r = ((waypoint.color >> 16) & 0xFF) / 255.0F;
        float g = ((waypoint.color >> 8) & 0xFF) / 255.0F;
        float b = (waypoint.color & 0xFF) / 255.0F;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        poseStack.pushPose();
        ShapeRenderer.renderLineBox(poseStack, lineBuffer, x - 0.35D, y, z - 0.35D, x + 0.35D, y + 1.8D, z + 0.35D, r, g, b, 1.0F);
        ShapeRenderer.renderLineBox(poseStack, lineBuffer, x - 0.10D, y + 1.8D, z - 0.10D, x + 0.10D, y + 14.0D, z + 0.10D, r, g, b, 0.5F);
        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }
}


