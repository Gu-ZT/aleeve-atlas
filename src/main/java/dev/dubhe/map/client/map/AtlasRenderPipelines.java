package dev.dubhe.map.client.map;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/// 旧版着色器与 RenderType 接入，保留地图圆形裁剪及标记的解析式抗锯齿绘制。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasRenderPipelines extends RenderType {
    private static final List<ShaderProgram> SHADERS = new ArrayList<>();
    public static final Function<ResourceLocation, RenderType> GUI_TEXTURED = RenderType::guiTextured;
    public static final Function<ResourceLocation, RenderType> MINIMAP_CIRCLE = marker("minimap_circle");
    public static final Function<ResourceLocation, RenderType> MINIMAP_OVERLAY = marker("minimap_overlay");
    public static final Function<ResourceLocation, RenderType> WORLD_COMPASS = marker("world_compass");
    public static final Function<ResourceLocation, RenderType> PLAYER_DROPLET = marker("player_droplet");
    public static final Function<ResourceLocation, RenderType> MAP_MARKER_DOT = marker("map_marker_dot");
    public static final Function<ResourceLocation, RenderType> MAP_MARKER_DIAMOND = marker("map_marker_diamond");
    public static final Function<ResourceLocation, RenderType> MAP_MARKER_CROSS = marker("map_marker_cross");
    public static final Function<ResourceLocation, RenderType> MAP_PING_RIPPLE = marker("map_ping_ripple");
    public static final Function<ResourceLocation, RenderType> MAP_MARKER_ARROW = marker("map_marker_arrow");

    private AtlasRenderPipelines() {
        super("atlas_shaders", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                1536, false, false, () -> {}, () -> {});
    }

    private static Function<ResourceLocation, RenderType> marker(String name) {
        ShaderProgram program = new ShaderProgram(AleeveAtlas.id("core/" + name),
                DefaultVertexFormat.POSITION_TEX_COLOR, ShaderDefines.EMPTY);
        SHADERS.add(program);
        Map<ResourceLocation, RenderType> textures = new HashMap<>();
        return texture -> textures.computeIfAbsent(texture, key -> create("atlas_" + name,
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder().setShaderState(new ShaderStateShard(program))
                        .setTextureState(new TextureStateShard(key, net.minecraft.util.TriState.FALSE, false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL).createCompositeState(false)));
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        SHADERS.forEach(event::registerShader);
    }
}
