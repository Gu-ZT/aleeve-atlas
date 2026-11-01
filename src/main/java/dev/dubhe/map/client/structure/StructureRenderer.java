package dev.dubhe.map.client.structure;

import dev.dubhe.map.client.map.AtlasGui;
import dev.dubhe.map.client.map.MinimapLayout;
import dev.dubhe.map.structure.StructureKind;
import dev.dubhe.map.structure.StructureRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import dev.dubhe.map.client.map.AtlasRenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/// 图标直接取自原版图集，每帧查询当前图集以支持资源重载。
public final class StructureRenderer {
    public static final int ICON_SIZE = 12;

    private StructureRenderer() {
    }

    public static void icon(GuiGraphics graphics, StructureKind kind, double x, double y, boolean selected) {
        var atlas = (net.minecraft.client.renderer.texture.TextureAtlas) Minecraft.getInstance().getTextureManager()
                .getTexture(ResourceLocation.withDefaultNamespace("textures/atlas/map_decorations.png"));
        var sprite = atlas.getSprite(kind.sprite());
        graphics.pose().pushPose();
        graphics.pose().translate((float) x, (float) y, 0.0F);
        if (selected) {
            graphics.fill(-9, -9, 9, 9, 0xCCFFD45A);
            graphics.fill(-8, -8, 8, 8, 0xDD202020);
        }
        AtlasGui.blitSprite(graphics, AtlasRenderPipelines.GUI_TEXTURED, sprite, -ICON_SIZE / 2, -ICON_SIZE / 2, ICON_SIZE, ICON_SIZE);
        graphics.pose().popPose();
    }

    public static void minimap(GuiGraphics graphics, MinimapLayout layout, boolean circular, double rotation) {
        var store = ClientStructures.current();
        if (store == null) {
            return;
        }
        for (StructureRecord record : store.all()) {
            double[] point = layout.rotatedScreenPoint(record.position().getX() + 0.5, record.position().getZ() + 0.5, rotation);
            int margin = ICON_SIZE / 2;
            if (layout.contains(point[0] - margin, point[1] - margin, circular)
                    && layout.contains(point[0] + margin, point[1] - margin, circular)
                    && layout.contains(point[0] - margin, point[1] + margin, circular)
                    && layout.contains(point[0] + margin, point[1] + margin, circular)) {
                icon(graphics, record.kind(), point[0], point[1], false);
            }
        }
    }

    public static List<Component> description(StructureRecord record) {
        var player = Minecraft.getInstance().player;
        long distance = player == null ? 0 : Math.round(Math.sqrt(record.positionDistanceSquared(player.getX(), player.getZ())));
        return List.of(Component.translatable(record.kind().translationKey()),
                ClientStructureSearch.text("coordinates", record.position().getX(), record.position().getY(), record.position().getZ()),
                ClientStructureSearch.text("distance", distance),
                ClientStructureSearch.text("source." + record.source().name().toLowerCase(Locale.ROOT)));
    }
}
