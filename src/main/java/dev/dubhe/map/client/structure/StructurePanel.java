package dev.dubhe.map.client.structure;

import com.mojang.blaze3d.platform.InputConstants;
import dev.dubhe.map.structure.StructureKind;
import dev.dubhe.map.structure.StructureRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/// 世界地图的结构侧栏，区域内输入只作用于侧栏。
public final class StructurePanel {
    private final Consumer<StructureRecord> focus;
    private final BooleanSupplier active;
    private final List<Button> buttons = new ArrayList<>();
    private List<StructureRecord> results = List.of();
    private @Nullable StructureKind kind;
    private boolean favoritesOnly;
    private boolean open;
    private @Nullable String selected;
    private int offset;
    private int width;
    private int bottom;
    private int listBottom;
    private long revision = -1;
    private Component message = Component.empty();
    private @Nullable Button filter;
    private @Nullable Button favorites;
    private @Nullable Button follow;
    private @Nullable Button locate;
    private @Nullable Button favorite;

    public StructurePanel(Consumer<StructureRecord> focus, BooleanSupplier active) {
        this.focus = focus;
        this.active = active;
    }

    public void init(int screenWidth, int screenHeight, Consumer<Button> add) {
        this.buttons.clear();
        this.width = Math.min(214, screenWidth - 12);
        this.bottom = screenHeight - 22;
        this.listBottom = Math.max(106, this.bottom - 45);
        this.filter = this.button(add, this.typeLabel(), 38, b -> {
            this.kind = this.kind == null ? StructureKind.values()[0]
                    : this.kind.ordinal() + 1 == StructureKind.values().length ? null : StructureKind.values()[this.kind.ordinal() + 1];
            this.refresh();
        });
        this.favorites = this.button(add, ClientStructureSearch.text("all_records"), 60, b -> {
            this.favoritesOnly = !this.favoritesOnly;
            this.refresh();
        });
        this.follow = this.button(add, ClientStructureSearch.text("follow"), 60, b -> {
            var store = ClientStructures.current();
            if (store != null && this.kind != null) {
                store.toggleFollowed(this.kind);
                store.flush();
                this.refresh();
            }
        });
        Button known = this.button(add, ClientStructureSearch.text("search_known"), 82, b -> this.refresh());
        this.locate = this.button(add, ClientStructureSearch.text("search_nearest"), 82, b -> {
            if (this.kind != null && ClientStructureSearch.search(this.kind, result -> {
                this.message = ClientStructureSearch.status(result.status());
                this.refresh();
                if (result.record() != null && this.open && this.active.getAsBoolean()) {
                    this.select(result.record());
                }
            })) {
                this.message = ClientStructureSearch.text("searching");
            }
        });
        int half = (this.width - 16) / 2;
        this.favorites.setWidth(half);
        this.follow.setWidth(half);
        this.follow.setX(16 + half);
        known.setWidth(half);
        this.locate.setWidth(half);
        this.locate.setX(16 + half);
        this.favorite = this.button(add, ClientStructureSearch.text("favorite"), this.bottom - 21, b -> {
            var store = ClientStructures.current();
            if (store != null && this.selected != null) {
                store.toggleFavorite(this.selected);
                store.flush();
                this.refresh();
            }
        });
        this.refresh();
        this.update();
    }

    private Button button(Consumer<Button> add, Component label, int y, Button.OnPress action) {
        Button button = Button.builder(label, action).bounds(12, y, this.width - 12, 20).build();
        this.buttons.add(button);
        add.accept(button);
        return button;
    }

    public boolean open() {
        return this.open;
    }

    public int width() {
        return this.open ? this.width + 6 : 0;
    }

    public void toggle() {
        this.open = !this.open;
        if (!this.open) {
            this.selected = null;
        }
        this.refresh();
        this.update();
    }

    public void select(StructureRecord record) {
        this.open = true;
        this.selected = record.key();
        this.focus.accept(record);
        this.update();
    }

    public boolean selected(StructureRecord record) {
        return this.open && record.key().equals(this.selected);
    }

    private Component typeLabel() {
        return this.kind == null ? ClientStructureSearch.text("all_types") : Component.translatable(this.kind.translationKey());
    }

    private void refresh() {
        var store = ClientStructures.current();
        var player = Minecraft.getInstance().player;
        this.results = store == null || player == null ? List.of()
                : store.search(this.kind, this.favoritesOnly, player.getX(), player.getZ());
        this.revision = store == null ? -1 : store.revision();
        this.offset = Math.min(this.offset, Math.max(0, this.results.size() - this.visibleRows()));
    }

    public void update() {
        var store = ClientStructures.current();
        if (store != null && store.revision() != this.revision) {
            this.refresh();
        }
        this.buttons.forEach(b -> b.visible = this.open);
        if (this.filter == null || this.favorites == null || this.follow == null || this.locate == null || this.favorite == null) {
            return;
        }
        this.filter.setMessage(this.typeLabel());
        this.favorites.setMessage(ClientStructureSearch.text(this.favoritesOnly ? "favorites_only" : "all_records"));
        this.follow.active = this.kind != null && store != null;
        this.follow.setMessage(ClientStructureSearch.text(store != null && this.kind != null
                && store.followed(this.kind) ? "unfollow" : "follow"));
        this.locate.active = this.kind != null && ClientStructureSearch.allowed() && !ClientStructureSearch.busy();
        this.locate.setTooltip(Tooltip.create(ClientStructureSearch.text(ClientStructureSearch.allowed()
                ? "locate_tip" : "status.disabled")));
        this.favorite.active = store != null && this.selected != null && store.get(this.selected) != null;
        this.favorite.setMessage(ClientStructureSearch.text(store != null && this.selected != null
                && store.favorite(this.selected) ? "unfavorite" : "favorite"));
        var record = store == null || this.selected == null ? null : store.get(this.selected);
        this.favorite.setTooltip(record == null ? null : Tooltip.create(Component.translatable(record.kind().translationKey())
                .append(" " + record.position().getX() + ", " + record.position().getZ())));
    }

    public boolean contains(double x, double y) {
        return this.open && x >= 6 && x <= this.width + 6 && y >= 32 && y <= this.bottom;
    }

    private int visibleRows() {
        return Math.max(0, (this.listBottom - 106) / 24);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.open) {
            return;
        }
        var font = Minecraft.getInstance().font;
        graphics.fill(6, 32, this.width + 6, this.bottom, 0xEE181818);
        graphics.enableScissor(10, 106, this.width + 2, this.listBottom);
        for (int row = 0; row < this.visibleRows() && this.offset + row < this.results.size(); row++) {
            StructureRecord record = this.results.get(this.offset + row);
            int y = 106 + row * 24;
            boolean hovered = mouseX >= 10 && mouseX < this.width && mouseY >= y && mouseY < y + 24;
            graphics.fill(10, y, this.width + 2, y + 23, this.selected(record) ? 0xFF605133 : hovered ? 0xFF404040 : 0xFF282828);
            StructureRenderer.icon(graphics, record.kind(), 21, y + 11, false);
            graphics.text(font, font.plainSubstrByWidth(Component.translatable(record.kind().translationKey()).getString(),
                    this.width - 44), 32, y + 2, 0xFFFFFFFF);
            graphics.text(font, record.position().getX() + ", " + record.position().getZ(), 32, y + 13, 0xFFBBBBBB);
            if (hovered) {
                graphics.setComponentTooltipForNextFrame(font, StructureRenderer.description(record), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
        if (this.results.isEmpty()) {
            graphics.textWithWordWrap(font, ClientStructureSearch.text("empty"), 12, 109, this.width - 18, 0xFFBBBBBB);
        }
        if (this.results.size() > this.visibleRows() && this.visibleRows() > 0) {
            int height = this.visibleRows() * 24;
            int thumb = Math.max(8, height * this.visibleRows() / this.results.size());
            int top = 106 + (height - thumb) * this.offset / (this.results.size() - this.visibleRows());
            graphics.fill(this.width + 2, top, this.width + 5, top + thumb, 0xFFCCCCCC);
        }
        graphics.text(font, font.plainSubstrByWidth(this.message.getString(), this.width - 18),
                12, this.listBottom + 5, 0xFFFFDD88);
        if (this.contains(mouseX, mouseY) && mouseY >= this.listBottom && mouseY < this.bottom - 21) {
            graphics.setTooltipForNextFrame(this.message, mouseX, mouseY);
        }
    }

    public boolean click(MouseButtonEvent event) {
        if (!this.contains(event.x(), event.y())) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() >= 106 && event.y() < 106 + this.visibleRows() * 24) {
            int index = this.offset + (int) (event.y() - 106) / 24;
            if (index < this.results.size()) {
                this.select(this.results.get(index));
            }
        }
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!this.contains(x, y)) {
            return false;
        }
        this.offset = Math.max(0, Math.min(Math.max(0, this.results.size() - this.visibleRows()),
                this.offset - (int) Math.signum(amount) * 3));
        return true;
    }
}
