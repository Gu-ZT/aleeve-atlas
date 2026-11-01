package dev.dubhe.map.client.visibility;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/// 遮挡查询的代理包围盒绘制。
///
/// 代理盒由 Blaze3d 管线绘制：深度测试沿用世界深度，颜色与深度写入关闭，
/// 面剔除关闭以便相机位于盒内时仍能产生样本。查询对象由 LWJGL 直接管理，
/// 绘制状态全部交给管线与状态缓存，避免直接修改 FBO、program 与掩码。
///
/// 片元 alpha 固定为 1，确保原版 `position_color` 着色器不会执行 `discard`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class OcclusionProxyRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/occlusion");
    /// 查询总数上限，规格要求每帧不超过 64 个。
    public static final int MAX_QUERIES = 64;

    private static final int WHITE = 0xFFFFFFFF;

    private static final RenderPipeline PROXY_PIPELINE = RenderPipeline
            .builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(AleeveAtlas.id("pipeline/occlusion_proxy"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withColorTargetState(new ColorTargetState(java.util.Optional.empty(), ColorTargetState.WRITE_NONE))
            .withCull(false)
            .withPolygonMode(PolygonMode.FILL)
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private static final RenderType PROXY_TYPE =
            RenderType.create("aleeve_atlas_occlusion_proxy",
                    RenderSetup.builder(PROXY_PIPELINE).createRenderSetup());

    private int[] queries;
    private boolean unavailable;
    private String failureReason = "";

    @SubscribeEvent
    public static void registerPipeline(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(PROXY_PIPELINE);
    }

    /// 检查当前 GL 后端是否支持遮挡查询并分配查询对象。
    public boolean open() {
        if (this.queries != null) {
            return true;
        }
        if (this.unavailable) {
            return false;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return this.disable("not on the render thread");
        }
        String backend = RenderSystem.getDevice().getBackendName();
        if (backend == null || !backend.toLowerCase(Locale.ROOT).contains("opengl")) {
            return this.disable("backend is " + backend);
        }
        if (!GL.getCapabilities().OpenGL33) {
            return this.disable("boolean occlusion queries unsupported");
        }
        if (!GL.getCapabilities().OpenGL44 && !GL.getCapabilities().GL_ARB_query_buffer_object) {
            return this.disable("non-blocking query result retrieval unsupported");
        }
        int error = GL11C.glGetError();
        if (error != GL11C.GL_NO_ERROR) {
            return this.disable("GL error during capability probe: 0x"
                    + Integer.toHexString(error));
        }
        this.queries = new int[MAX_QUERIES];
        for (int i = 0; i < this.queries.length; i++) {
            this.queries[i] = GL15C.glGenQueries();
        }
        error = GL11C.glGetError();
        if (error != GL11C.GL_NO_ERROR) {
            this.releaseQueries();
            return this.disable("GL error while creating queries: 0x"
                    + Integer.toHexString(error));
        }
        LOGGER.info("Occlusion queries enabled: GL_ANY_SAMPLES_PASSED, {} query slots", MAX_QUERIES);
        return true;
    }

    /// 当前是否可用。
    public boolean available() {
        return this.queries != null && !this.unavailable;
    }

    /// 不可用的原因。
    public String failureReason() {
        return this.failureReason;
    }

    private boolean disable(String reason) {
        this.unavailable = true;
        this.failureReason = reason;
        LOGGER.warn("Occlusion queries disabled: {}", reason);
        return false;
    }

    /// 提交一次代理盒查询；查询对象绑定到指定池槽位。
    public boolean submit(int slot, AABB box, Vec3 cameraPos) {
        if (!this.available() || slot < 0 || slot >= this.queries.length) {
            return false;
        }
        double minX = box.minX - cameraPos.x;
        double minY = box.minY - cameraPos.y;
        double minZ = box.minZ - cameraPos.z;
        double maxX = box.maxX - cameraPos.x;
        double maxY = box.maxY - cameraPos.y;
        double maxZ = box.maxZ - cameraPos.z;

        GL15C.glBeginQuery(GL33C.GL_ANY_SAMPLES_PASSED, this.queries[slot]);
        try {
            drawBox(minX, minY, minZ, maxX, maxY, maxZ);
        } finally {
            GL15C.glEndQuery(GL33C.GL_ANY_SAMPLES_PASSED);
        }
        return true;
    }

    /// 一次调用读取布尔结果；未就绪时保持 -1，不轮询可用性，也不等待 GPU。
    public int poll(int slot) {
        if (!this.available() || slot < 0 || slot >= this.queries.length) {
            return -1;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var result = stack.ints(-1);
            GL15C.glGetQueryObjectuiv(this.queries[slot], GL44C.GL_QUERY_RESULT_NO_WAIT, result);
            return result.get(0);
        }
    }

    /// 读取并清除最近一次 GL 错误，供调用方决定是否降级。
    public int consumeError() {
        return this.available() ? GL11C.glGetError() : GL11C.GL_NO_ERROR;
    }

    /// 布尔查询只要有任意样本通过即为可见。
    public static boolean passesThreshold(int samples) {
        return samples > 0;
    }

    /// 释放全部查询对象。
    public void close() {
        this.releaseQueries();
        this.unavailable = false;
        this.failureReason = "";
    }

    private void releaseQueries() {
        if (this.queries == null) {
            return;
        }
        int[] ids = this.queries;
        this.queries = null;
        if (RenderSystem.isOnRenderThread()) {
            GL15C.glDeleteQueries(ids);
        } else {
            // GL 对象只能在持有上下文的渲染线程删除。
            Minecraft.getInstance().execute(() -> GL15C.glDeleteQueries(ids));
        }
    }

    private static void drawBox(double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ) {
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // 六个面各自两个三角形；顶点顺序无关，管线关闭了面剔除。
        quad(buffer, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ);
        quad(buffer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        quad(buffer, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ, minX, minY, maxZ);
        quad(buffer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ);
        quad(buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ);
        quad(buffer, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ);
        MeshData mesh = buffer.buildOrThrow();
        // RenderType.draw 负责关闭 MeshData，包括绘制异常时的清理。
        PROXY_TYPE.draw(mesh);
    }

    private static void quad(BufferBuilder buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            double x3, double y3, double z3, double x4, double y4, double z4) {
        vertex(buffer, x1, y1, z1);
        vertex(buffer, x2, y2, z2);
        vertex(buffer, x3, y3, z3);
        vertex(buffer, x4, y4, z4);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z) {
        buffer.addVertex((float) x, (float) y, (float) z).setColor(WHITE);
    }
}
