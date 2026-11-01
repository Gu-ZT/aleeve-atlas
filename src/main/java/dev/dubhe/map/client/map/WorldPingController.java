package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import dev.dubhe.map.client.AtlasKeyMappings;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/// 世界内中键标点：短按创建一般标点，长按打开轮盘。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class WorldPingController {
    private static final int HOLD_TICKS = 4;
    private static final double MAX_PING_DISTANCE = 200.0;
    private static long holdStarted = -1L;
    private static boolean holding;
    private static boolean wheelOpened;
    private static Vec3 target;

    private WorldPingController() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!MinimapPingAPI.isWorldInputEnabled()) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            reset();
            return;
        }
        if (holdStarted >= 0L && minecraft.gui.screen() != null
                && !(minecraft.gui.screen() instanceof PingWheelMenu)) {
            reset();
            return;
        }
        if (holding && holdStarted >= 0L && !wheelOpened
                && minecraft.level.getGameTime() - holdStarted > HOLD_TICKS) {
            wheelOpened = true;
            PingWheelMenu.open(target);
        }
    }

    /// 在输入事件中直接处理中键，并阻止它触发原版选取方块。
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!MinimapPingAPI.isWorldInputEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        MouseButtonEvent mouse = new MouseButtonEvent(0, 0, event.getMouseButtonInfo());
        if (!AtlasKeyMappings.WORLD_PING.matchesMouse(mouse)) {
            return;
        }
        if (minecraft.gui.screen() != null && holdStarted < 0L) {
            return;
        }
        processPress(minecraft, event.getAction());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (!MinimapPingAPI.isWorldInputEnabled()) {
            return;
        }
        if (!AtlasKeyMappings.WORLD_PING.matches(event.getKeyEvent())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            processPress(minecraft, event.getAction());
        }
    }

    private static void processPress(Minecraft minecraft, int action) {
        if (minecraft.level == null) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            if (minecraft.gui.screen() == null && !holding) {
                holding = true;
                holdStarted = minecraft.level.getGameTime();
                target = target(minecraft);
            }
            return;
        }
        if (action != GLFW.GLFW_RELEASE || holdStarted < 0L) {
            return;
        }
        holding = false;
        if (wheelOpened) {
            if (minecraft.gui.screen() instanceof PingWheelMenu wheel) {
                wheel.triggerFromHoldRelease();
            }
            reset();
            return;
        }
        if (minecraft.level.getGameTime() - holdStarted <= HOLD_TICKS) {
            createPing(target, MapPingData.PingIcon.GENERAL);
        }
        reset();
    }

    static void createPing(Vec3 position, MapPingData.PingIcon icon) {
        if (position != null) {
            ClientPings.createLocal(position, icon);
        }
    }

    static void finishWheel() {
        reset();
    }

    private static void reset() {
        holdStarted = -1L;
        holding = false;
        wheelOpened = false;
        target = null;
    }

    private static Vec3 target(Minecraft minecraft) {
        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return minecraft.player.getEyePosition().add(minecraft.player.getLookAngle()
                    .scale(MAX_PING_DISTANCE));
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        HitResult blockHit = camera.pick(MAX_PING_DISTANCE, 1.0F, false);
        double maxDistance = blockHit.getType() == HitResult.Type.MISS
                ? MAX_PING_DISTANCE * MAX_PING_DISTANCE
                : blockHit.getLocation().distanceToSqr(eye);
        Vec3 view = camera.getViewVector(1.0F);
        Vec3 end = eye.add(view.scale(MAX_PING_DISTANCE));
        AABB searchBox = camera.getBoundingBox().expandTowards(view.scale(MAX_PING_DISTANCE))
                .inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(camera, eye, end, searchBox,
                entity -> (!entity.isSpectator() && entity.isPickable())
                        || entity instanceof ItemEntity, maxDistance);
        if (entityHit != null
                && entityHit.getLocation().distanceToSqr(eye)
                < blockHit.getLocation().distanceToSqr(eye)) {
            return entityHit.getLocation();
        }
        return blockHit.getLocation();
    }
}
