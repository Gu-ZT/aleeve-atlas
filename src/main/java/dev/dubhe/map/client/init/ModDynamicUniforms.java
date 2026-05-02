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

    public record MapUniform(Vector2fc center, float radius) implements DynamicUniformStorage.DynamicUniform {
        public static int size() {
            return new Std140SizeCalculator()
                .putVec2()
                .putFloat()
                .get();
        }

        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec2(this.center)
                .putFloat(this.radius);
        }
    }
}
