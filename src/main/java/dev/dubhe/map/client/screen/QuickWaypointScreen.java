package dev.dubhe.map.client.screen;

import dev.dubhe.map.client.waypoint.Waypoint;
import dev.dubhe.map.client.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class QuickWaypointScreen extends Screen {
    private final Screen parent;
    private final Waypoint draft;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;

    public QuickWaypointScreen(Screen parent) {
        super(Component.translatable("screen.aleeve_atlas.quick_waypoint"));
        this.parent = parent;
        this.draft = WaypointManager.createAtCamera();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 62;

        this.nameBox = addRenderableWidget(new EditBox(this.font, left, top + 12, 200, 20, Component.translatable("screen.aleeve_atlas.waypoint.name")));
        this.nameBox.setValue(this.draft.name);

        this.xBox = addRenderableWidget(new EditBox(this.font, left, top + 40, 62, 20, Component.literal("X")));
        this.xBox.setValue(Integer.toString(this.draft.x));

        this.yBox = addRenderableWidget(new EditBox(this.font, left + 69, top + 40, 62, 20, Component.literal("Y")));
        this.yBox.setValue(Integer.toString(this.draft.y));

        this.zBox = addRenderableWidget(new EditBox(this.font, left + 138, top + 40, 62, 20, Component.literal("Z")));
        this.zBox.setValue(Integer.toString(this.draft.z));

        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.quick_waypoint.save"), button -> saveAndClose())
            .bounds(left, top + 70, 98, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(left + 102, top + 70, 98, 20)
            .build());

        setInitialFocus(this.nameBox);
    }

    private void saveAndClose() {
        Waypoint waypoint = this.draft.copy();
        waypoint.name = this.nameBox.getValue().isBlank() ? Component.translatable("screen.aleeve_atlas.quick_waypoint.default_name").getString() : this.nameBox.getValue();
        waypoint.x = parseInt(this.xBox.getValue(), waypoint.x);
        waypoint.y = parseInt(this.yBox.getValue(), waypoint.y);
        waypoint.z = parseInt(this.zBox.getValue(), waypoint.z);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            waypoint.dimension = minecraft.level.dimension().location().toString();
        }

        WaypointManager.upsert(waypoint);
        onClose();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = this.width / 2 - 100;
        int top = this.height / 2 - 62;
        guiGraphics.fill(0, 0, this.width, this.height, 0xA0101010);
        guiGraphics.fill(left - 4, top - 4, left + 204, top + 96, 0xB0202020);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top - 12, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("screen.aleeve_atlas.waypoint.name"), left, top, 0xA0A0A0);
        guiGraphics.drawString(this.font, Component.translatable("screen.aleeve_atlas.waypoint.xyz"), left, top + 28, 0xA0A0A0);
    }
}


