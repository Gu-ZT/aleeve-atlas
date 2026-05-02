package dev.dubhe.map.client.screen;

import dev.dubhe.map.client.waypoint.Waypoint;
import dev.dubhe.map.client.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

public class WaypointListScreen extends Screen {
    private final @Nullable Screen parent;
    private @Nullable EditBox nameBox;
    private @Nullable EditBox xBox;
    private @Nullable EditBox yBox;
    private @Nullable EditBox zBox;
    private @Nullable EditBox colorBox;
    private int selectedIndex = -1;

    public WaypointListScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.aleeve_atlas.waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int editorLeft = this.width / 2 - 10;
        int top = 56;

        this.nameBox = addRenderableWidget(new EditBox(
            this.font,
            editorLeft,
            top,
            160,
            20,
            Component.translatable("screen.aleeve_atlas.waypoint.name")
        ));
        this.xBox = addRenderableWidget(new EditBox(this.font, editorLeft, top + 28, 50, 20, Component.literal("X")));
        this.yBox = addRenderableWidget(new EditBox(this.font, editorLeft + 55, top + 28, 50, 20, Component.literal("Y")));
        this.zBox = addRenderableWidget(new EditBox(this.font, editorLeft + 110, top + 28, 50, 20, Component.literal("Z")));
        this.colorBox = addRenderableWidget(new EditBox(
            this.font,
            editorLeft,
            top + 56,
            160,
            20,
            Component.translatable("screen.aleeve_atlas.waypoint.color")
        ));
        this.colorBox.setValue("#FF55FF");

        int buttonY = top + 88;
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.previous"), ignored -> selectRelative(-1))
            .bounds(editorLeft, buttonY, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.next"), ignored -> selectRelative(1))
            .bounds(editorLeft + 85, buttonY, 75, 20)
            .build());

        buttonY += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.use_player"), ignored -> fillFromPlayer())
            .bounds(editorLeft, buttonY, 160, 20)
            .build());

        buttonY += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.new"), ignored -> newDraft())
            .bounds(editorLeft, buttonY, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.save"), ignored -> saveCurrent())
            .bounds(editorLeft + 85, buttonY, 75, 20)
            .build());

        buttonY += 24;
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.delete"), ignored -> deleteCurrent())
            .bounds(editorLeft, buttonY, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("screen.aleeve_atlas.waypoint.set_active"), ignored -> setActiveCurrent())
            .bounds(editorLeft + 85, buttonY, 75, 20)
            .build());

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(this.width / 2 - 75, this.height - 28, 150, 20)
            .build());

        if (WaypointManager.getWaypoints().isEmpty()) {
            newDraft();
        } else {
            this.selectedIndex = 0;
            loadSelectedIntoInputs();
        }
    }

    private void selectRelative(int direction) {
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        if (waypoints.isEmpty()) {
            newDraft();
            return;
        }
        this.selectedIndex = Math.floorMod(this.selectedIndex + direction, waypoints.size());
        loadSelectedIntoInputs();
    }

    private void newDraft() {
        this.selectedIndex = -1;
        Waypoint draft = WaypointManager.createAtPlayer();
        applyToInputs(draft);
    }

    private void fillFromPlayer() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        BlockPos pos = minecraft.player.blockPosition();
        if (this.xBox != null) {
            this.xBox.setValue(Integer.toString(pos.getX()));
        }
        if (this.yBox != null) {
            this.yBox.setValue(Integer.toString(pos.getY()));
        }
        if (this.zBox != null) {
            this.zBox.setValue(Integer.toString(pos.getZ()));
        }
        if (this.selectedIndex < 0 && this.nameBox != null && this.nameBox.getValue().isBlank()) {
            this.nameBox.setValue("Waypoint " + (WaypointManager.getWaypoints().size() + 1));
        }
    }

    private void loadSelectedIntoInputs() {
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        if (this.selectedIndex < 0 || this.selectedIndex >= waypoints.size()) {
            newDraft();
            return;
        }
        applyToInputs(waypoints.get(this.selectedIndex));
    }

    private void applyToInputs(Waypoint waypoint) {
        if (this.nameBox != null) {
            this.nameBox.setValue(waypoint.name);
        }
        if (this.xBox != null) {
            this.xBox.setValue(Integer.toString(waypoint.x));
        }
        if (this.yBox != null) {
            this.yBox.setValue(Integer.toString(waypoint.y));
        }
        if (this.zBox != null) {
            this.zBox.setValue(Integer.toString(waypoint.z));
        }
        if (this.colorBox != null) {
            this.colorBox.setValue(String.format(Locale.ROOT, "#%06X", waypoint.color & 0xFFFFFF));
        }
    }

    private void saveCurrent() {
        Minecraft minecraft = Minecraft.getInstance();
        Waypoint waypoint;
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        if (this.selectedIndex >= 0 && this.selectedIndex < waypoints.size()) {
            waypoint = waypoints.get(this.selectedIndex).copy();
        } else {
            waypoint = WaypointManager.createAtPlayer();
            this.selectedIndex = waypoints.size();
        }

        if (this.nameBox != null) {
            waypoint.name = this.nameBox.getValue().isBlank() ? "Waypoint" : this.nameBox.getValue();
        }
        if (this.xBox != null) {
            waypoint.x = parseInt(this.xBox.getValue(), waypoint.x);
        }
        if (this.yBox != null) {
            waypoint.y = parseInt(this.yBox.getValue(), waypoint.y);
        }
        if (this.zBox != null) {
            waypoint.z = parseInt(this.zBox.getValue(), waypoint.z);
        }
        if (this.colorBox != null) {
            waypoint.color = parseColor(this.colorBox.getValue(), waypoint.color);
        }
        if (minecraft.level != null) {
            waypoint.dimension = minecraft.level.dimension().identifier().toString();
        }
        WaypointManager.upsert(waypoint);
        this.selectedIndex = WaypointManager.getWaypoints().stream().map(existing -> existing.id).toList().indexOf(waypoint.id);
        loadSelectedIntoInputs();
    }

    private void deleteCurrent() {
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        if (this.selectedIndex < 0 || this.selectedIndex >= waypoints.size()) {
            return;
        }
        WaypointManager.delete(waypoints.get(this.selectedIndex).id);
        if (WaypointManager.getWaypoints().isEmpty()) {
            newDraft();
        } else {
            this.selectedIndex = Math.min(this.selectedIndex, WaypointManager.getWaypoints().size() - 1);
            loadSelectedIntoInputs();
        }
    }

    private void setActiveCurrent() {
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        if (this.selectedIndex < 0 || this.selectedIndex >= waypoints.size()) {
            return;
        }
        WaypointManager.setActive(waypoints.get(this.selectedIndex).id);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseColor(String raw, int fallback) {
        String value = raw.trim().replace("#", "");
        if (value.length() != 6) {
            return fallback;
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int listLeft = this.width / 2 - 170;
        int listTop = 56;
        int rowHeight = 18;
        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        for (int i = 0; i < Math.min(waypoints.size(), 12); i++) {
            int rowY = listTop + i * rowHeight;
            if (mouseX >= listLeft && mouseX <= listLeft + 150 && mouseY >= rowY && mouseY <= rowY + rowHeight) {
                this.selectedIndex = i;
                loadSelectedIntoInputs();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Avoid Screen blur pass conflict: NeoForge 1.21.8 allows blur only once per frame.
        guiGraphics.fill(0, 0, this.width, this.height, 0xA0101010);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        guiGraphics.text(this.font, Component.translatable("screen.aleeve_atlas.waypoint.list"), this.width / 2 - 170, 40, 0xFFFFFF);
        guiGraphics.text(this.font, Component.translatable("screen.aleeve_atlas.waypoint.editor"), this.width / 2 - 10, 40, 0xFFFFFF);

        List<Waypoint> waypoints = WaypointManager.getWaypoints();
        int listLeft = this.width / 2 - 170;
        int listTop = 56;
        for (int i = 0; i < Math.min(waypoints.size(), 12); i++) {
            Waypoint waypoint = waypoints.get(i);
            int rowY = listTop + i * 18;
            int bg = i == this.selectedIndex ? 0x804466AA : 0x40222222;
            guiGraphics.fill(listLeft, rowY, listLeft + 150, rowY + 16, bg);
            String activePrefix = WaypointManager.getActiveWaypoint().map(active -> active.id.equals(waypoint.id) ? "★ " : "").orElse("");
            guiGraphics.text(this.font, activePrefix + waypoint.name, listLeft + 4, rowY + 4, 0xFFFFFF, false);
        }

        guiGraphics.text(this.font, Component.translatable("screen.aleeve_atlas.waypoint.name"), this.width / 2 - 10, 46, 0xA0A0A0);
        guiGraphics.text(this.font, Component.translatable("screen.aleeve_atlas.waypoint.xyz"), this.width / 2 - 10, 74, 0xA0A0A0);
        guiGraphics.text(this.font, Component.translatable("screen.aleeve_atlas.waypoint.color"), this.width / 2 - 10, 102, 0xA0A0A0);
    }
}

