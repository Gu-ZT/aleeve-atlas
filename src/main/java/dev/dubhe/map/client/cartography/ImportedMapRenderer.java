package dev.dubhe.map.client.cartography;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.cartography.MapSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.world.level.material.MapColor;

/// 大比例导入地图超出高清画布时，仍显示完整轮廓；高清画布随后覆盖此底层。
public final class ImportedMapRenderer {
    private static final Map<MapSnapshot, ResourceLocation> TEXTURES = new HashMap<>();
    private ImportedMapRenderer() { }

    public static void draw(GuiGraphics graphics, double centerX, double centerZ, float zoom, int width, int height) {
        var store = ClientCartography.currentStore();
        if (store == null) return;
        var maps = store.intersecting((int) Math.floor(centerX - width / (2.0 * zoom)),
                (int) Math.floor(centerZ - height / (2.0 * zoom)), (int) Math.ceil(centerX + width / (2.0 * zoom)),
                (int) Math.ceil(centerZ + height / (2.0 * zoom)));
        var visible = new HashSet<>(maps);
        TEXTURES.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey())) return false;
            Minecraft.getInstance().getTextureManager().release(entry.getValue());
            return true;
        });
        for (int i = maps.size() - 1; i >= 0; i--) {
            var map = maps.get(i);
            ResourceLocation texture = TEXTURES.computeIfAbsent(map, ImportedMapRenderer::create);
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) (width / 2.0 + (map.minX() - centerX) * zoom),
                    (float) (height / 2.0 + (map.minZ() - centerZ) * zoom));
            graphics.pose().scale(zoom * (1 << map.scale()), zoom * (1 << map.scale()));
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, 128, 128, 128, 128);
            graphics.pose().popMatrix();
        }
    }

    private static ResourceLocation create(MapSnapshot map) {
        var image = new NativeImage(128, 128, false);
        byte[] colors = map.colors();
        for (int z = 0; z < 128; z++) for (int x = 0; x < 128; x++)
            image.setPixel(x, z, MapColor.getColorFromPackedId(colors[z * 128 + x]));
        var texture = new DynamicTexture(() -> "Imported vanilla map", image);
        ResourceLocation id = AleeveAtlas.id("imported_map/" + map.mapId());
        Minecraft.getInstance().getTextureManager().register(id, texture);
        texture.upload();
        return id;
    }

    public static void clear() {
        for (ResourceLocation id : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(id);
        TEXTURES.clear();
    }
}
