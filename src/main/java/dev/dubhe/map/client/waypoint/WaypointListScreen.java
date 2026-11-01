package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.WaypointSendPolicy;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// 当前维度的路径点管理列表，先选择条目，再使用底部按钮执行操作。
public final class WaypointListScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 36;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int BOTTOM_RESERVED = 78;

    private final Screen parent;
    private final List<Waypoint> entries = new ArrayList<>();
    private final List<Button> selectionButtons = new ArrayList<>();
    private int scrollOffset;
    private boolean scrollbarDragging;
    private UUID hoveredLabel;
    private long hoverStartedAt;
    private UUID selectedId;
    private Button toggleButton;
    private Button shareButton;
    private Button shareableButton;

    public WaypointListScreen(Screen parent) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_list"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.reloadEntries();
    }

    @Override
    public void added() {
        super.added();
        this.reloadEntries();
    }

    void reloadEntries() {
        this.entries.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            this.entries.addAll(ClientWaypoints.allInDimension(minecraft.level.dimension()));
        }
        if (this.selectedWaypoint() == null) {
            this.selectedId = null;
        }
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScrollOffset());
        this.rebuildRows();
    }

    private void rebuildRows() {
        clearWidgets();
        this.selectionButtons.clear();
        this.buildBottomButtons();
        this.updateButtonStates();
    }

    private void buildBottomButtons() {
        this.addBottomButton(Component.translatable("aleeve_atlas.screen.waypoint_list.add"),
                button -> this.create(), 0, 0, false);
        this.addBottomButton(Component.translatable("aleeve_atlas.screen.waypoint_list.edit"),
                button -> this.editSelected(), 0, 1, true);
        this.toggleButton = this.addBottomButton(Component.translatable(
                "aleeve_atlas.screen.waypoint_list.hide"),
                button -> this.toggleSelected(), 0, 2, true);
        this.shareButton = this.addBottomButton(Component.translatable(
                "aleeve_atlas.screen.waypoint_list.share"),
                button -> this.shareSelected(), 0, 3, true);

        this.addBottomButton(Component.translatable("aleeve_atlas.screen.world_map.teleport"),
                button -> this.teleportSelected(), 1, 0, true);
        this.shareableButton = this.addBottomButton(Component.translatable(
                "aleeve_atlas.screen.waypoint_list.public"),
                button -> this.toggleShareableSelected(), 1, 1, true);
        this.addBottomButton(Component.translatable("aleeve_atlas.screen.waypoint_list.delete"),
                button -> this.deleteSelected(), 1, 2, true);
        this.addBottomButton(Component.translatable("gui.back"), button -> this.onClose(),
                1, 3, false);
    }

    private Button addBottomButton(Component label, Button.OnPress action,
            int row, int column, boolean requiresSelection) {
        int buttonWidth = (width - 30) / 4;
        Button button = Button.builder(label, action)
                .bounds(6 + column * (buttonWidth + 6), height - 50 + row * 24,
                        buttonWidth, 20)
                .build();
        if (requiresSelection) {
            this.selectionButtons.add(button);
        }
        return addRenderableWidget(button);
    }

    private void updateButtonStates() {
        Waypoint selected = this.selectedWaypoint();
        for (Button button : this.selectionButtons) {
            button.active = selected != null;
        }
        if (selected != null) {
            this.toggleButton.setMessage(Component.translatable(selected.enabled()
                    ? "aleeve_atlas.screen.waypoint_list.hide"
                    : "aleeve_atlas.screen.waypoint_list.show"));
            this.shareableButton.setMessage(Component.translatable(selected.shareable()
                    ? "aleeve_atlas.screen.waypoint_list.public"
                    : "aleeve_atlas.screen.waypoint_list.private"));
            this.shareButton.active = AtlasSession.synchronizedMode() && selected.shareable();
        }
    }

    private Waypoint selectedWaypoint() {
        if (this.selectedId == null) {
            return null;
        }
        for (Waypoint waypoint : this.entries) {
            if (waypoint.id().equals(this.selectedId)) {
                return waypoint;
            }
        }
        return null;
    }

    private void create() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.gui.setScreen(new WaypointEditScreen(this, minecraft.player.blockPosition()));
        }
    }

    private void editSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint != null) {
            Minecraft.getInstance().gui.setScreen(new WaypointEditScreen(this, waypoint));
        }
    }

    private void toggleSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint != null) {
            ClientWaypoints.update(waypoint.withEnabled(!waypoint.enabled()));
            this.reloadEntries();
        }
    }

    private void toggleShareableSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint != null) {
            ClientWaypoints.update(waypoint.withShareable(!waypoint.shareable()));
            this.reloadEntries();
        }
    }

    private void shareSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint == null) {
            return;
        }
        ClientWaypoints.share(waypoint);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_shared_team",
                    waypoint.displayName().getString()));
        }
    }

    private void teleportSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint != null) {
            WaypointActions.teleport(waypoint.pos());
        }
    }

    private void deleteSelected() {
        Waypoint waypoint = this.selectedWaypoint();
        if (waypoint == null) {
            return;
        }
        var store = ClientWaypoints.store();
        if (store != null) {
            store.remove(waypoint.id());
        }
        if (AtlasClientConfigs.CONFIG.waypoint.sendPolicy == WaypointSendPolicy.AUTO_SYNC) {
            ClientWaypoints.shareDelete(waypoint);
        }
        this.selectedId = null;
        this.reloadEntries();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.text(font, title, 12, 12, 0xFFFFFFFF, true);
        if (this.entries.isEmpty()) {
            graphics.text(font,
                    Component.translatable("aleeve_atlas.screen.waypoint_list.empty"),
                    12, LIST_TOP, 0xFFAAAAAA, true);
        } else {
            for (int row = 0; row < this.visibleRows(); row++) {
                int index = this.scrollOffset + row;
                if (index >= this.entries.size()) {
                    break;
                }
                Waypoint waypoint = this.entries.get(index);
                int y = LIST_TOP + row * ROW_HEIGHT;
                if (waypoint.id().equals(this.selectedId)) {
                    graphics.fill(6, y, width - 16, y + 20, 0x6044AAFF);
                }
                int color = waypoint.enabled() ? waypoint.colorArgb() : 0xFF666666;
                graphics.fill(6, y, 10, y + 18, color);
                this.drawWaypointLabel(graphics, waypoint, y, mouseX, mouseY);
            }
        }
        graphics.text(font,
                Component.translatable("aleeve_atlas.screen.waypoint_list.scroll_hint"),
                12, height - 66, 0xFF888888, true);
        this.drawScrollbar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawWaypointLabel(GuiGraphicsExtractor graphics, Waypoint waypoint, int y,
            int mouseX, int mouseY) {
        int labelX = 14;
        int maxWidth = Math.max(0, width - 40);
        Component line = Component.literal("%s  (%d, %d, %d)".formatted(
                        waypoint.displayName().getString(), waypoint.pos().getX(),
                        waypoint.pos().getY(), waypoint.pos().getZ()))
                .withStyle(waypoint.enabled() ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY);
        Component death = waypoint.deathPoint()
                ? Component.translatable("aleeve_atlas.screen.waypoint_list.death")
                : Component.empty();
        int deathWidth = waypoint.deathPoint() ? font.width(death) + 6 : 0;
        int textWidth = font.width(line) + deathWidth;
        boolean overflow = textWidth > maxWidth;
        boolean hovered = overflow && mouseX >= 12 && mouseX <= 12 + maxWidth
                && mouseY >= y && mouseY < y + ROW_HEIGHT;
        double offset = 0.0;
        if (hovered) {
            if (!waypoint.id().equals(this.hoveredLabel)) {
                this.hoveredLabel = waypoint.id();
                this.hoverStartedAt = System.currentTimeMillis();
            }
            offset = Math.min(textWidth - maxWidth,
                    (System.currentTimeMillis() - this.hoverStartedAt) / 50.0);
        } else if (waypoint.id().equals(this.hoveredLabel)) {
            this.hoveredLabel = null;
        }
        if (overflow) {
            graphics.enableScissor(12, y, 12 + maxWidth, y + 18);
        }
        int drawX = (int) Math.round(labelX - offset);
        graphics.text(font, line, drawX, y + 5, 0xFFFFFFFF, true);
        if (waypoint.deathPoint()) {
            graphics.text(font, death, drawX + font.width(line) + 6, y + 5,
                    0xFFFF5555, true);
        }
        if (overflow) {
            graphics.disableScissor();
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int x = width - SCROLLBAR_WIDTH - 4;
        int trackHeight = this.listHeight();
        graphics.fill(x, LIST_TOP, x + SCROLLBAR_WIDTH, LIST_TOP + trackHeight, 0xFF202020);
        int thumbHeight = this.scrollbarThumbHeight();
        int travel = Math.max(0, this.maxScrollOffset());
        int thumbY = LIST_TOP + (travel == 0 ? 0
                : Math.round((float) (trackHeight - thumbHeight) * this.scrollOffset / travel));
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    private int scrollbarThumbHeight() {
        if (this.entries.isEmpty()) {
            return this.listHeight();
        }
        return Math.min(this.listHeight(),
                Math.max(12, this.listHeight() * this.visibleRows() / this.entries.size()));
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= width - SCROLLBAR_WIDTH - 6 && mouseX <= width
                && mouseY >= LIST_TOP && mouseY <= LIST_TOP + this.listHeight();
    }

    private void updateScrollFromMouse(double mouseY) {
        int maxOffset = this.maxScrollOffset();
        if (maxOffset <= 0) {
            this.scrollOffset = 0;
            return;
        }
        int thumbHeight = this.scrollbarThumbHeight();
        int travel = Math.max(1, this.listHeight() - thumbHeight);
        double ratio = (mouseY - LIST_TOP - thumbHeight / 2.0) / travel;
        int newOffset = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * maxOffset);
        if (newOffset != this.scrollOffset) {
            this.scrollOffset = newOffset;
            this.rebuildRows();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && this.overScrollbar(event.x(), event.y())) {
            this.scrollbarDragging = true;
            this.updateScrollFromMouse(event.y());
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.x() >= 6 && event.x() < width - 16) {
            int row = (int) Math.floor((event.y() - LIST_TOP) / ROW_HEIGHT);
            int index = this.scrollOffset + row;
            if (row >= 0 && row < this.visibleRows() && index < this.entries.size()) {
                this.selectedId = this.entries.get(index).id();
                this.updateButtonStates();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.scrollbarDragging) {
            this.updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.scrollbarDragging) {
            this.scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            int newOffset = Math.max(0, Math.min(this.maxScrollOffset(),
                    this.scrollOffset + (scrollY > 0 ? -1 : 1)));
            if (newOffset != this.scrollOffset) {
                this.scrollOffset = newOffset;
                this.rebuildRows();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - BOTTOM_RESERVED) / ROW_HEIGHT);
    }

    private int listHeight() {
        return this.visibleRows() * ROW_HEIGHT;
    }

    private int maxScrollOffset() {
        return Math.max(0, this.entries.size() - this.visibleRows());
    }

    int scrollOffset() {
        return this.scrollOffset;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
