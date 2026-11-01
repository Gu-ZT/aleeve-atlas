package dev.dubhe.map.client.waypoint;

import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/// 路径点创建与编辑表单。
///
/// 内容高度不足时可滚动，保存按钮固定在底部；窗口调整大小后保留未保存的输入。
public final class WaypointEditScreen extends Screen {
    private static final int FORM_WIDTH = 300;
    private static final int CONTENT_HEIGHT = 204;
    private static final int CONTENT_TOP = 20;
    private static final int MAX_VISIBLE_DISTANCE = 100000;
    private static final int[] PALETTE = {
        0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55,
        0x55FFFF, 0x5555FF, 0xFF55FF, 0xFFFFFF
    };

    private final Screen parent;
    private final Waypoint original;
    private final BlockPos initialPos;
    private final List<Button> paletteButtons = new ArrayList<>();

    private EditBox nameBox;
    private EditBox coordinateXBox;
    private EditBox coordinateYBox;
    private EditBox coordinateZBox;
    private EditBox colorBox;
    private EditBox distanceBox;
    private Checkbox enabledBox;
    private Checkbox shareableBox;
    private Button saveButton;
    private Component errorMessage;
    private int previewColor;
    private int formTop;
    private int formScroll;

    private String nameValue;
    private String coordinateXValue;
    private String coordinateYValue;
    private String coordinateZValue;
    private String colorValue;
    private String distanceValue;
    private Boolean enabledValue;
    private Boolean shareableValue;

    /// 创建路径点，坐标由地图点击位置或玩家当前位置提供。
    public WaypointEditScreen(Screen parent, BlockPos initialPos) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_edit.create_title"));
        this.parent = parent;
        this.original = null;
        this.initialPos = initialPos;
        this.previewColor = 0xFF55FFFF;
    }

    /// 编辑已有路径点，保留 UUID、维度、图标与来源信息。
    public WaypointEditScreen(Screen parent, Waypoint waypoint) {
        super(Component.translatable("aleeve_atlas.screen.waypoint_edit.edit_title"));
        this.parent = parent;
        this.original = waypoint;
        this.initialPos = waypoint.pos();
        this.previewColor = waypoint.colorArgb();
    }

    @Override
    protected void init() {
        this.captureValues();
        this.paletteButtons.clear();
        final int left = width / 2 - FORM_WIDTH / 2;
        Waypoint values = this.original;
        String name = this.nameValue != null ? this.nameValue : values == null
                ? Component.translatable("aleeve_atlas.waypoint.default_name").getString()
                : values.displayName().getString();
        final int color = values == null ? 0xFF55FFFF : values.colorArgb();
        final int visibleDistance = values == null ? Waypoint.DEFAULT_VISIBLE_DISTANCE
                : values.visibleDistance();

        this.nameBox = addRenderableWidget(new EditBox(font, left, 0, FORM_WIDTH, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.name")));
        this.nameBox.setMaxLength(64);
        this.nameBox.setValue(name);

        int coordinateWidth = 92;
        this.coordinateXBox = this.coordinateBox(left, coordinateWidth,
                this.coordinateXValue != null ? this.coordinateXValue : Integer.toString(this.initialPos.getX()), "X");
        this.coordinateYBox = this.coordinateBox(left + 104, coordinateWidth,
                this.coordinateYValue != null ? this.coordinateYValue : Integer.toString(this.initialPos.getY()), "Y");
        this.coordinateZBox = this.coordinateBox(left + 208, coordinateWidth,
                this.coordinateZValue != null ? this.coordinateZValue : Integer.toString(this.initialPos.getZ()), "Z");

        this.colorBox = addRenderableWidget(new EditBox(font, left, 0, 140, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.color")));
        this.colorBox.setMaxLength(7);
        this.colorBox.setValue(this.colorValue != null ? this.colorValue : formatColor(color));

        for (int paletteColor : PALETTE) {
            String hex = formatColor(paletteColor);
            Button button = Button.builder(
                            Component.empty(),
                            pressed -> this.colorBox.setValue(hex))
                    .bounds(0, 0, 24, 20)
                    .tooltip(Tooltip.create(Component.literal(hex)))
                    .createNarration(supplier -> Component.translatable(
                            "aleeve_atlas.screen.waypoint_edit.color").append(" " + hex))
                    .build();
            this.paletteButtons.add(addRenderableWidget(button));
        }

        this.distanceBox = addRenderableWidget(new EditBox(font, left, 0, 140, 20,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.visible_distance")));
        this.distanceBox.setMaxLength(6);
        this.distanceBox.setFilter(value -> value.matches("-?\\d*"));
        this.distanceBox.setValue(this.distanceValue != null ? this.distanceValue
                : Integer.toString(visibleDistance));

        this.enabledBox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.enabled"), font)
                .pos(left, 0)
                .selected(this.enabledValue != null ? this.enabledValue : values == null || values.enabled())
                .build());
        this.shareableBox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.shareable"), font)
                .pos(left + 150, 0)
                .selected(this.shareableValue != null ? this.shareableValue
                        : values == null || values.shareable())
                .build());

        this.saveButton = addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_edit.save"),
                        button -> this.save())
                .bounds(width / 2 - 105, height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),
                        button -> this.onClose())
                .bounds(width / 2 + 5, height - 28, 100, 20).build());

        this.nameBox.setResponder(value -> this.validateForm());
        this.coordinateXBox.setResponder(value -> this.validateForm());
        this.coordinateYBox.setResponder(value -> this.validateForm());
        this.coordinateZBox.setResponder(value -> this.validateForm());
        this.colorBox.setResponder(value -> this.validateForm());
        this.distanceBox.setResponder(value -> this.validateForm());
        this.validateForm();
        this.layoutForm();
    }

    /// 保存 resize 前的输入，避免 init 重建控件时丢失修改。
    private void captureValues() {
        if (this.nameBox == null) {
            return;
        }
        this.nameValue = this.nameBox.getValue();
        this.coordinateXValue = this.coordinateXBox.getValue();
        this.coordinateYValue = this.coordinateYBox.getValue();
        this.coordinateZValue = this.coordinateZBox.getValue();
        this.colorValue = this.colorBox.getValue();
        this.distanceValue = this.distanceBox.getValue();
        this.enabledValue = this.enabledBox.selected();
        this.shareableValue = this.shareableBox.selected();
    }

    private EditBox coordinateBox(int x, int width, String value, String label) {
        EditBox box = addRenderableWidget(new EditBox(font, x, 0, width, 20,
                Component.literal(label)));
        box.setMaxLength(10);
        box.setValue(value);
        return box;
    }

    /// 按滚动位置布局内容，视口外的控件不参与渲染和点击。
    private void layoutForm() {
        int maxScroll = Math.max(0, CONTENT_HEIGHT - (height - 52));
        this.formScroll = Math.min(this.formScroll, maxScroll);
        this.formTop = 4 - this.formScroll;
        int contentBottom = height - 52;
        final int left = width / 2 - FORM_WIDTH / 2;
        this.setPosition(this.nameBox, this.formTop + 26, contentBottom);
        this.setPosition(this.coordinateXBox, this.formTop + 60, contentBottom);
        this.setPosition(this.coordinateYBox, this.formTop + 60, contentBottom);
        this.setPosition(this.coordinateZBox, this.formTop + 60, contentBottom);
        this.setPosition(this.colorBox, this.formTop + 94, contentBottom);
        for (int i = 0; i < this.paletteButtons.size(); i++) {
            Button button = this.paletteButtons.get(i);
            button.setX(left + i * 28);
            this.setPosition(button, this.formTop + 118, contentBottom);
        }
        this.setPosition(this.distanceBox, this.formTop + 152, contentBottom);
        this.setPosition(this.enabledBox, this.formTop + 176, contentBottom);
        this.setPosition(this.shareableBox, this.formTop + 176, contentBottom);
    }

    private void setPosition(AbstractWidget widget, int y, int contentBottom) {
        widget.setY(y);
        widget.visible = y >= CONTENT_TOP && y + widget.getHeight() <= contentBottom;
    }

    /// 校验并保存；新建走 add，编辑走 update，以复用现有同步策略。
    private void save() {
        FormValues values = this.readForm();
        if (values == null) {
            this.validateForm();
            return;
        }
        Waypoint base = this.original == null
                ? Waypoint.create(values.name(), values.pos(), ClientWaypoints.dimension(),
                        values.colorArgb())
                : this.original;
        Waypoint result = base.withDetails(values.name(), values.pos(), values.colorArgb(),
                values.visibleDistance(), values.enabled(), values.shareable());
        Minecraft minecraft = Minecraft.getInstance();
        if (this.original == null) {
            ClientWaypoints.add(result);
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(Component.translatable(
                        "aleeve_atlas.message.waypoint_created", result.pos().getX(),
                        result.pos().getY(), result.pos().getZ()));
            }
        } else {
            ClientWaypoints.update(result);
        }
        minecraft.setScreen(this.parent);
    }

    private void validateForm() {
        FormValues values = this.readForm();
        if (this.saveButton != null) {
            this.saveButton.active = values != null;
        }
    }

    /// 读取表单并记录首个校验错误，供按钮禁用与错误文本共用。
    private FormValues readForm() {
        this.errorMessage = null;
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            this.errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.name_required");
            return null;
        }
        int x;
        int y;
        int z;
        int visibleDistance;
        try {
            x = Integer.parseInt(this.coordinateXBox.getValue().trim());
            y = Integer.parseInt(this.coordinateYBox.getValue().trim());
            z = Integer.parseInt(this.coordinateZBox.getValue().trim());
            visibleDistance = Integer.parseInt(this.distanceBox.getValue().trim());
        } catch (NumberFormatException e) {
            this.errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_number");
            return null;
        }
        if (visibleDistance < -1 || visibleDistance > MAX_VISIBLE_DISTANCE) {
            this.errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_distance",
                    MAX_VISIBLE_DISTANCE);
            return null;
        }
        int color;
        try {
            color = parseColor(this.colorBox.getValue());
        } catch (IllegalArgumentException e) {
            this.errorMessage = Component.translatable(
                    "aleeve_atlas.screen.waypoint_edit.invalid_color");
            return null;
        }
        this.previewColor = color;
        return new FormValues(name, new BlockPos(x, y, z), color, visibleDistance,
                this.enabledBox.selected(), this.shareableBox.selected());
    }

    /// 解析 `#RRGGBB`，统一补全不透明 alpha。
    static int parseColor(String raw) {
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 || !value.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid waypoint color");
        }
        return 0xFF000000 | Integer.parseInt(value, 16);
    }

    static String formatColor(int colorArgb) {
        return "#%06X".formatted(colorArgb & 0xFFFFFF);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        final int left = width / 2 - FORM_WIDTH / 2;
        graphics.text(font, title, left, 8, 0xFFFFFFFF, true);
        this.drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.name"),
                left, this.formTop + 16);
        this.drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.coordinates"),
                left, this.formTop + 50);
        this.drawLabel(graphics, Component.translatable("aleeve_atlas.screen.waypoint_edit.color"),
                left, this.formTop + 84);
        if (this.formTop + 96 >= CONTENT_TOP && this.formTop + 112 <= height - 52) {
            graphics.fill(left + 146, this.formTop + 96, left + 164, this.formTop + 112, this.previewColor);
        }
        this.drawLabel(graphics,
                Component.translatable("aleeve_atlas.screen.waypoint_edit.visible_distance"),
                left, this.formTop + 142);
        if (this.errorMessage != null) {
            graphics.centeredText(font, this.errorMessage, width / 2, height - 44, 0xFFFF5555);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // 色板直接绘制几何色块，避免依赖字体中的方块字形或源码的非 ASCII 字符解码。
        for (int index = 0; index < this.paletteButtons.size(); index++) {
            Button button = this.paletteButtons.get(index);
            if (!button.visible) continue;
            int x = button.getX() + 6;
            int y = button.getY() + 4;
            if ((this.previewColor & 0xFFFFFF) == PALETTE[index]) {
                graphics.fill(x - 2, y - 2, x + 14, y + 14, 0xFFFFFFFF);
            }
            graphics.fill(x - 1, y - 1, x + 13, y + 13, 0xFF101010);
            graphics.fill(x, y, x + 12, y + 12, 0xFF000000 | PALETTE[index]);
        }
    }

    private void drawLabel(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        if (y >= CONTENT_TOP && y + 9 <= height - 52) {
            graphics.text(font, text, x, y, 0xFFAAAAAA, true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            int maxScroll = Math.max(0, CONTENT_HEIGHT - (height - 52));
            int newScroll = Math.max(0, Math.min(maxScroll,
                    this.formScroll + (scrollY > 0 ? -12 : 12)));
            if (newScroll != this.formScroll) {
                this.formScroll = newScroll;
                this.layoutForm();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record FormValues(String name, BlockPos pos, int colorArgb, int visibleDistance,
            boolean enabled, boolean shareable) {
    }
}
