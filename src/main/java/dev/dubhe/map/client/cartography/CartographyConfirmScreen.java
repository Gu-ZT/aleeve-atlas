package dev.dubhe.map.client.cartography;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.cartography.Cartography;
import dev.dubhe.map.client.map.WorldMapScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

import java.util.UUID;

/// 确认页持有冻结的色板快照，提交的数据与预览完全相同。
public final class CartographyConfirmScreen extends Screen {
    private final WorldMapScreen parent;
    private final ClientCartography.HeldMap held;
    private final int centerX, centerZ;
    private final byte[] colors;
    private final Identifier textureId = AleeveAtlas.id("cartography_preview/" + UUID.randomUUID());
    private boolean registered;
    private boolean locked;
    private boolean submitting;
    private boolean invalidated;
    private Checkbox lockBox;
    private Button confirm;
    private Component error = Component.empty();

    public CartographyConfirmScreen(WorldMapScreen parent, ClientCartography.HeldMap held,
            int centerX, int centerZ, byte[] colors) {
        super(ClientCartography.text("confirm_title"));
        this.parent = parent; this.held = held; this.centerX = centerX; this.centerZ = centerZ;
        this.colors = colors.clone();
    }

    @Override
    protected void init() {
        if (!this.registered) {
            NativeImage image = new NativeImage(128, 128, false);
            for (int z = 0; z < 128; z++) for (int x = 0; x < 128; x++) {
                int color = MapColor.getColorFromPackedId(this.colors[z * 128 + x]);
                image.setPixel(x, z, color == 0 ? 0xFFD8C99B : color);
            }
            var texture = new DynamicTexture(() -> "Atlas cartography preview", image);
            this.minecraft.getTextureManager().register(this.textureId, texture);
            texture.upload();
            this.registered = true;
        }
        this.lockBox = addRenderableWidget(Checkbox.builder(ClientCartography.text("lock"), this.font)
                .pos(this.width / 2 - 100, this.height - 79).selected(this.locked)
                .onValueChange((box, value) -> this.locked = value).build());
        this.confirm = addRenderableWidget(Button.builder(Component.translatable("gui.ok"), b -> this.submit())
                .bounds(this.width / 2 - 104, this.height - 27, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 4, this.height - 27, 100, 20).build());
        this.updateControls();
    }

    private void updateControls() {
        var player = this.minecraft.player;
        boolean glass = player != null && (player.getAbilities().instabuild || Cartography.glassSlot(player) >= 0);
        if (!glass && this.lockBox.selected()) this.lockBox.onPress(null);
        this.lockBox.active = glass && !this.submitting && !this.invalidated;
        this.lockBox.setTooltip(glass ? null : Tooltip.create(ClientCartography.text("glass_missing")));
        this.confirm.active = !this.submitting && !this.invalidated && this.held.valid()
                && this.minecraft.level != null && Cartography.validRegion(this.minecraft.level, this.centerX, this.centerZ);
    }

    private void submit() {
        this.updateControls();
        if (!this.confirm.active) return;
        this.submitting = true;
        if (!ClientCartography.craft(this.held, this.centerX, this.centerZ, this.colors, this.locked, result -> {
            this.submitting = false;
            if (result.status().equals("crafted")) {
                this.parent.finishCartography();
                this.minecraft.gui.setScreen(this.parent);
                ClientCartography.message("crafted");
            } else {
                this.error = ClientCartography.text(result.status());
                if (result.status().equals("changed")) this.invalidated = true;
                this.updateControls();
            }
        })) {
            this.submitting = false;
            this.error = ClientCartography.text("busy");
        }
        this.updateControls();
    }

    @Override
    public void tick() {
        ClientCartography.busy();
        if (!this.submitting && !this.held.valid()) {
            this.invalidated = true;
            this.error = ClientCartography.text("changed");
        }
        this.updateControls();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xF0181818);
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        graphics.centeredText(this.font, ClientCartography.text("bounds", this.centerX - 64, this.centerZ - 64,
                this.centerX + 63, this.centerZ + 63), this.width / 2, 23, 0xFFCCCCCC);
        int size = Math.max(16, Math.min(256, Math.min(this.width - 24, this.height - 125)));
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.textureId, (this.width - size) / 2, 37,
                0, 0, size, size, 128, 128, 128, 128);
        boolean creative = this.minecraft.player != null && this.minecraft.player.getAbilities().instabuild;
        graphics.centeredText(this.font, ClientCartography.text(creative ? "cost_creative" : this.locked ? "cost_locked" : "cost"),
                this.width / 2, this.height - 55, 0xFFCCCCCC);
        graphics.centeredText(this.font, this.submitting ? ClientCartography.text("submitting") : this.error,
                this.width / 2, this.height - 40, 0xFFFFAA55);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { this.minecraft.gui.setScreen(this.parent); }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        if (this.registered) { this.minecraft.getTextureManager().release(this.textureId); this.registered = false; }
    }
}
