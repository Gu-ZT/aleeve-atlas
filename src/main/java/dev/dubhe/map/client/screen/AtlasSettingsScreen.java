package dev.dubhe.map.client.screen;

import dev.dubhe.map.client.AleeveAtlasClient;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AtlasSettingsScreen extends Screen {
    private final Screen parent;

    public AtlasSettingsScreen(Screen parent) {
        super(Component.translatable("screen.aleeve_atlas.settings"));
        this.parent = parent;
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int left = this.width / 2 - 155;
        int top = 42;
        int buttonWidth = 150;
        int rowHeight = 24;

        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.display", AleeveAtlasClient.CONFIG.display), button -> {
            AleeveAtlasClient.CONFIG.display = !AleeveAtlasClient.CONFIG.display;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.shape", AleeveAtlasClient.CONFIG.mapShape), button -> {
            AleeveAtlasClient.CONFIG.mapShape = next(AleeveAtlasClient.CONFIG.mapShape);
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.rotation", AleeveAtlasClient.CONFIG.rotation), button -> {
            AleeveAtlasClient.CONFIG.rotation = !AleeveAtlasClient.CONFIG.rotation;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.zoom", AleeveAtlasClient.CONFIG.zoom), button -> {
            AleeveAtlasClient.CONFIG.zoom = AleeveAtlasClient.CONFIG.zoom >= 5 ? 1 : AleeveAtlasClient.CONFIG.zoom + 1;
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.dynamic_lighting", AleeveAtlasClient.CONFIG.dynamicLighting), button -> {
            AleeveAtlasClient.CONFIG.dynamicLighting = !AleeveAtlasClient.CONFIG.dynamicLighting;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.cave_mapping", AleeveAtlasClient.CONFIG.caveMapping), button -> {
            AleeveAtlasClient.CONFIG.caveMapping = !AleeveAtlasClient.CONFIG.caveMapping;
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.coordinates", AleeveAtlasClient.CONFIG.showCoordinates), button -> {
            AleeveAtlasClient.CONFIG.showCoordinates = !AleeveAtlasClient.CONFIG.showCoordinates;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.environment", AleeveAtlasClient.CONFIG.showEnvironment), button -> {
            AleeveAtlasClient.CONFIG.showEnvironment = !AleeveAtlasClient.CONFIG.showEnvironment;
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.anchor", AleeveAtlasClient.CONFIG.minimapAnchor), button -> {
            AleeveAtlasClient.CONFIG.minimapAnchor = next(AleeveAtlasClient.CONFIG.minimapAnchor);
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.size", AleeveAtlasClient.CONFIG.minimapSize), button -> {
            AleeveAtlasClient.CONFIG.minimapSize = next(AleeveAtlasClient.CONFIG.minimapSize);
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar", AleeveAtlasClient.CONFIG.radar), button -> {
            AleeveAtlasClient.CONFIG.radar = !AleeveAtlasClient.CONFIG.radar;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar_range", AleeveAtlasClient.CONFIG.radarRange.blocks()), button -> {
            AleeveAtlasClient.CONFIG.radarRange = next(AleeveAtlasClient.CONFIG.radarRange);
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar_hostile", AleeveAtlasClient.CONFIG.radarHostile), button -> {
            AleeveAtlasClient.CONFIG.radarHostile = !AleeveAtlasClient.CONFIG.radarHostile;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar_friendly", AleeveAtlasClient.CONFIG.radarFriendly), button -> {
            AleeveAtlasClient.CONFIG.radarFriendly = !AleeveAtlasClient.CONFIG.radarFriendly;
            persistAndRebuild();
        }));

        top += rowHeight;
        addRenderableWidget(optionButton(left, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar_items", AleeveAtlasClient.CONFIG.radarItems), button -> {
            AleeveAtlasClient.CONFIG.radarItems = !AleeveAtlasClient.CONFIG.radarItems;
            persistAndRebuild();
        }));
        addRenderableWidget(optionButton(left + 160, top, buttonWidth, () -> label("screen.aleeve_atlas.option.radar_players", AleeveAtlasClient.CONFIG.radarPlayers), button -> {
            AleeveAtlasClient.CONFIG.radarPlayers = !AleeveAtlasClient.CONFIG.radarPlayers;
            persistAndRebuild();
        }));

        top += rowHeight + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
            .bounds(this.width / 2 - 75, top, 150, 20)
            .build());
    }

    private Button optionButton(int x, int y, int width, Supplier<Component> message, Button.OnPress onPress) {
        return Button.builder(message.get(), onPress).bounds(x, y, width, 20).build();
    }

    private void persistAndRebuild() {
        AleeveAtlasClient.CONFIG.normalize();
        rebuildWidgets();
    }

    private Component label(String key, Object value) {
        return Component.translatable(key, prettyValue(value));
    }

    private Component prettyValue(Object value) {
        if (value instanceof Boolean bool) {
            return Component.translatable(bool ? "screen.aleeve_atlas.value.on" : "screen.aleeve_atlas.value.off");
        }
        String name = String.valueOf(value).toLowerCase();
        return switch (name) {
            case "square" -> Component.translatable("screen.aleeve_atlas.value.square");
            case "circle" -> Component.translatable("screen.aleeve_atlas.value.circle");
            case "top_left" -> Component.translatable("screen.aleeve_atlas.value.top_left");
            case "top_right" -> Component.translatable("screen.aleeve_atlas.value.top_right");
            case "bottom_left" -> Component.translatable("screen.aleeve_atlas.value.bottom_left");
            case "bottom_right" -> Component.translatable("screen.aleeve_atlas.value.bottom_right");
            case "small" -> Component.translatable("screen.aleeve_atlas.value.small");
            case "medium" -> Component.translatable("screen.aleeve_atlas.value.medium");
            case "large" -> Component.translatable("screen.aleeve_atlas.value.large");
            default -> Component.literal(String.valueOf(value));
        };
    }

    private static <E extends Enum<E>> E next(E current) {
        E[] values = current.getDeclaringClass().getEnumConstants();
        return values[(current.ordinal() + 1) % values.length];
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, Component.translatable("screen.aleeve_atlas.settings.hint"), this.width / 2, 28, 0xB0B0B0);
    }
}


