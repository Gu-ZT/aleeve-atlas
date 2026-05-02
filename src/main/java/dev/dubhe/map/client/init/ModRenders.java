package dev.dubhe.map.client.init;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.map.AleeveAtlas;

public class ModRenders {
    public static final RenderPipeline.Snippet SNIPPET_COMMON = RenderPipeline.builder()
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .buildSnippet();

    public static final RenderPipeline MAP_PIPELINE = RenderPipeline.builder(SNIPPET_COMMON)
        .withLocation(AleeveAtlas.of("pipeline/map"))
        .withVertexShader("core/position_color")
        .withFragmentShader(AleeveAtlas.of("core/map"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withUniform("MapUniform", UniformType.UNIFORM_BUFFER)
        .build();

    /** Pipeline for rendering circular / arrow entity and player markers on the minimap. */
    public static final RenderPipeline MARKER_PIPELINE = RenderPipeline.builder(SNIPPET_COMMON)
        .withLocation(AleeveAtlas.of("pipeline/marker"))
        .withVertexShader("core/position_color")
        .withFragmentShader(AleeveAtlas.of("core/marker"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withUniform("MarkerUniform", UniformType.UNIFORM_BUFFER)
        .build();

    public static final RenderPipeline MINIMAP_FRAME_PIPELINE = RenderPipeline.builder(SNIPPET_COMMON)
        .withLocation(AleeveAtlas.of("pipeline/minimap_frame"))
        .withVertexShader("core/position_color")
        .withFragmentShader(AleeveAtlas.of("core/minimap_frame"))
        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
        .withUniform("MinimapFrameUniform", UniformType.UNIFORM_BUFFER)
        .build();
}
