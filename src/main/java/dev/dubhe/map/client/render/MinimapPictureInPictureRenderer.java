package dev.dubhe.map.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.map.client.AleeveAtlasClient;
import dev.dubhe.map.client.init.ModDynamicUniforms;
import dev.dubhe.map.client.init.ModRenders;
import dev.dubhe.map.client.render.state.MinimapPictureInPictureRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public class MinimapPictureInPictureRenderer extends PictureInPictureRenderer<MinimapPictureInPictureRenderState> {

    public MinimapPictureInPictureRenderer(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    private static void v(BufferBuilder b, float x, float y, int color) {
        b.addVertex(x, y, 0f).setColor(color);
    }

    @Override
    protected float getTranslateY(int height, int guiScale) {
        return (float) height / 2.0f;
    }

    @Override
    public Class<MinimapPictureInPictureRenderState> getRenderStateClass() {
        return MinimapPictureInPictureRenderState.class;
    }

    @Override
    protected void renderToTexture(MinimapPictureInPictureRenderState state, PoseStack poseStack) {
        var modUniforms = AleeveAtlasClient.getModDynamicUniforms();
        if (modUniforms == null || state.cells().isEmpty()) return;

        modUniforms.endFrame();

        float guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int pipX0 = state.x0(), pipY0 = state.y0();
        float texW = (state.x1() - pipX0) * guiScale, texH = (state.y1() - pipY0) * guiScale;

        // DynamicTransforms
        var dynUniforms = RenderSystem.getDynamicUniforms();
        GpuBufferSlice dynamicTransforms = dynUniforms.writeTransform(
            new Matrix4f(), new Vector4f(1, 1, 1, 1), new Vector3f(), new Matrix4f());

        // MapUniform
        float fbHalf = (state.x1() - pipX0) / 2f * guiScale;
        float fbCx = texW / 2f, fbCy = texH / 2f;
        float clipMode = state.circleMode() ? 1f : 0f;
        GpuBufferSlice mapUniform = modUniforms.getMapUbo().writeUniform(
            new ModDynamicUniforms.MapUniform(new Vector2f(fbCx, fbCy), new Vector2f(fbHalf, fbHalf), fbHalf, clipMode));

        // Build cell vertices
        var byteBuf = new ByteBufferBuilder(state.cells().size() * 4 * 16);
        var cb = new BufferBuilder(byteBuf, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int vertCount = 0;
        for (var c : state.cells()) {
            v(cb, (c.p0().x() - pipX0) * guiScale, (c.p0().y() - pipY0) * guiScale, c.color());
            v(cb, (c.p3().x() - pipX0) * guiScale, (c.p3().y() - pipY0) * guiScale, c.color());
            v(cb, (c.p2().x() - pipX0) * guiScale, (c.p2().y() - pipY0) * guiScale, c.color());
            v(cb, (c.p1().x() - pipX0) * guiScale, (c.p1().y() - pipY0) * guiScale, c.color());
            vertCount += 4;
        }
        var mesh = cb.build();
        if (mesh == null) {
            byteBuf.close();
            return;
        }
        GpuBuffer vertBuf = DefaultVertexFormat.POSITION_COLOR.uploadImmediateVertexBuffer(mesh.vertexBuffer());
        byteBuf.close();

        // Render
        var colorTex = RenderSystem.outputColorTextureOverride;
        var depthTex = RenderSystem.outputDepthTextureOverride;
        if (colorTex == null) return;

        try (
            RenderPass rp = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "minimap_cells", colorTex, OptionalInt.empty(), depthTex, OptionalDouble.empty())
        ) {
            RenderSystem.bindDefaultUniforms(rp);
            rp.setUniform("DynamicTransforms", dynamicTransforms);
            rp.setPipeline(ModRenders.MAP_PIPELINE);
            rp.setUniform("MapUniform", mapUniform);
            rp.setVertexBuffer(0, vertBuf);
            var autoIdx = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            int ic = vertCount / 4 * 6;
            rp.setIndexBuffer(autoIdx.getBuffer(ic), autoIdx.type());
            rp.drawIndexed(0, 0, ic, 1);
        }
    }

    @Override
    protected String getTextureLabel() {
        return "minimap";
    }
}
