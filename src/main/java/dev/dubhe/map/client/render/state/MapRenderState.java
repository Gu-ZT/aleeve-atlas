package dev.dubhe.map.client.render.state;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.lib.v2.wheel.client.gui.render.state.LibGuiElementRenderState;
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

public record MapRenderState(
    Matrix3x2f pose,
    Vector2f start,
    Vector2f end,
    int color,
    GpuBufferSlice mapUniform,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements LibGuiElementRenderState {
    public MapRenderState(
        Matrix3x2f pose,
        Vector2f start,
        Vector2f end,
        int color,
        GpuBufferSlice mapUniform,
        @Nullable ScreenRectangle scissorArea
    ) {
        this(
            pose,
            start,
            end,
            color,
            mapUniform,
            scissorArea,
            LibGuiElementRenderState.getBounds(pose, start.x, start.y, end.x, end.y, scissorArea)
        );
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(this.pose(), this.start().x(), this.start().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.start().x(), this.end().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.end().x(), this.end().y()).setColor(this.color());
        consumer.addVertexWith2DPose(this.pose(), this.end().x(), this.start().y()).setColor(this.color());
    }

    @Override
    public RenderPipeline pipeline() {
        return ModRenders.MAP_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    public static @Nullable GpuBufferSlice createMapUniform(Vector2fc center, float radius) {
        if (AleeveAtlasClient.getModDynamicUniforms() == null) return null;
        return AleeveAtlasClient.getModDynamicUniforms().getMapUbo().writeUniform(new ModDynamicUniforms.MapUniform(
            center, radius
        ));
    }

    @Override
    public Map<String, GpuBufferSlice> bufferSlices() {
        return Map.of("MapUniform", this.mapUniform());
    }
}
