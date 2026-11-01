package dev.dubhe.map.client.radar;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.visibility.OcclusionProxyRenderer;
import dev.dubhe.map.config.NonTeamVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 可见性判定的 CPU 前置剔除。
///
/// 在发起射线检测或 GPU 查询前，先按状态与视锥剔除目标，降低判定开销。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class VisibilityPreFilter {
    /// 单个待查询目标：身份、世界包围盒与到相机的距离平方。
    public record Target(UUID id, AABB box, double distanceSq, long frame) {
    }

    private static Frustum currentFrustum;

    private VisibilityPreFilter() {
    }

    /// 缓存当前世界帧的真实视锥，包含动态 FOV 与相机变换。
    @SubscribeEvent
    public static void onFrameGraphSetup(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        currentFrustum = event.getFrustum();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        currentFrustum = null;
    }

    /// 状态剔除：隐身且未穿戴护甲的目标不可见。
    public static boolean isHiddenByInvisibility(Entity target) {
        if (!target.isInvisible()) {
            return false;
        }
        if (!(target instanceof LivingEntity living)) {
            return true;
        }
        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (!living.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /// 视锥体剔除：目标 AABB 与当前帧视锥是否相交。
    public static boolean isInViewFrustum(Entity target) {
        Frustum frustum = currentFrustum();
        return frustum == null || frustum.isVisible(target.getBoundingBox());
    }

    private static Frustum currentFrustum() {
        if (currentFrustum != null) {
            return currentFrustum;
        }
        return null;
    }

    /// 选出需要遮挡查询的非同队玩家。
    ///
    /// 使用渲染状态中的插值位置构造包围盒，与当前帧矩阵保持一致；
    /// 结果按距离升序排列并截断到查询上限。
    public static List<Target> selectOcclusionTargets(Minecraft minecraft,
            Vec3 cameraPos, float partialTick, Player observer, long frame) {
        ClientLevel level = minecraft.level;
        if (level == null
                || AtlasSession.nonTeamVisibility() != NonTeamVisibility.VISIBLE_ONLY) {
            return List.of();
        }
        Frustum frustum = currentFrustum();
        List<Target> targets = new ArrayList<>();
        Set<UUID> accepted = new HashSet<>();
        for (Player player : level.players()) {
            if (player == observer || player.isRemoved()) continue;
            if (RadarStyle.isTeammate(player) || isHiddenByInvisibility(player)) {
                continue;
            }
            Vec3 position = player.getPosition(partialTick);
            AABB box = player.getBoundingBox().move(position.subtract(player.position()));
            if (frustum != null && !frustum.isVisible(box)) {
                continue;
            }
            if (!accepted.add(player.getUUID())) {
                continue;
            }
            targets.add(new Target(player.getUUID(), box,
                    box.getCenter().distanceToSqr(cameraPos), frame));
        }
        targets.sort(Comparator.comparingDouble(Target::distanceSq));
        if (targets.size() > OcclusionProxyRenderer.MAX_QUERIES) {
            return new ArrayList<>(targets.subList(0, OcclusionProxyRenderer.MAX_QUERIES));
        }
        return targets;
    }
}
