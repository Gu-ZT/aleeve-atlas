package dev.dubhe.map.client.render.state;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.lib.v2.rendering.state.LibGuiElementRenderState;
import dev.dubhe.map.client.AleeveAtlasClient;
import dev.dubhe.map.client.init.ModDynamicUniforms;
import dev.dubhe.map.client.init.ModRenders;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.joml.Vector2fc;

import java.util.Map;
import javax.annotation.Nullable;

public record MinimapFrameRenderState(
    Matrix3x2f pose,
    Vector2f p0,
    Vector2f p1,
    Vector2f p2,
    Vector2f p3,
    int color,
    GpuBufferSlice frameUniform,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements LibGuiElementRenderState {
    public MinimapFrameRenderState(
        Matrix3x2f pose,
        Vector2f p0,
        Vector2f p1,
        Vector2f p2,
        Vector2f p3,
        int color,
        GpuBufferSlice frameUniform,
        @Nullable ScreenRectangle scissorArea
    ) {
        float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        this(
            pose,
            p0,
            p1,
            p2,
            p3,
            color,
            frameUniform,
            scissorArea,
            LibGuiElementRenderState.getBounds(pose, minX, minY, maxX, maxY, scissorArea)
        );
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(this.pose(), this.p0().x(), this.p0().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.p3().x(), this.p3().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.p2().x(), this.p2().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.p1().x(), this.p1().y()).setColor(this.color());
    }

    @Override
    public RenderPipeline pipeline() {
        return ModRenders.MINIMAP_FRAME_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public Map<String, GpuBufferSlice> bufferSlices() {
        return Map.of("MinimapFrameUniform", this.frameUniform());
    }

    @Nullable
    public static GpuBufferSlice createFrameUniform(
        Vector2fc frameCenter,
        float frameRadius,
        float borderWidth,
        int fillColor,
        int borderColor
    ) {
        if (AleeveAtlasClient.getModDynamicUniforms() == null) {
            return null;
        }

        float fillA = ((fillColor >>> 24) & 0xFF) / 255.0F;
        float fillR = ((fillColor >>> 16) & 0xFF) / 255.0F;
        float fillG = ((fillColor >>> 8) & 0xFF) / 255.0F;
        float fillB = (fillColor & 0xFF) / 255.0F;

        float borderA = ((borderColor >>> 24) & 0xFF) / 255.0F;
        float borderR = ((borderColor >>> 16) & 0xFF) / 255.0F;
        float borderG = ((borderColor >>> 8) & 0xFF) / 255.0F;
        float borderB = (borderColor & 0xFF) / 255.0F;

        return AleeveAtlasClient.getModDynamicUniforms().getMinimapFrameUbo().writeUniform(
            new ModDynamicUniforms.MinimapFrameUniform(
                frameCenter,
                frameRadius,
                borderWidth,
                fillR,
                fillG,
                fillB,
                fillA,
                borderR,
                borderG,
                borderB,
                borderA
            )
        );
    }
}

