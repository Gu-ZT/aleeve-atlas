package dev.dubhe.map.client.map;

import dev.anvilcraft.lib.v2.wheel.api.WheelMenuBuilder;
import dev.anvilcraft.lib.v2.wheel.api.WheelMenuModel;
import dev.anvilcraft.lib.v2.wheel.api.WheelOpenMode;
import dev.anvilcraft.lib.v2.wheel.client.gui.component.WheelFrostedBackground;
import dev.anvilcraft.lib.v2.wheel.client.gui.component.WheelWidget;
import dev.anvilcraft.lib.v2.wheel.client.gui.screen.WheelScreen;
import dev.dubhe.map.client.AtlasKeyMappings;
import dev.dubhe.map.ping.MapPingData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/// 世界地图与世界内共用的四类标点轮盘。
final class PingWheelMenu extends WheelScreen {
    private static final Field WHEEL_WIDGET = field("wheelWidget");
    private static final Field FROSTED_BACKGROUND = field("frostedBackground");

    private final WorldMapScreen parent;
    private boolean closing;

    private PingWheelMenu(WorldMapScreen parent, Vec3 target) {
        super(model(icon -> {
            if (parent == null) {
                WorldPingController.createPing(target, icon);
            } else {
                parent.createWheelPing(icon);
            }
        }), WheelOpenMode.HOLD);
        this.parent = parent;
    }

    static void open(WorldMapScreen parent) {
        Minecraft.getInstance().setScreen(new PingWheelMenu(parent, null));
    }

    static void open(Vec3 target) {
        Minecraft.getInstance().setScreen(new PingWheelMenu(null, target));
    }

    private static WheelMenuModel model(Consumer<MapPingData.PingIcon> action) {
        WheelMenuBuilder builder = WheelMenuBuilder.create().slotsPerPage(4);
        for (MapPingData.PingIcon icon : MapPingData.PingIcon.values()) {
            builder.action(icon.name(), icon.displayName(), (graphics, pose, width, height) -> {
                Component label = icon.displayName();
                var font = Minecraft.getInstance().font;
                graphics.text(font, label, -font.width(label) / 2,
                        -font.lineHeight / 2, 0xFFFFFFFF, true);
            }, context -> action.accept(icon));
        }
        return builder.build();
    }

    @Override
    protected void init() {
        if (this.parent != null
                && (this.parent.width != this.width || this.parent.height != this.height)) {
            this.parent.resize(this.width, this.height);
        }
        super.init();
        this.disableFrostedBackground();
    }

    private void disableFrostedBackground() {
        try {
            if (WHEEL_WIDGET.get(this) instanceof WheelWidget widget) {
                widget.setFrostedBackground(null);
            }
            if (FROSTED_BACKGROUND.get(this) instanceof WheelFrostedBackground background) {
                background.close();
                FROSTED_BACKGROUND.set(this, null);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to disable ping wheel background", e);
        }
    }

    private static Field field(String name) {
        try {
            Field field = WheelScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.disableFrostedBackground();
        if (this.parent != null) {
            this.parent.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (AtlasKeyMappings.WORLD_PING.matchesMouse(event)) {
            if (!this.closing) {
                this.triggerFromHoldRelease();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (AtlasKeyMappings.WORLD_PING.matches(event)) {
            if (!this.closing) {
                this.triggerFromHoldRelease();
            }
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.onClose();
        return true;
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            Minecraft.getInstance().setScreen(this.parent);
        }
    }

    @Override
    public void removed() {
        this.closing = true;
        if (this.parent == null) {
            WorldPingController.finishWheel();
        } else {
            this.parent.finishWheel();
        }
        super.removed();
    }
}
