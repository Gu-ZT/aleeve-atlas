package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// 在客户端渲染线程使用本帧世界画面的相机和最终投影定位标点。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WorldProjection {
    private static final double LOOK_TOLERANCE_PIXELS = 24.0;
    private static @Nullable Frame frame;

    private record Frame(Vec3 cameraPosition, Matrix4fc worldToClip, int width, int height) {
    }

    private WorldProjection() {
    }

    /// 投影结果：屏幕坐标与到相机的距离（方块）。
    public record Projected(double screenX, double screenY, double distance) {
    }

    @SubscribeEvent
    public static void onWorldRender(FrameGraphSetupEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = event.getCamera();
        if (minecraft.level == null || !camera.isInitialized()) {
            frame = null;
            return;
        }
        frame = new Frame(camera.getPosition(), new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()),
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            frame = null;
        }
    }

    /// 投影点是否位于准星附近的圆形容差范围内。
    static boolean isLookingAt(Projected projected, int guiWidth, int guiHeight) {
        double deltaX = projected.screenX() - guiWidth / 2.0;
        double deltaY = projected.screenY() - guiHeight / 2.0;
        return deltaX * deltaX + deltaY * deltaY
                <= LOOK_TOLERANCE_PIXELS * LOOK_TOLERANCE_PIXELS;
    }

    /// 把世界坐标投影到 GUI 缩放后的屏幕坐标；不在视野前方时返回 {@link Optional#empty()}。
    public static Optional<Projected> project(Vec3 worldPosition) {
        Frame current = frame;
        if (current == null) {
            return Optional.empty();
        }
        return project(worldPosition, current.cameraPosition(), current.worldToClip(),
                current.width(), current.height());
    }

    static Optional<Projected> project(Vec3 worldPosition, Vec3 cameraPos,
            Matrix4fc worldToClip, int guiWidth, int guiHeight) {
        double distance = cameraPos.distanceTo(worldPosition);
        if (distance < 0.1) {
            return Optional.empty();
        }

        Vector4f clip = new Vector4f(
                (float) (worldPosition.x - cameraPos.x),
                (float) (worldPosition.y - cameraPos.y),
                (float) (worldPosition.z - cameraPos.z),
                1.0F);
        worldToClip.transform(clip);
        if (clip.w <= 0.0F) {
            return Optional.empty();
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (ndcX < -1.2F || ndcX > 1.2F || ndcY < -1.2F || ndcY > 1.2F) {
            return Optional.empty();
        }

        double screenX = (ndcX * 0.5 + 0.5) * guiWidth;
        double screenY = (0.5 - ndcY * 0.5) * guiHeight;
        return Optional.of(new Projected(screenX, screenY, distance));
    }
}
