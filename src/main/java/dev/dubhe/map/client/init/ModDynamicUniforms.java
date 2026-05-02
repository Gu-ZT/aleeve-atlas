package dev.dubhe.map.client.init;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import lombok.Getter;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Vector2fc;

import java.nio.ByteBuffer;

public class ModDynamicUniforms {
    @Getter
    private final DynamicUniformStorage<MapUniform> mapUbo = new DynamicUniformStorage<>(
        "MapUniform UBO",
        MapUniform.size(),
        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST
    );

    @Getter
    private final DynamicUniformStorage<MarkerUniform> markerUbo = new DynamicUniformStorage<>(
        "MarkerUniform UBO",
        MarkerUniform.size(),
        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST
    );

    @Getter
    private final DynamicUniformStorage<MinimapFrameUniform> minimapFrameUbo = new DynamicUniformStorage<>(
        "MinimapFrameUniform UBO",
        MinimapFrameUniform.size(),
        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST
    );

    public record MapUniform(
        Vector2fc clipCenter,
        Vector2fc clipHalfSize,
        float clipRadius,
        float clipMode
    ) implements DynamicUniformStorage.DynamicUniform {
        public static int size() {
            return new Std140SizeCalculator()
                .putVec2()
                .putVec2()
                .putFloat()
                .putFloat()
                .get();
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(this.clipCenter)
                .putVec2(this.clipHalfSize)
                .putFloat(this.clipRadius)
                .putFloat(this.clipMode);
        }
    }

    public record MarkerUniform(
        Vector2fc clipCenter,
        Vector2fc clipHalfSize,
        float clipRadius,
        float clipMode,
        Vector2fc markerCenter,
        float markerRadius,
        float markerMode,
        float arrowAngle
    ) implements DynamicUniformStorage.DynamicUniform {
        public static int size() {
            return new Std140SizeCalculator()
                .putVec2()   // ClipCenter
                .putVec2()   // ClipHalfSize
                .putFloat()  // ClipRadius
                .putFloat()  // ClipMode
                .putVec2()   // MarkerCenter
                .putFloat()  // MarkerRadius
                .putFloat()  // MarkerMode
                .putFloat()  // ArrowAngle
                .get();
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(this.clipCenter)
                .putVec2(this.clipHalfSize)
                .putFloat(this.clipRadius)
                .putFloat(this.clipMode)
                .putVec2(this.markerCenter)
                .putFloat(this.markerRadius)
                .putFloat(this.markerMode)
                .putFloat(this.arrowAngle);
        }
    }

    public record MinimapFrameUniform(
        Vector2fc frameCenter,
        float frameRadius,
        float borderWidth,
        float fillR,
        float fillG,
        float fillB,
        float fillA,
        float borderR,
        float borderG,
        float borderB,
        float borderA
    ) implements DynamicUniformStorage.DynamicUniform {
        public static int size() {
            return new Std140SizeCalculator()
                .putVec2()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .putFloat()
                .get();
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(this.frameCenter)
                .putFloat(this.frameRadius)
                .putFloat(this.borderWidth)
                .putFloat(this.fillR)
                .putFloat(this.fillG)
                .putFloat(this.fillB)
                .putFloat(this.fillA)
                .putFloat(this.borderR)
                .putFloat(this.borderG)
                .putFloat(this.borderB)
                .putFloat(this.borderA);
        }
    }
}
