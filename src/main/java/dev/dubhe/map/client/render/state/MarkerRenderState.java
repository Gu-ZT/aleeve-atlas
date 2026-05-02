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

/**
 * Render state for a single minimap marker (entity dot or player arrow).
 *
 * <p>The backing fragment shader ({@code core/marker}) uses signed-distance-field
 * maths to draw either a filled circle (MarkerMode = 0) or a circle with a
 * directional arrow tip (MarkerMode = 1) within the minimap clip region.
 */
public record MarkerRenderState(
    Matrix3x2f pose,
    Vector2f p0,
    Vector2f p1,
    Vector2f p2,
    Vector2f p3,
    int color,
    GpuBufferSlice markerUniform,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements LibGuiElementRenderState {

    /** Convenience constructor – computes {@code bounds} automatically. */
    public MarkerRenderState(
        Matrix3x2f pose,
        Vector2f p0,
        Vector2f p1,
        Vector2f p2,
        Vector2f p3,
        int color,
        GpuBufferSlice markerUniform,
        @Nullable ScreenRectangle scissorArea
    ) {
        float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
        float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
        float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
        float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
        this(
            pose, p0, p1, p2, p3, color, markerUniform, scissorArea,
            LibGuiElementRenderState.getBounds(pose, minX, minY, maxX, maxY, scissorArea)
        );
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose(), p0().x(), p0().y()).setColor(color());
        consumer.addVertexWith2DPose(pose(), p3().x(), p3().y()).setColor(color());
        consumer.addVertexWith2DPose(pose(), p2().x(), p2().y()).setColor(color());
        consumer.addVertexWith2DPose(pose(), p1().x(), p1().y()).setColor(color());
    }

    @Override
    public RenderPipeline pipeline() {
        return ModRenders.MARKER_PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public Map<String, GpuBufferSlice> bufferSlices() {
        return Map.of("MarkerUniform", markerUniform());
    }

    // ── Factory helpers ──────────────────────────────────────────────────────

    /**
     * Writes a {@link ModDynamicUniforms.MarkerUniform} into the shared dynamic-uniform
     * storage and returns a slice to it.  Returns {@code null} when the client has not
     * yet been initialised (e.g. during early loading).
     *
     * @param clipCenter    framebuffer-space centre of the minimap clip region
     * @param clipHalfSize  half-extents of the clip region
     * @param clipRadius    radius for circular clip mode
     * @param clipMode      0 = square, 1 = circle
     * @param markerCenter  framebuffer-space centre of the marker
     * @param markerRadius  marker radius in framebuffer pixels
     * @param markerMode    0 = circle, 1 = circle + arrow
     * @param arrowAngle    arrow direction in radians (0 = north / up)
     */
    @Nullable
    public static GpuBufferSlice createMarkerUniform(
        Vector2fc clipCenter,
        Vector2fc clipHalfSize,
        float clipRadius,
        float clipMode,
        Vector2fc markerCenter,
        float markerRadius,
        float markerMode,
        float arrowAngle
    ) {
        if (AleeveAtlasClient.getModDynamicUniforms() == null) {
            return null;
        }
        return AleeveAtlasClient.getModDynamicUniforms().getMarkerUbo().writeUniform(
            new ModDynamicUniforms.MarkerUniform(
                clipCenter, clipHalfSize, clipRadius, clipMode,
                markerCenter, markerRadius, markerMode, arrowAngle
            )
        );
    }
}

