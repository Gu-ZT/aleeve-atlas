package dev.dubhe.map.client.visibility;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.client.radar.VisibilityGate;
import dev.dubhe.map.client.radar.VisibilityPreFilter;
import dev.dubhe.map.config.NonTeamVisibility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// GPU 硬件遮挡查询实现。
///
/// 查询在 {@link RenderLevelStageEvent.AfterOpaqueFeatures} 提交：此时不透明地形与实体深度已绘制，
/// 半透明地形尚未开始，代理盒直接复用世界深度缓冲。结果由 GPU 异步产出，
/// 本类只读取已就绪的查询，未就绪时沿用上一有效值，不阻塞渲染线程。
///
/// 代理盒由 Blaze3d 管线绘制，LWJGL 仅管理查询对象；绘制细节在
/// {@link OcclusionProxyRenderer}，目标筛选在 {@link VisibilityPreFilter}。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class GlOcclusionVisibilityResolver implements VisibilityResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/occlusion");
    /// 离开视野后保留结果与渐隐状态的最大帧数。
    private static final int MAX_AGE_FRAMES = 600;
    private static final GlOcclusionVisibilityResolver INSTANCE =
            new GlOcclusionVisibilityResolver();

    private final OcclusionProxyRenderer renderer = new OcclusionProxyRenderer();
    private final VisibilityResultCache cache = new VisibilityResultCache();
    private long frame;
    private boolean initialized;
    private boolean active;

    private GlOcclusionVisibilityResolver() {
    }

    public static GlOcclusionVisibilityResolver instance() {
        return INSTANCE;
    }

    /// 初始化 GL 查询资源，失败时由选择器保留射线实现。
    public boolean initialize() {
        if (this.initialized) {
            return this.active;
        }
        this.initialized = true;
        try {
            this.active = this.renderer.open();
        } catch (RuntimeException e) {
            this.active = false;
            LOGGER.warn("Occlusion query initialization failed", e);
        }
        if (!this.active) {
            LOGGER.warn("Occlusion query unavailable: {}", this.renderer.failureReason());
        }
        return this.active;
    }

    public boolean active() {
        return this.active;
    }

    @Override
    public boolean isVisible(Level level, Player observer, Entity target) {
        // 只读异步结果；pending 期间沿用上一有效值，从未取得结果时默认不可见。
        return this.cache.isVisible(target.getUUID());
    }

    @Override
    public String name() {
        return "gpu-occlusion";
    }

    /// 目标最近一次读取到的查询样本数，供诊断输出。
    public int samplesOf(UUID id) {
        return this.cache.samples(id);
    }

    /// 目标是否有查询尚未读完。
    public boolean isPending(UUID id) {
        return this.cache.isPending(id);
    }

    /// 每帧提交与轮询遮挡查询。
    @SubscribeEvent
    public static void onAfterEntities(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        INSTANCE.render(event);
    }

    /// 登出时释放查询并清空跨会话状态。
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.close();
    }

    private void render(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (!this.active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player observer = minecraft.player;
        LevelRenderState renderState = event.getLevelRenderState();
        if (level == null || observer == null || renderState.cameraRenderState == null) {
            return;
        }
        if (AtlasSession.nonTeamVisibility() != NonTeamVisibility.VISIBLE_ONLY) {
            this.cache.clear();
            return;
        }
        this.frame++;
        long started = System.nanoTime();
        try {
            this.pollReady();
            List<VisibilityPreFilter.Target> targets = VisibilityPreFilter.selectOcclusionTargets(
                    minecraft, renderState, observer, this.frame);
            if (!targets.isEmpty()) {
                this.issueQueries(targets, event);
                int error = this.renderer.consumeError();
                if (error != 0) {
                    throw new IllegalStateException("GL error after occlusion submission: 0x"
                            + Integer.toHexString(error));
                }
            }
            this.prune();
        } catch (RuntimeException e) {
            this.disable(e);
        } finally {
            AtlasProfiling.recordOcclusion(System.nanoTime() - started);
        }
    }

    /// 读取已就绪的查询；未就绪的目标保留上一判定。
    private void pollReady() {
        long now = System.currentTimeMillis();
        for (VisibilityResultCache.Entry entry : this.cache.all()) {
            if (!entry.pending || entry.slot < 0) {
                continue;
            }
            int samples = this.renderer.poll(entry.slot);
            if (samples < 0) {
                continue;
            }
            this.cache.complete(entry, OcclusionProxyRenderer.passesThreshold(samples), samples, now);
        }
    }

    /// 为目标分配查询槽位并提交代理盒。
    private void issueQueries(List<VisibilityPreFilter.Target> targets,
            RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Set<Integer> occupied = this.cache.occupiedSlots();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            stack.set(event.getModelViewMatrix());
            for (VisibilityPreFilter.Target target : targets) {
                VisibilityResultCache.Entry entry = this.cache.observe(target.id(), this.frame);
                // 上一帧查询尚未完成时保留旧结果，不覆盖查询对象。
                if (entry.pending) {
                    continue;
                }
                int slot = nextFreeSlot(occupied);
                if (slot < 0) {
                    break;
                }
                this.cache.attach(entry, slot);
                occupied.add(slot);
                if (!this.renderer.submit(slot, target.box(),
                        event.getLevelRenderState().cameraRenderState.pos)) {
                    this.cache.cancel(entry);
                }
            }
        } finally {
            stack.popMatrix();
        }
    }

    private static int nextFreeSlot(Set<Integer> occupied) {
        for (int i = 0; i < OcclusionProxyRenderer.MAX_QUERIES; i++) {
            if (!occupied.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    /// 清理长期不再出现的目标，避免缓存与查询槽位泄漏。
    private void prune() {
        List<VisibilityResultCache.Entry> removed = new ArrayList<>();
        this.cache.prune(this.frame, MAX_AGE_FRAMES, removed);
    }

    /// 查询或绘制失败后释放 GL 资源，并切换到射线实现。
    private void disable(RuntimeException error) {
        LOGGER.warn("Occlusion query failed; switching to raycast visibility", error);
        this.renderer.close();
        this.cache.clear();
        this.active = false;
        VisibilityGate.setResolver(new RaycastVisibilityResolver());
    }

    /// 释放查询资源并清空跨会话状态。
    public void close() {
        this.renderer.close();
        this.cache.clear();
        this.frame = 0;
        this.initialized = false;
        this.active = false;
    }
}
