package dev.dubhe.map.client.map;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.api.ping.MapPingInput;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import dev.dubhe.map.cartography.Cartography;
import dev.dubhe.map.cartography.MapSnapshot;
import dev.dubhe.map.client.cartography.ClientCartography;
import dev.dubhe.map.client.cartography.CartographyConfirmScreen;
import dev.dubhe.map.client.structure.ClientStructures;
import dev.dubhe.map.client.structure.ClientStructureSearch;
import dev.dubhe.map.client.structure.StructurePanel;
import dev.dubhe.map.client.structure.StructureRenderer;
import dev.dubhe.map.structure.StructureRecord;
import net.minecraft.world.item.Items;
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.api.territory.TerritoryGroup;
import dev.dubhe.map.client.AtlasKeyMappings;
import dev.dubhe.map.client.radar.RadarRenderer;
import dev.dubhe.map.client.waypoint.ClientWaypoints;
import dev.dubhe.map.client.waypoint.WaypointActions;
import dev.dubhe.map.client.waypoint.WaypointEditScreen;
import dev.dubhe.map.client.waypoint.WaypointListScreen;
import dev.dubhe.map.client.waypoint.WaypointRenderer;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.ConfigBounds;
import dev.dubhe.map.config.WaypointSendPolicy;
import dev.dubhe.map.map.ChunkMapData;
import dev.dubhe.map.map.MapDimensionCache;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import dev.dubhe.map.ping.PingRenderer;
import dev.dubhe.map.territory.TerritoryRegistry;
import dev.dubhe.map.territory.TerritoryRuns;
import dev.dubhe.map.waypoint.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// 全屏世界地图。
///
/// 支持鼠标拖拽平移与滚轮无级缩放；未探索区块显示为黑色网格背景。
/// 双击创建路径点、中键发起标点、右键打开路径点菜单。
public final class WorldMapScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/worldmap");
    /// 每区块像素数的缩放范围。
    private static final float MIN_PIXELS_PER_CHUNK = 0.05F;
    private static final float MAX_PIXELS_PER_CHUNK = 12.0F;
    private static final int BACKGROUND_COLOR = 0xF0101010;
    /// 低于该区块像素尺寸时隐藏领地名称标签。
    private static final float MIN_PIXELS_FOR_LABEL = 3.0F;
    /// 虚线边框的节距（像素）。
    private static final int BORDER_DASH_PERIOD = 4;
    /// 虚线边框实线段长度（像素）。
    private static final int BORDER_DASH_LENGTH = 2;
    private static final int MIDDLE_HOLD_TICKS = 8;
    private static final int CONTEXT_MENU_WIDTH = 160;
    private static final int CONTEXT_MENU_ROW_HEIGHT = 20;
    private static final int OPEN_ANIMATION_TICKS = 10;

    /// 当前打开的世界地图界面；未打开时为 null。
    private static WorldMapScreen active;

    private final MapCanvas canvas;
    private boolean caveView;
    private MapDimensionCache cache;
    /// 视野中心（世界方块坐标）。
    private double centerX;
    private double centerZ;
    private float pixelsPerChunk = 4.0F;
    private float targetPixelsPerChunk = 4.0F;
    private float previousPixelsPerChunk = 4.0F;
    private double previousCenterX;
    private double previousCenterZ;
    private float renderedPixelsPerChunk = 4.0F;
    private double renderedCenterX;
    private double renderedCenterZ;
    private boolean renderingFrame;
    private boolean zoomAnchored;
    private double zoomAnchorWorldX;
    private double zoomAnchorWorldZ;
    private double zoomAnchorScreenX;
    private double zoomAnchorScreenY;
    private int openAnimationTicks;
    private float animationScale = 1.0F;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;
    private boolean middleHeld;
    private int middleHoldTicks;
    private double middleWorldX;
    private double middleWorldZ;
    private boolean wheelOpened;
    private Double wheelCursorX;
    private Double wheelCursorY;
    private ContextMenu contextMenu;
    private Button importButton;
    private Button craftButton;
    private ClientCartography.HeldMap craftingHeld;
    private MapSnapshot importedHighlight;
    private long importedHighlightUntil;
    private final StructurePanel structures = new StructurePanel(this::focusStructure,
            () -> Minecraft.getInstance().screen == this);
    private Button structureButton;
    private @org.jspecify.annotations.Nullable StructureRecord hoveredStructure;

    public WorldMapScreen(MapCanvas canvas, MapDimensionCache cache, double centerX,
            double centerZ) {
        super(Component.translatable("aleeve_atlas.screen.world_map"));
        this.canvas = canvas;
        this.cache = cache;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.previousCenterX = centerX;
        this.previousCenterZ = centerZ;
        this.renderedCenterX = centerX;
        this.renderedCenterZ = centerZ;
    }

    @Override
    protected void init() {
        if (!this.canvas.ready()) {
            this.canvas.register();
        }
        this.refreshCanvas();
        this.addTeamButtons();
        this.addCartographyButtons();
        int structureWidth = Math.max(20, Math.min(72, (this.width - 42) / 6));
        this.structureButton = addRenderableWidget(Button.builder(ClientStructureSearch.text("title"), b -> {
            this.contextMenu = null;
            this.dragging = false;
            this.structures.toggle();
        }).bounds(6, 6, structureWidth, 20).build());
        this.structures.init(this.width, this.height, this::addRenderableWidget);
        active = this;
        this.verifyScrollIfRequested();
    }

    /// 自检：按环境变量指定的步数连续平移，验证像素搬移路径。
    ///
    /// 仅用于自动化验收，正常游玩不触发。
    private void verifyScrollIfRequested() {
        String raw = System.getenv("ALEEVE_ATLAS_SCROLL_SELFTEST");
        if (raw == null || this.cache == null) {
            return;
        }
        int steps;
        try {
            steps = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid ALEEVE_ATLAS_SCROLL_SELFTEST value: {}", raw, e);
            return;
        }
        // 以玩家位置为基准，按小步长连续平移，模拟真实拖拽的位移节奏。
        // 步长 1 区块用于验证阈值缓存是否按预期减少重建次数。
        double startX = this.centerX;
        double startZ = this.centerZ;
        for (int i = 1; i <= steps; i++) {
            this.centerX = startX + i * ChunkMapData.SIZE;
            this.centerZ = startZ + (i % 2 == 0 ? i : -i) * ChunkMapData.SIZE;
            this.refreshCanvas();
        }
        // 平移结束后回到原点，便于截图核对画面是否与平移前一致。
        this.centerX = startX;
        this.centerZ = startZ;
        this.refreshCanvas();
    }

    @Override
    public void added() {
        super.added();
        active = this;
    }

    @Override
    public void removed() {
        if (active == this) {
            active = null;
        }
        super.removed();
    }

    /// 地图数据发生外部变更时重绘已打开的界面。
    public static void onMapDataChanged() {
        WorldMapScreen screen = active;
        if (screen != null && Minecraft.getInstance().screen == screen) {
            screen.refreshCanvas();
        }
    }

    private void addCartographyButtons() {
        int buttonWidth = Math.max(20, Math.min(72, (this.width - 42) / 6));
        int x = this.width - 6 - 4 * buttonWidth - 3 * 6;
        this.importButton = addRenderableWidget(Button.builder(ClientCartography.text("import"),
                b -> ClientCartography.importMap(this)).bounds(x, 6, buttonWidth, 20).build());
        this.craftButton = addRenderableWidget(Button.builder(ClientCartography.text("craft"), b -> {
            if (this.craftingHeld != null) this.finishCartography();
            else {
                if (this.structures.open()) {
                    this.structures.toggle();
                }
                this.craftingHeld = ClientCartography.held(Items.MAP);
                this.contextMenu = null; this.middleHeld = false; this.wheelOpened = false; this.dragging = false;
            }
        }).bounds(x, 6, buttonWidth, 20).build());
        this.updateCartographyButtons();
    }

    private void updateCartographyButtons() {
        boolean available = ClientCartography.available();
        boolean busy = ClientCartography.busy();
        this.importButton.visible = available && ClientCartography.held(Items.FILLED_MAP) != null;
        this.importButton.active = !busy && this.craftingHeld == null;
        this.craftButton.visible = available && ClientCartography.held(Items.MAP) != null;
        this.importButton.setX(this.craftButton.getX()
                - (this.craftButton.visible ? this.craftButton.getWidth() + 6 : 0));
        this.craftButton.active = !busy;
        this.craftButton.setMessage(ClientCartography.text(this.craftingHeld == null ? "craft" : "cancel_selection"));
        if (this.craftingHeld != null && (!available || !this.craftingHeld.valid())) this.finishCartography();
    }

    public void finishCartography() { this.craftingHeld = null; this.dragging = false; }

    public void onMapImported(MapSnapshot snapshot) {
        if (this.minecraft.level == null || !snapshot.dimension().equals(this.minecraft.level.dimension().identifier().toString())) {
            ClientCartography.message("other_dimension", snapshot.dimension());
            return;
        }
        this.centerX = snapshot.centerX(); this.centerZ = snapshot.centerZ();
        this.previousCenterX = this.centerX; this.previousCenterZ = this.centerZ;
        this.zoomAnchored = false;
        float fit = Math.max(MIN_PIXELS_PER_CHUNK, Math.min(MAX_PIXELS_PER_CHUNK,
                (float) Math.min(this.width - 40, this.height - 100) / snapshot.size()));
        this.pixelsPerChunk = fit; this.previousPixelsPerChunk = fit; this.targetPixelsPerChunk = fit;
        this.importedHighlight = snapshot;
        this.importedHighlightUntil = System.currentTimeMillis() + 30_000;
        this.refreshCanvas();
        ClientCartography.message("imported");
    }

    private void drawCartography(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.importedHighlight != null && System.currentTimeMillis() < this.importedHighlightUntil) {
            var map = this.importedHighlight;
            this.drawMapRegion(graphics, map.centerX(), map.centerZ(), map.size(), 0, 0xFFFFCC55);
            float x = (float) (this.width / 2.0 + (map.centerX() - this.centerX) * this.pixelsPerChunk);
            float y = (float) (this.height / 2.0 + (map.centerZ() - this.centerZ) * this.pixelsPerChunk);
            // 两条矩形均以真实投影中心对称，避免奇数臂长配偶数线宽产生半像素偏移。
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.fill(-5, -1, 5, 1, 0xFFFFCC55);
            graphics.fill(-1, -5, 1, 5, 0xFFFFCC55);
            graphics.pose().popMatrix();
        }
        if (this.craftingHeld != null && mouseY > 30) {
            int x = Cartography.center(this.screenToWorldX(mouseX));
            int z = Cartography.center(this.screenToWorldZ(mouseY));
            boolean valid = this.minecraft.level != null && Cartography.validRegion(this.minecraft.level, x, z);
            this.drawMapRegion(graphics, x, z, 128, valid ? 0x70808080 : 0x70AA3333, valid ? 0xFFCCCCCC : 0xFFFF5555);
        }
    }

    private void drawMapRegion(GuiGraphicsExtractor graphics, int x, int z, int size, int fill, int border) {
        int left = (int) Math.floor(this.width / 2.0 + (x - size / 2.0 - this.centerX) * this.pixelsPerChunk);
        int top = (int) Math.floor(this.height / 2.0 + (z - size / 2.0 - this.centerZ) * this.pixelsPerChunk);
        int right = (int) Math.ceil(this.width / 2.0 + (x + size / 2.0 - this.centerX) * this.pixelsPerChunk);
        int bottom = (int) Math.ceil(this.height / 2.0 + (z + size / 2.0 - this.centerZ) * this.pixelsPerChunk);
        graphics.enableScissor(0, 0, this.width, this.height);
        if (fill != 0) graphics.fill(left, top, right, bottom, fill);
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, bottom - 1, right, bottom, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top, right, bottom, border);
        graphics.disableScissor();
    }

    static void clearActive() {
        active = null;
    }

    /// 队伍相关按钮：独立模式下置灰并给出说明。
    private void addTeamButtons() {
        boolean synchronizedMode = AtlasSession.synchronizedMode();
        Component label = Component.translatable("aleeve_atlas.screen.world_map.share_team");
        int buttonWidth = Math.max(20, Math.min(72, (width - 42) / 6));
        int buttonHeight = 20;
        int x = width - buttonWidth - 6;
        int y = 6;
        Button share = Button.builder(label, button -> this.shareVisibleWaypoints())
                .bounds(x, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(synchronizedMode
                        ? Component.translatable("aleeve_atlas.screen.world_map.share_team.tip")
                        : Component.translatable(
                                "aleeve_atlas.message.server_unsupported")))
                .build();
        share.active = synchronizedMode;
        addRenderableWidget(share);

        // 路径点管理与客户端配置入口，与分享按钮保持 6px 间隔。
        addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.waypoint_list"),
                        button -> Minecraft.getInstance().setScreen(new WaypointListScreen(this)))
                .bounds(x - buttonWidth - 6, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("aleeve_atlas.screen.world_map.client_config"),
                        button -> this.openClientConfig())
                .bounds(x - (buttonWidth + 6) * 2, y, buttonWidth, 20).build());
    }

    /// 打开模组客户端配置，并保留当前世界地图作为返回页。
    private void openClientConfig() {
        var container = ModList.get().getModContainerById(AleeveAtlas.MOD_ID).orElseThrow();
        var factory = container.getCustomExtension(IConfigScreenFactory.class).orElseThrow();
        Minecraft.getInstance().setScreen(factory.createScreen(container, this));
    }

    /// 分享当前视野内的全部路径点。
    private void shareVisibleWaypoints() {
        dev.dubhe.map.client.structure.ClientStructures.shareAll();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int count = 0;
        for (Waypoint waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double x = width / 2.0 + (waypoint.pos().getX() + 0.5 - this.centerX)
                    * this.pixelsPerChunk;
            double y = height / 2.0 + (waypoint.pos().getZ() + 0.5 - this.centerZ)
                    * this.pixelsPerChunk;
            if (waypoint.shareable() && x >= 0.0 && x <= width && y >= 0.0 && y <= height) {
                ClientWaypoints.share(waypoint);
                count++;
            }
        }
        Component message = count == 0
                ? Component.translatable("aleeve_atlas.message.waypoint_none")
                : Component.translatable("aleeve_atlas.message.waypoints_shared_team", count);
        minecraft.player.sendOverlayMessage(message);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int viewWidth = width;
        int viewHeight = height;
        float animationProgress = Mth.clamp((this.openAnimationTicks + partialTick)
                / OPEN_ANIMATION_TICKS, 0.0F, 1.0F);
        float eased = 1.0F - (float) Math.pow(1.0F - animationProgress, 3.0);
        float animationScale = 0.8F + 0.2F * eased;
        this.animationScale = animationScale;
        int logicalMouseX = (int) Math.round(this.logicalMouseX(mouseX));
        int logicalMouseY = (int) Math.round(this.logicalMouseY(mouseY));

        float actualPixelsPerChunk = this.pixelsPerChunk;
        double actualCenterX = this.centerX;
        double actualCenterZ = this.centerZ;
        float framePixelsPerChunk = this.pixelsPerChunk;
        double frameCenterX = this.centerX;
        double frameCenterZ = this.centerZ;
        if (this.previousPixelsPerChunk != this.pixelsPerChunk) {
            framePixelsPerChunk = Mth.lerp(partialTick,
                    this.previousPixelsPerChunk, this.pixelsPerChunk);
            if (this.zoomAnchored) {
                frameCenterX = this.zoomAnchorWorldX
                        - (this.zoomAnchorScreenX - viewWidth / 2.0) / framePixelsPerChunk;
                frameCenterZ = this.zoomAnchorWorldZ
                        - (this.zoomAnchorScreenY - viewHeight / 2.0) / framePixelsPerChunk;
            } else {
                frameCenterX = lerp(this.previousCenterX, actualCenterX, partialTick);
                frameCenterZ = lerp(this.previousCenterZ, actualCenterZ, partialTick);
            }
        }
        this.renderingFrame = true;
        this.pixelsPerChunk = framePixelsPerChunk;
        this.centerX = frameCenterX;
        this.centerZ = frameCenterZ;
        this.renderedPixelsPerChunk = framePixelsPerChunk;
        this.renderedCenterX = frameCenterX;
        this.renderedCenterZ = frameCenterZ;

        // 背景、按钮、文字和方向标保持屏幕尺寸，仅地图内容使用开场缩放。
        graphics.fill(0, 0, viewWidth, viewHeight, BACKGROUND_COLOR);
        if (animationProgress < 1.0F) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(viewWidth / 2.0F, viewHeight / 2.0F);
            graphics.pose().scale(animationScale, animationScale);
            graphics.pose().translate(-viewWidth / 2.0F, -viewHeight / 2.0F);
        }

        if (!this.caveView) dev.dubhe.map.client.cartography.ImportedMapRenderer.draw(graphics,
                this.centerX, this.centerZ, this.pixelsPerChunk, viewWidth, viewHeight);
        if (this.canvas.ready()) {
            int canvasOriginPixelX = this.canvas.originChunkX() * ChunkMapData.SIZE;
            int canvasOriginPixelZ = this.canvas.originChunkZ() * ChunkMapData.SIZE;
            // 视口左上角对应的世界坐标：屏幕像素 / 每区块像素 = 世界方块偏移。
            double topLeftWorldX = this.centerX - (double) viewWidth / 2.0 / this.pixelsPerChunk;
            double topLeftWorldZ = this.centerZ - (double) viewHeight / 2.0 / this.pixelsPerChunk;

            // 画布在世界坐标中的覆盖范围，与视口求交，避免采样越界导致画面拉伸。
            double canvasLeft = canvasOriginPixelX;
            double canvasTop = canvasOriginPixelZ;
            int stride = this.canvas.blocksPerPixel();
            double canvasRight = canvasOriginPixelX + this.canvas.worldSize();
            double canvasBottom = canvasOriginPixelZ + this.canvas.worldSize();
            double visibleLeft = Math.max(topLeftWorldX, canvasLeft);
            double visibleTop = Math.max(topLeftWorldZ, canvasTop);
            double visibleRight = Math.min(topLeftWorldX + (double) viewWidth / this.pixelsPerChunk,
                    canvasRight);
            double visibleBottom = Math.min(topLeftWorldZ + (double) viewHeight / this.pixelsPerChunk,
                    canvasBottom);
            if (visibleRight > visibleLeft && visibleBottom > visibleTop) {
                int u = (int) Math.floor((visibleLeft - canvasLeft) / stride);
                int v = (int) Math.floor((visibleTop - canvasTop) / stride);
                int rightPixel = (int) Math.ceil((visibleRight - canvasLeft) / stride);
                int bottomPixel = (int) Math.ceil((visibleBottom - canvasTop) / stride);
                int pixelWidth = rightPixel - u;
                int pixelHeight = bottomPixel - v;
                double screenX = viewWidth / 2.0
                        + (canvasLeft + u * stride - this.centerX) * this.pixelsPerChunk;
                double screenY = viewHeight / 2.0
                        + (canvasTop + v * stride - this.centerZ) * this.pixelsPerChunk;
                graphics.pose().pushMatrix();
                graphics.pose().translate((float) screenX, (float) screenY);
                graphics.pose().scale(this.pixelsPerChunk * stride, this.pixelsPerChunk * stride);
                graphics.blit(RenderPipelines.GUI_TEXTURED, this.canvas.textureId(), 0, 0,
                        u, v, pixelWidth, pixelHeight, pixelWidth, pixelHeight,
                        MapCanvas.PIXELS, MapCanvas.PIXELS);
                graphics.pose().popMatrix();
            }
        }

        this.drawTerritories(graphics, logicalMouseX, logicalMouseY);
        ChunkOverlayRenderer.renderOnWorldMap(graphics, AtlasClientConfigs.CONFIG,
                this.centerX, this.centerZ, this.pixelsPerChunk, width, height);
        RadarRenderer.renderOnWorldMap(graphics, AtlasClientConfigs.CONFIG,
                Minecraft.getInstance(), this.centerX, this.centerZ, this.pixelsPerChunk, width, height);
        this.drawStructures(graphics, logicalMouseX, logicalMouseY);
        this.drawWaypoints(graphics);
        this.drawPings(graphics);
        this.drawPlayerMarker(graphics);
        this.drawCartography(graphics, logicalMouseX, logicalMouseY);
        if (animationProgress < 1.0F) {
            graphics.pose().popMatrix();
        }
        this.drawTerritoryTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
        this.drawWaypointLabels(graphics);
        CompassRenderer.renderOnWorldMap(graphics);
        this.drawTerritoryLabels(graphics);
        this.drawLocationInfo(graphics, logicalMouseX, logicalMouseY);
        this.drawContextMenu(graphics, mouseX, mouseY);
        this.structures.extractRenderState(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredStructure != null && this.contextMenu == null && !this.structures.contains(mouseX, mouseY) && mouseY > 30) {
            graphics.setComponentTooltipForNextFrame(this.font, StructureRenderer.description(this.hoveredStructure), mouseX, mouseY);
        }
        if (this.craftingHeld != null) graphics.centeredText(this.font, ClientCartography.text("selection_help"),
                this.width / 2, this.height - 12, 0xFFFFFFFF);
        this.pixelsPerChunk = actualPixelsPerChunk;
        this.centerX = actualCenterX;
        this.centerZ = actualCenterZ;
        this.renderingFrame = false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updateCartographyButtons();
        this.structureButton.active = this.craftingHeld == null;
        this.structures.update();
        this.openAnimationTicks = Math.min(OPEN_ANIMATION_TICKS, this.openAnimationTicks + 1);
        float previous = this.pixelsPerChunk;
        this.previousPixelsPerChunk = previous;
        this.previousCenterX = this.centerX;
        this.previousCenterZ = this.centerZ;
        this.pixelsPerChunk += (this.targetPixelsPerChunk - this.pixelsPerChunk)
                * (AtlasClientConfigs.CONFIG.worldMap.smoothZoom ? 0.25F : 1.0F);
        if (Math.abs(this.targetPixelsPerChunk - this.pixelsPerChunk) < 0.005F) {
            this.pixelsPerChunk = this.targetPixelsPerChunk;
        }
        if (this.zoomAnchored) {
            this.centerX = this.zoomAnchorWorldX
                    - (this.zoomAnchorScreenX - width / 2.0) / this.pixelsPerChunk;
            this.centerZ = this.zoomAnchorWorldZ
                    - (this.zoomAnchorScreenY - height / 2.0) / this.pixelsPerChunk;
            this.zoomAnchored = this.pixelsPerChunk != this.targetPixelsPerChunk;
        }
        if (previous != this.pixelsPerChunk) {
            this.refreshCanvas();
        }
        this.canvas.pollOverview();
        if (this.middleHeld && !this.wheelOpened
                && ++this.middleHoldTicks >= MIDDLE_HOLD_TICKS) {
            this.contextMenu = null;
            this.wheelOpened = true;
            this.rememberCursorAndCenter();
            MapPingInput input = MinimapPingAPI.mapInput();
            if (input == null) {
                PingWheelMenu.open(this);
            } else {
                int y = this.surfaceYAt(this.middleWorldX, this.middleWorldZ);
                input.openWheel(this, new Vec3(this.middleWorldX, y, this.middleWorldZ), this::finishWheel);
            }
        }
    }

    /// 领地覆层。
    ///
    /// 缩小到区块像素小于标签宽度时自动隐藏名称；悬停任一区块高亮其边框并展示信息卡片。
    private void drawTerritories(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        TerritoryRegistry registry = AtlasTerritory.clientRegistry();
        List<TerritoryGroup> groups = registry.orderedForDimension(dimension);
        if (groups.isEmpty()) {
            return;
        }
        ChunkPos hovered = this.chunkAtScreen(mouseX, mouseY);
        List<TerritoryGroup> hoveredGroups = hovered == null ? List.of()
                : registry.hitAt(dimension, hovered);

        for (TerritoryGroup group : groups) {
            int fill = withOpacity(group.themeColorRgb(), group.fillOpacity());
            boolean highlighted = hoveredGroups.contains(group);
            int border = highlighted ? 0xFFFFFFFF : group.borderStyle().colorArgb();
            // 填充：按横向连续路段批量绘制，减少绘制调用。
            for (TerritoryRuns.Run run : TerritoryRuns.of(group.chunks())) {
                // 按整段两端计算矩形：放大后单个区块可能超出视口，
                // 若按首区块可见性判断会整段跳过，导致放大地图时领地消失。
                int[] span = this.runScreenRect(run);
                int left = Math.max(0, span[0]);
                int top = Math.max(0, span[1]);
                int right = Math.min(this.width, span[0] + span[2]);
                int bottom = Math.min(this.height, span[1] + span[3]);
                if (right > left && bottom > top) {
                    graphics.fill(left, top, right, bottom, fill);
                }
            }
            // 边框：仅在与非领地交界处逐区块描边。
            for (ChunkPos chunk : group.chunks()) {
                int[] rect = this.chunkScreenRect(chunk);
                if (rect == null) {
                    continue;
                }
                this.drawChunkOutline(graphics, group, chunk, rect, border);
            }
        }

    }

    private void drawTerritoryTooltip(GuiGraphicsExtractor graphics, int mapMouseX, int mapMouseY,
            int screenMouseX, int screenMouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        ChunkPos hovered = this.chunkAtScreen(mapMouseX, mapMouseY);
        if (hovered == null) {
            return;
        }
        List<TerritoryGroup> hoveredGroups = AtlasTerritory.clientRegistry()
                .hitAt(dimension, hovered);
        if (hoveredGroups.isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (TerritoryGroup group : hoveredGroups) {
            lines.add(group.displayName().copy().withStyle(ChatFormatting.AQUA));
            lines.addAll(group.tooltip());
        }
        this.drawTooltipLines(graphics, minecraft, lines, screenMouseX, screenMouseY);
    }

    /// 绘制简易信息卡片（避免依赖 1.21.11 的 tooltip 组件装配）。
    private void drawTooltipLines(GuiGraphicsExtractor graphics, Minecraft minecraft,
            List<Component> lines, int mouseX, int mouseY) {
        int lineHeight = 10;
        int padding = 3;
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, minecraft.font.width(line));
        }
        int boxWidth = maxWidth + padding * 2;
        int boxHeight = lines.size() * lineHeight + padding * 2;
        int x = Math.min(mouseX + 8, Math.max(0, width - boxWidth - 4));
        int y = Math.min(mouseY + 8, Math.max(0, height - boxHeight - 4));
        graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xF0100010);
        graphics.fill(x, y, x + boxWidth, y + 1, 0xFF5000FF);
        graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, 0xFF5000FF);
        int textY = y + padding;
        for (Component line : lines) {
            graphics.text(minecraft.font, line, x + padding, textY, 0xFFFFFFFF, true);
            textY += lineHeight;
        }
    }

    /// 领地名称标签。在组件渲染之后调用，确保标签位于最上层。
    ///
    /// 逐个连通分量比较其屏幕像素面积与名称文本宽度：面积不足以容纳文本时隐藏标签，
    /// 仅保留色块，避免大量飞地标签相互重叠（AC-10）。
    private void drawTerritoryLabels(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || this.pixelsPerChunk < MIN_PIXELS_FOR_LABEL) {
            return;
        }
        TerritoryRegistry registry = AtlasTerritory.clientRegistry();
        var groups = registry.orderedForDimension(minecraft.level.dimension());
        if (groups.isEmpty()) {
            return;
        }
        for (TerritoryGroup group : groups) {
            TerritoryRegistry.CachedTerritory cached = registry.cached(group.territoryId());
            if (cached == null) {
                continue;
            }
            int textWidth = minecraft.font.width(group.displayName());
            for (TerritoryRegistry.TerritoryComponent component : cached.components()) {
                if (component.anchor() == null) {
                    continue;
                }
                // 分量的屏幕像素面积不足以容纳文本时隐藏标签。
                // 面积单位为平方像素，右侧以文本宽度的 8 倍作为阈值，避免细长分量也显示标签。
                if (component.chunks().size() * this.pixelsPerChunk * this.pixelsPerChunk
                        < textWidth * 8.0F) {
                    continue;
                }
                // 锚点可能偏移出视口：此时退化为该分量内任一可见区块，保证标签仍可见。
                int[] rect = this.chunkScreenRect(component.anchor());
                if (rect == null) {
                    rect = this.firstVisibleChunkRect(component);
                }
                if (rect == null) {
                    continue;
                }
                // 名称标签带深色背景框，避免在浅色地形上难以辨认。
                double labelCenterX = this.mapAnimationX(rect[0] + rect[2] / 2.0);
                double labelCenterY = this.mapAnimationY(rect[1] + rect[3] / 2.0);
                int tx = (int) Math.round(labelCenterX) - textWidth / 2;
                int ty = (int) Math.round(labelCenterY) - 4;
                graphics.fill(tx - 2, ty - 2, tx + textWidth + 2, ty + 10, 0xB0000000);
                graphics.text(minecraft.font, group.displayName(), tx, ty, 0xFFFFFFFF,
                        false);
            }
        }
    }

    /// 分量内第一个可见区块的屏幕矩形；全部不可见时返回 null。
    private int[] firstVisibleChunkRect(TerritoryRegistry.TerritoryComponent component) {
        for (ChunkPos chunk : component.chunks()) {
            int[] rect = this.chunkScreenRect(chunk);
            if (rect != null) {
                return rect;
            }
        }
        return null;
    }

    /// 区块在屏幕上的矩形：`[x, y, width, height]`；完全不可见时返回 null。
    /// 连续路段的屏幕矩形（不裁剪）：用于把整段填充裁到视口内。
    private int[] runScreenRect(TerritoryRuns.Run run) {
        double leftWorld = (run.startX() << 4);
        double rightWorld = ((run.endX() + 1) << 4);
        double topWorld = (run.z() << 4);
        double bottomWorld = ((run.z() + 1) << 4);
        int x = (int) Math.round(width / 2.0 + (leftWorld - this.centerX) * this.pixelsPerChunk);
        int y = (int) Math.round(height / 2.0 + (topWorld - this.centerZ) * this.pixelsPerChunk);
        int x2 = (int) Math.round(width / 2.0 + (rightWorld - this.centerX) * this.pixelsPerChunk);
        int y2 = (int) Math.round(height / 2.0 + (bottomWorld - this.centerZ) * this.pixelsPerChunk);
        return new int[] { x, y, x2 - x, y2 - y };
    }

    private int[] chunkScreenRect(ChunkPos chunk) {
        double centerChunkX = (chunk.x() << 4) + 8.0;
        double centerChunkZ = (chunk.z() << 4) + 8.0;
        double screenX = width / 2.0 + (centerChunkX - this.centerX) * this.pixelsPerChunk;
        double screenY = height / 2.0 + (centerChunkZ - this.centerZ) * this.pixelsPerChunk;
        int size = Math.max(1, Math.round(ChunkMapData.SIZE * this.pixelsPerChunk));
        int x = (int) Math.round(screenX) - size / 2;
        int y = (int) Math.round(screenY) - size / 2;
        if (x + size < 0 || x > width || y + size < 0 || y > height) {
            return null;
        }
        return new int[] { x, y, size, size };
    }

    /// 仅在领地与非领地交界处描边。
    private void drawChunkOutline(GuiGraphicsExtractor graphics, TerritoryGroup group, ChunkPos chunk,
            int[] rect, int color) {
        int borderWidth = Math.max(1, Math.round(group.borderStyle().width()));
        boolean dashed = group.borderStyle().dashed();
        Set<ChunkPos> chunks = group.chunks();
        int x = rect[0];
        int y = rect[1];
        int size = rect[2];
        if (!chunks.contains(new ChunkPos(chunk.x(), chunk.z() - 1))) {
            this.drawBorderSegment(graphics, x, y, size, borderWidth, color, dashed, true);
        }
        if (!chunks.contains(new ChunkPos(chunk.x(), chunk.z() + 1))) {
            this.drawBorderSegment(graphics, x, y + size - borderWidth, size, borderWidth, color,
                    dashed, true);
        }
        if (!chunks.contains(new ChunkPos(chunk.x() - 1, chunk.z()))) {
            this.drawBorderSegment(graphics, x, y, borderWidth, size, color, dashed, false);
        }
        if (!chunks.contains(new ChunkPos(chunk.x() + 1, chunk.z()))) {
            this.drawBorderSegment(graphics, x + size - borderWidth, y, borderWidth, size, color,
                    dashed, false);
        }
    }

    /// 绘制一段领地边框；虚线样式下按固定节距间隔填充。
    private void drawBorderSegment(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
            int color, boolean dashed, boolean horizontal) {
        if (!dashed) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        int length = horizontal ? width : height;
        for (int offset = 0; offset < length; offset += BORDER_DASH_PERIOD) {
            int dashLength = Math.min(BORDER_DASH_LENGTH, length - offset);
            if (horizontal) {
                graphics.fill(x + offset, y, x + offset + dashLength, y + height, color);
            } else {
                graphics.fill(x, y + offset, x + width, y + offset + dashLength, color);
            }
        }
    }

    /// 鼠标位置对应的区块；在世界地图外返回 null。
    private ChunkPos chunkAtScreen(double mouseX, double mouseY) {
        if (mouseX < 0 || mouseX > width || mouseY < 0 || mouseY > height) {
            return null;
        }
        double worldX = this.screenToWorldX(mouseX);
        double worldZ = this.screenToWorldZ(mouseY);
        return new ChunkPos((int) Math.floor(worldX) >> 4, (int) Math.floor(worldZ) >> 4);
    }

    /// 主题色 + 填充透明度。
    private static int withOpacity(int rgb, float opacity) {
        int alpha = Math.round(Mth.clamp(opacity, 0.0F, 1.0F) * 255.0F);
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    /// 世界地图上的路径点标记。
    private void drawWaypoints(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (var waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double screenX = width / 2.0
                    + (waypoint.pos().getX() + 0.5 - this.centerX) * this.pixelsPerChunk;
            double screenY = height / 2.0
                    + (waypoint.pos().getZ() + 0.5 - this.centerZ) * this.pixelsPerChunk;
            if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
                continue;
            }
            WaypointRenderer.renderOnWorldMap(graphics, screenX, screenY, waypoint.colorArgb(),
                    waypoint.icon());
        }
    }

    private void drawStructures(GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        this.hoveredStructure = null;
        var store = ClientStructures.current();
        if (store == null) {
            return;
        }
        double closest = 49;
        for (StructureRecord record : store.all()) {
            double x = this.width / 2d + (record.position().getX() + 0.5 - this.centerX) * this.pixelsPerChunk;
            double y = this.height / 2d + (record.position().getZ() + 0.5 - this.centerZ) * this.pixelsPerChunk;
            if (x < -12 || y < -12 || x > this.width + 12 || y > this.height + 12) {
                continue;
            }
            StructureRenderer.icon(graphics, record.kind(), x, y, this.structures.selected(record));
            double distance = (x - mouseX) * (x - mouseX) + (y - mouseY) * (y - mouseY);
            if (this.craftingHeld == null && distance < closest) {
                closest = distance;
                this.hoveredStructure = record;
            }
        }
    }

    private void focusStructure(StructureRecord record) {
        this.centerX = record.position().getX() + 0.5 - this.structures.width() / (2d * this.pixelsPerChunk);
        this.centerZ = record.position().getZ() + 0.5;
        this.previousCenterX = this.centerX;
        this.previousCenterZ = this.centerZ;
        this.zoomAnchored = false;
        this.contextMenu = null;
        this.middleHeld = false;
        this.dragging = false;
        this.refreshCanvas();
    }

    private @org.jspecify.annotations.Nullable StructureRecord findStructureAt(double mouseX, double mouseY) {
        var store = ClientStructures.current();
        if (store == null) {
            return null;
        }
        StructureRecord nearest = null;
        double distance = 49;
        for (StructureRecord record : store.all()) {
            double x = this.width / 2d + (record.position().getX() + 0.5 - this.centerX) * this.pixelsPerChunk;
            double y = this.height / 2d + (record.position().getZ() + 0.5 - this.centerZ) * this.pixelsPerChunk;
            double squared = (x - mouseX) * (x - mouseX) + (y - mouseY) * (y - mouseY);
            if (squared < distance) {
                distance = squared;
                nearest = record;
            }
        }
        return nearest;
    }

    private void drawWaypointLabels(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (var waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double logicalX = width / 2.0
                    + (waypoint.pos().getX() + 0.5 - this.centerX) * this.pixelsPerChunk;
            double logicalY = height / 2.0
                    + (waypoint.pos().getZ() + 0.5 - this.centerZ) * this.pixelsPerChunk;
            double screenX = this.mapAnimationX(logicalX);
            double screenY = this.mapAnimationY(logicalY);
            if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
                continue;
            }
            Component name = waypoint.displayName();
            int textWidth = minecraft.font.width(name);
            int labelX = (int) Math.round(screenX) - textWidth / 2;
            int labelY = (int) Math.round(screenY) + 7;
            if (labelY + 10 <= height) {
                graphics.fill(labelX - 2, labelY - 1, labelX + textWidth + 2, labelY + 10,
                        0x80000000);
                graphics.text(minecraft.font, name, labelX, labelY, 0xFFFFFFFF, true);
            }
        }
    }

    /// 世界地图上的临时标点波纹。
    private void drawPings(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        String dimension = MapPingData.dimensionId(minecraft.level.dimension());
        for (MapPingData ping : PingRenderer.activePings()) {
            if (!ping.dimensionId().equals(dimension)) {
                continue;
            }
            float opacity = PingRenderer.opacityByPolicy(minecraft, ping);
            if (opacity <= 0.0F) {
                continue;
            }
            double screenX = width / 2.0 + (ping.position().x - this.centerX) * this.pixelsPerChunk;
            double screenY = height / 2.0 + (ping.position().z - this.centerZ) * this.pixelsPerChunk;
            if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
                continue;
            }
            PingRenderer.renderOnWorldMap(graphics, screenX, screenY, ping, opacity);
        }
    }

    /// 屏幕坐标 → 世界坐标（X 轴）。
    private double screenToWorldX(double screenX) {
        return this.viewCenterX() + (screenX - width / 2.0) / this.viewPixelsPerChunk();
    }

    /// 屏幕坐标 → 世界坐标（Z 轴）。
    private double screenToWorldZ(double screenY) {
        return this.viewCenterZ() + (screenY - height / 2.0) / this.viewPixelsPerChunk();
    }

    /// 背景由 {@link #render} 自行填充，避免父类每帧二次模糊（1.21.11 限制）。
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    /// 玩家指示器固定使用界面尺寸，避免地形缩放改变其大小。
    private void drawPlayerMarker(GuiGraphicsExtractor graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double screenX = width / 2.0 + (minecraft.player.getX() - this.centerX) * this.pixelsPerChunk;
        double screenY = height / 2.0 + (minecraft.player.getZ() - this.centerZ) * this.pixelsPerChunk;
        if (screenX < 0 || screenX > width || screenY < 0 || screenY > height) {
            return;
        }
        PlayerIndicatorRenderer.draw(graphics, AtlasClientConfigs.CONFIG.worldMap.playerIndicator,
                screenX, screenY, minecraft.player.getYRot(), 0f, 1f);
    }

    private void drawContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.contextMenu == null) {
            return;
        }
        int x = this.contextMenu.screenX();
        int y = this.contextMenu.screenY();
        int menuHeight = this.contextMenuHeight();
        int menuWidth = this.contextMenu.width();
        graphics.fill(x, y, x + menuWidth, y + menuHeight, 0xF0181818);
        graphics.fill(x, y, x + menuWidth, y + 1, 0xFFAAAAAA);
        graphics.fill(x, y + menuHeight - 1, x + menuWidth,
                y + menuHeight, 0xFFAAAAAA);
        graphics.fill(x, y, x + 1, y + menuHeight, 0xFFAAAAAA);
        graphics.fill(x + menuWidth - 1, y, x + menuWidth,
                y + menuHeight, 0xFFAAAAAA);
        for (int index = 0; index < this.contextMenu.actions().size(); index++) {
            Component label = this.contextMenu.actions().get(index).label();
            graphics.text(this.font, this.font.plainSubstrByWidth(label.getString(), menuWidth - 16),
                    x + 8, y + 10 + index * CONTEXT_MENU_ROW_HEIGHT, 0xFFFFFFFF);
            if (this.contextMenuContains(mouseX, mouseY)
                    && (mouseY - y - 4) / CONTEXT_MENU_ROW_HEIGHT == index) {
                graphics.setTooltipForNextFrame(label, mouseX, mouseY);
            }
        }
    }

    /// 显示鼠标下方的方块、群系和最高优先级领地。
    private void drawLocationInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        double worldX = this.screenToWorldX(mouseX);
        double worldZ = this.screenToWorldZ(mouseY);
        BlockPos pos = new BlockPos((int) Math.floor(worldX), this.surfaceYAt(worldX, worldZ),
                (int) Math.floor(worldZ));
        var cached = this.cache == null ? null : this.cache.get(pos.getX() >> 4, pos.getZ() >> 4).orElse(null);
        var pixel = cached == null ? null : cached.stateAt(pos.getX() & 15, pos.getZ() & 15);
        BlockState state = pixel == null ? minecraft.level.getBlockState(pos) : pixel.state();
        Component block = state.isAir()
                ? Component.translatable("aleeve_atlas.screen.world_map.unknown")
                : state.getBlock().getName();
        Identifier biomeId = pixel == null ? null : Identifier.tryParse(pixel.biomeId());
        if (biomeId == null && minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            biomeId = minecraft.level.getBiome(pos).unwrapKey().map(key -> key.identifier()).orElse(null);
        }
        String biomeKey = biomeId == null ? "aleeve_atlas.screen.world_map.unknown"
                : "biome." + biomeId.getNamespace() + "." + biomeId.getPath();
        Component location = Component.translatable("aleeve_atlas.screen.world_map.location",
                block, pos.getX(), pos.getY(), pos.getZ(), Component.translatable(biomeKey));
        List<TerritoryGroup> territories = AtlasTerritory.clientRegistry()
                .hitAt(minecraft.level.dimension(), ChunkPos.containing(pos));
        if (!territories.isEmpty()) {
            location = Component.empty().append(location).append("  ")
                    .append(Component.translatable("aleeve_atlas.screen.world_map.territory",
                            territories.get(0).displayName()));
        }
        graphics.text(minecraft.font, location, 6, height - 28, 0xFFDDDDDD);
    }

    private static double lerp(double start, double end, float progress) {
        return start + (end - start) * progress;
    }

    private double mapAnimationX(double screenX) {
        return width / 2.0 + (screenX - width / 2.0) * this.animationScale;
    }

    private double mapAnimationY(double screenY) {
        return height / 2.0 + (screenY - height / 2.0) * this.animationScale;
    }

    private void alignCameraToRenderedFrame() {
        this.centerX = this.renderedCenterX;
        this.centerZ = this.renderedCenterZ;
        this.pixelsPerChunk = this.renderedPixelsPerChunk;
        this.previousCenterX = this.centerX;
        this.previousCenterZ = this.centerZ;
        this.previousPixelsPerChunk = this.pixelsPerChunk;
    }

    private double viewCenterX() {
        return this.renderingFrame ? this.centerX : this.renderedCenterX;
    }

    private double viewCenterZ() {
        return this.renderingFrame ? this.centerZ : this.renderedCenterZ;
    }

    private float viewPixelsPerChunk() {
        return this.renderingFrame ? this.pixelsPerChunk : this.renderedPixelsPerChunk;
    }

    private double logicalMouseX(double mouseX) {
        return width / 2.0 + (mouseX - width / 2.0) / this.animationScale;
    }

    private double logicalMouseY(double mouseY) {
        return height / 2.0 + (mouseY - height / 2.0) / this.animationScale;
    }

    private MouseButtonEvent logicalMouseEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(this.logicalMouseX(event.x()), this.logicalMouseY(event.y()),
                event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.middleHeld = false;
        if (this.structures.contains(event.x(), event.y())) {
            this.contextMenu = null;
            this.dragging = false;
            return super.mouseClicked(event, doubleClick) || this.structures.click(event);
        }
        if (this.contextMenu != null) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && this.contextMenuContains(event.x(), event.y())) {
                this.executeContextMenu(event.y());
            } else {
                this.contextMenu = null;
            }
            return true;
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (this.structures.click(event)) {
            this.dragging = false;
            return true;
        }
        this.alignCameraToRenderedFrame();
        event = this.logicalMouseEvent(event);
        if (this.craftingHeld == null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.y() > 30) {
            StructureRecord record = this.findStructureAt(event.x(), event.y());
            if (record != null) {
                this.structures.select(record);
                return true;
            }
        }
        if (this.craftingHeld != null) {
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { this.finishCartography(); return true; }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                this.dragging = true; this.lastMouseX = event.x(); this.lastMouseY = event.y(); return true;
            }
            if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.y() > 30 && this.craftingHeld.valid()) {
                int x = Cartography.center(this.screenToWorldX(event.x()));
                int z = Cartography.center(this.screenToWorldZ(event.y()));
                if (this.minecraft.level == null || !Cartography.validRegion(this.minecraft.level, x, z)) {
                    ClientCartography.message("region"); return true;
                }
                byte[] colors = ClientCartography.preview(x, z);
                if (!Cartography.hasContent(colors)) { ClientCartography.message("empty"); return true; }
                this.dragging = false;
                this.minecraft.setScreen(new CartographyConfirmScreen(this, this.craftingHeld, x, z, colors));
            }
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.middleHeld = true;
            this.wheelOpened = false;
            this.middleHoldTicks = 0;
            this.middleWorldX = this.screenToWorldX(event.x());
            this.middleWorldZ = this.screenToWorldZ(event.y());
            this.dragging = false;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            this.openContextMenu(event.x(), event.y());
            return true;
        }
        // 双击左键在地图位置创建路径点。
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && doubleClick) {
            this.createWaypointAt(event.x(), event.y());
            this.dragging = false;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = true;
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
            return true;
        }
        return false;
    }

    /// 在地图位置打开创建路径点或删除路径点的菜单。
    private void openContextMenu(double screenX, double screenY) {
        this.dragging = false;
        Waypoint waypoint = this.findWaypointAt(screenX, screenY);
        List<ContextAction> actions = new ArrayList<>();
        BlockPos target = new BlockPos((int) Math.floor(this.screenToWorldX(screenX)),
                this.surfaceYAt(this.screenToWorldX(screenX), this.screenToWorldZ(screenY)),
                (int) Math.floor(this.screenToWorldZ(screenY)));
        if (waypoint == null) {
            actions.add(new ContextAction(Component.translatable(
                    "aleeve_atlas.screen.world_map.add_waypoint"),
                    () -> this.createWaypointAt(screenX, screenY)));
        } else {
            if (waypoint.shareable()) {
                actions.add(new ContextAction(Component.translatable(
                        "aleeve_atlas.screen.world_map.share_team"),
                        () -> ClientWaypoints.share(waypoint)));
            }
            target = waypoint.pos();
        }
        BlockPos teleportTarget = target;
        actions.add(new ContextAction(Component.translatable(
                "aleeve_atlas.screen.world_map.teleport"),
                () -> WaypointActions.teleport(teleportTarget)));
        actions.add(new ContextAction(Component.translatable(
                "aleeve_atlas.screen.world_map.share_coordinates"),
                () -> WaypointActions.shareCoordinates(teleportTarget)));
        if (waypoint != null) {
            actions.add(new ContextAction(Component.translatable(
                    "aleeve_atlas.screen.world_map.delete_waypoint"),
                    () -> this.deleteWaypoint(waypoint)));
        }
        int menuHeight = actions.size() * CONTEXT_MENU_ROW_HEIGHT + 4;
        int left = Math.max(4, this.structures.width() + 4);
        int menuWidth = Math.min(CONTEXT_MENU_WIDTH, this.width - left - 4);
        int menuX = (int) Math.max(left, Math.min(width - menuWidth - 4,
                this.mapAnimationX(screenX)));
        int menuY = (int) Math.max(4, Math.min(height - menuHeight - 4,
                this.mapAnimationY(screenY)));
        this.contextMenu = new ContextMenu(this.screenToWorldX(screenX),
                this.screenToWorldZ(screenY), menuX, menuY, menuWidth, actions);
    }

    private boolean contextMenuContains(double x, double y) {
        return this.contextMenu != null && x >= this.contextMenu.screenX()
                && x < this.contextMenu.screenX() + this.contextMenu.width()
                && y >= this.contextMenu.screenY()
                && y < this.contextMenu.screenY() + this.contextMenuHeight();
    }

    private int contextMenuHeight() {
        return this.contextMenu.actions().size() * CONTEXT_MENU_ROW_HEIGHT + 4;
    }

    private void executeContextMenu(double mouseY) {
        if (this.contextMenu == null) {
            return;
        }
        int index = (int) Math.floor((mouseY - this.contextMenu.screenY() - 4.0)
                / CONTEXT_MENU_ROW_HEIGHT);
        if (index >= 0 && index < this.contextMenu.actions().size()) {
            this.contextMenu.actions().get(index).action().run();
        }
        this.contextMenu = null;
    }

    private void deleteWaypoint(Waypoint waypoint) {
        var store = ClientWaypoints.store();
        boolean removed = store != null && store.remove(waypoint.id());
        if (removed && AtlasClientConfigs.CONFIG.waypoint.sendPolicy
                == WaypointSendPolicy.AUTO_SYNC) {
            ClientWaypoints.shareDelete(waypoint);
        }
    }

    /// 命中地图上最近的启用路径点。
    private Waypoint findWaypointAt(double screenX, double screenY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        Waypoint nearest = null;
        double nearestDistance = 12.0 * 12.0;
        for (Waypoint waypoint : ClientWaypoints.waypointsFor(minecraft.level.dimension())) {
            double x = width / 2.0
                    + (waypoint.pos().getX() + 0.5 - this.viewCenterX())
                            * this.viewPixelsPerChunk();
            double y = height / 2.0
                    + (waypoint.pos().getZ() + 0.5 - this.viewCenterZ())
                            * this.viewPixelsPerChunk();
            double distance = (x - screenX) * (x - screenX) + (y - screenY) * (y - screenY);
            if (distance <= nearestDistance) {
                nearest = waypoint;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /// 在指定屏幕位置创建路径点编辑界面。
    private void createWaypointAt(double screenX, double screenY) {
        this.createWaypoint(this.screenToWorldX(screenX), this.screenToWorldZ(screenY));
    }

    private void createWaypoint(double worldX, double worldZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        int y = this.surfaceYAt(worldX, worldZ);
        BlockPos pos = new BlockPos((int) Math.floor(worldX), y, (int) Math.floor(worldZ));
        minecraft.setScreen(new WaypointEditScreen(this, pos));
    }

    /// 根据指定类型创建中键标点。
    void createWheelPing(MapPingData.PingIcon icon) {
        this.createPing(this.middleWorldX, this.middleWorldZ, icon);
    }

    /// WheelScreen 关闭后清理中键状态。
    void finishWheel() {
        this.wheelOpened = false;
        this.middleHeld = false;
        this.restoreWheelCursor();
        if (active == this && Minecraft.getInstance().screen != this) {
            active = null;
        }
    }

    private void rememberCursorAndCenter() {
        Minecraft minecraft = Minecraft.getInstance();
        this.wheelCursorX = minecraft.mouseHandler.xpos();
        this.wheelCursorY = minecraft.mouseHandler.ypos();
        var window = minecraft.getWindow();
        GLFW.glfwSetCursorPos(window.handle(), window.getScreenWidth() / 2.0,
                window.getScreenHeight() / 2.0);
    }

    private void restoreWheelCursor() {
        if (this.wheelCursorX == null || this.wheelCursorY == null) {
            return;
        }
        GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().handle(),
                this.wheelCursorX, this.wheelCursorY);
        this.wheelCursorX = null;
        this.wheelCursorY = null;
    }

    private void createPing(double worldX, double worldZ, MapPingData.PingIcon icon) {
        int y = this.surfaceYAt(worldX, worldZ);
        ClientPings.createLocal(new Vec3(worldX, y, worldZ), icon);
    }

    /// 由地图缓存推断地表高度；无数据时回退到海平面以上默认值。
    private int surfaceYAt(double worldX, double worldZ) {
        ChunkPos pos = new ChunkPos((int) Math.floor(worldX) >> 4, (int) Math.floor(worldZ) >> 4);
        var data = this.cache == null ? null : this.cache.get(pos.x(), pos.z()).orElse(null);
        if (data == null) {
            return 64;
        }
        int localX = (int) Math.floor(worldX) & 15;
        int localZ = (int) Math.floor(worldZ) & 15;
        if (data.stateAt(localX, localZ).mapColor() == MapColor.NONE) {
            return 64;
        }
        return data.heightAt(localX, localZ);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.structures.contains(event.x(), event.y())) {
            this.middleHeld = false;
            this.dragging = false;
            return true;
        }
        if (this.craftingHeld != null) { this.dragging = false; return true; }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && this.middleHeld) {
            this.alignCameraToRenderedFrame();
            this.middleHeld = false;
            if (!this.wheelOpened && this.middleHoldTicks < MIDDLE_HOLD_TICKS) {
                MapPingInput input = MinimapPingAPI.mapInput();
                if (input == null) {
                    this.createPing(this.middleWorldX, this.middleWorldZ, MapPingData.PingIcon.GENERAL);
                } else {
                    int y = this.surfaceYAt(this.middleWorldX, this.middleWorldZ);
                    input.ping(new Vec3(this.middleWorldX, y, this.middleWorldZ));
                }
            }
            return true;
        }
        if (super.mouseReleased(event)) {
            this.dragging = false;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.structures.contains(event.x(), event.y()) && !this.dragging) {
            return true;
        }
        if (this.contextMenu != null) {
            this.contextMenu = null;
        }
        if (this.wheelOpened || this.middleHeld) {
            return true;
        }
        if (this.dragging) {
            this.alignCameraToRenderedFrame();
            event = this.logicalMouseEvent(event);
            this.zoomAnchored = false;
            // 事件携带的是从按下点起的总位移，因此用相邻事件差得到帧增量。
            this.centerX -= (event.x() - this.lastMouseX) / this.pixelsPerChunk;
            this.centerZ -= (event.y() - this.lastMouseY) / this.pixelsPerChunk;
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
            this.refreshCanvas();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.structures.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        this.alignCameraToRenderedFrame();
        double mapMouseX = this.logicalMouseX(mouseX);
        double mapMouseY = this.logicalMouseY(mouseY);
        this.contextMenu = null;
        this.middleHeld = false;
        this.wheelOpened = false;
        if (scrollY != 0.0) {
            double anchorX = this.screenToWorldX(mapMouseX);
            double anchorZ = this.screenToWorldZ(mapMouseY);
            this.targetPixelsPerChunk = ConfigBounds.clamp(
                    (float) (this.targetPixelsPerChunk * (scrollY > 0 ? 1.25 : 0.8)),
                    MIN_PIXELS_PER_CHUNK, MAX_PIXELS_PER_CHUNK);
            this.zoomAnchorWorldX = anchorX;
            this.zoomAnchorWorldZ = anchorZ;
            this.zoomAnchorScreenX = mapMouseX;
            this.zoomAnchorScreenY = mapMouseY;
            this.zoomAnchored = true;
            if (!AtlasClientConfigs.CONFIG.worldMap.smoothZoom) {
                this.pixelsPerChunk = this.targetPixelsPerChunk;
                this.zoomAnchored = false;
            }
            this.centerX = anchorX - (mapMouseX - width / 2.0) / this.pixelsPerChunk;
            this.centerZ = anchorZ - (mapMouseY - height / 2.0) / this.pixelsPerChunk;
            this.previousPixelsPerChunk = this.pixelsPerChunk;
            this.previousCenterX = this.centerX;
            this.previousCenterZ = this.centerZ;
            if (!AtlasClientConfigs.CONFIG.worldMap.smoothZoom) {
                this.renderedPixelsPerChunk = this.pixelsPerChunk;
                this.renderedCenterX = this.centerX;
                this.renderedCenterZ = this.centerZ;
            }
            this.refreshCanvas();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && this.structures.open()) {
            this.structures.toggle();
            return true;
        }
        if (this.craftingHeld != null && event.key() == GLFW.GLFW_KEY_ESCAPE) { this.finishCartography(); return true; }
        this.middleHeld = false;
        if (this.contextMenu != null || this.wheelOpened) {
            this.contextMenu = null;
            this.middleHeld = false;
            return true;
        }
        // ESC 与呼出键均可关闭世界地图。
        if (event.key() == GLFW.GLFW_KEY_ESCAPE
                || AtlasKeyMappings.OPEN_WORLD_MAP.matches(event)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.contextMenu = null;
        this.wheelOpened = false;
        this.middleHeld = false;
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ContextMenu(double worldX, double worldZ, int screenX, int screenY, int width,
            List<ContextAction> actions) {
    }

    private record ContextAction(Component label, Runnable action) {
    }

    /// 按当前视野中心重绘画布。
    private void refreshCanvas() {
        int centerChunkX = (int) Math.floor(this.centerX / ChunkMapData.SIZE);
        int centerChunkZ = (int) Math.floor(this.centerZ / ChunkMapData.SIZE);
        ClientMapManager manager = AtlasClientMap.manager();
        if (manager != null) {
            this.cache = manager.displayCache();
            this.caveView = manager.displaySlice().cave();
        }
        this.canvas.setCaveView(this.caveView, AtlasClientConfigs.CONFIG.caves.depthShading);
        this.canvas.setCenter(centerChunkX, centerChunkZ, this.cache,
                AtlasClientConfigs.CONFIG.minimap.colorScheme,
                width, height, Math.min(this.pixelsPerChunk, this.previousPixelsPerChunk));
    }
}
