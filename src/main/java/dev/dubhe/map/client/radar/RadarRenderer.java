package dev.dubhe.map.client.radar;

import dev.dubhe.map.AtlasSession;
import dev.dubhe.map.client.map.MapMarkerRenderer;
import dev.dubhe.map.client.map.MinimapLayout;
import dev.dubhe.map.config.AtlasClientConfig;
import dev.dubhe.map.config.MinimapShape;
import dev.dubhe.map.config.RadarDisplayMode;
import dev.dubhe.map.map.MapPalette;
import dev.dubhe.map.sync.ClientPlayerPositions;
import dev.dubhe.map.team.TeamProviders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 实体雷达的小地图与世界地图标记渲染。
///
/// 圆点模式下按类别配色绘制；玩家列表按住模式下按住玩家列表键时，
/// 玩家标记切换为皮肤头像和实体图标。四种模式共用分类、距离、可见性与渐隐规则。
public final class RadarRenderer {
    /// 玩家头像尺寸（GUI 像素）。
    private static final int HEAD_SIZE = 6;
    private static final int ICON_SIZE = 8;
    private static final float ICON_SCALE = 0.5F;
    /// 掉落物合并粒度（像素），同格内只绘制一个圆点。
    private static final double ITEM_MERGE_PIXELS = 4.0;
    /// 皮肤未就绪时的占位纹理。
    private static final Identifier DEFAULT_SKIN_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    private RadarRenderer() {
    }

    /// 在小地图上绘制雷达标记。
    public static void render(GuiGraphics graphics, AtlasClientConfig config,
            MinimapLayout layout, Minecraft minecraft, double rotationDegrees) {
        boolean circular = config.minimap.shape == MinimapShape.CIRCLE;
        renderMarkers(graphics, config, minecraft, new MarkerProjection() {
            @Override
            public double screenX(Vec3 position) {
                return layout.rotatedScreenPoint(position.x, position.z, rotationDegrees)[0];
            }

            @Override
            public double screenY(Vec3 position) {
                return layout.rotatedScreenPoint(position.x, position.z, rotationDegrees)[1];
            }

            @Override
            public boolean contains(double screenX, double screenY, double margin) {
                double centerX = layout.x() + layout.size() / 2.0;
                double centerY = layout.y() + layout.size() / 2.0;
                if (circular) {
                    double dx = screenX - centerX;
                    double dy = screenY - centerY;
                    return Math.hypot(dx, dy)
                            <= layout.size() / 2.0 - MinimapLayout.CLIP_INSET - margin;
                }
                return screenX >= layout.x() + MinimapLayout.CLIP_INSET + margin
                        && screenX <= layout.x() + layout.size()
                                - MinimapLayout.CLIP_INSET - margin
                        && screenY >= layout.y() + MinimapLayout.CLIP_INSET + margin
                        && screenY <= layout.y() + layout.size()
                                - MinimapLayout.CLIP_INSET - margin;
            }

            @Override
            public double[] fit(double x, double y, double margin) {
                return layout.fitInside(x, y, circular, margin, 0.0);
            }
        });
    }

    /// 在世界地图上绘制雷达标记，坐标换算与全屏地图一致。
    public static void renderOnWorldMap(GuiGraphics graphics, AtlasClientConfig config,
            Minecraft minecraft, double centerX, double centerZ, float pixelsPerChunk,
            int viewWidth, int viewHeight) {
        renderMarkers(graphics, config, minecraft, new MarkerProjection() {
            @Override
            public double screenX(Vec3 position) {
                return viewWidth / 2.0 + (position.x - centerX) * pixelsPerChunk;
            }

            @Override
            public double screenY(Vec3 position) {
                return viewHeight / 2.0 + (position.z - centerZ) * pixelsPerChunk;
            }

            @Override
            public boolean contains(double x, double y, double margin) {
                return x >= margin && x <= viewWidth - margin
                        && y >= margin && y <= viewHeight - margin;
            }
        });
    }

    private static void renderMarkers(GuiGraphics graphics, AtlasClientConfig config,
            Minecraft minecraft, MarkerProjection projection) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        if (config.radar.displayMode == RadarDisplayMode.OFF) {
            return;
        }
        RadarDisplayMode mode = config.radar.displayMode;
        boolean showIcons = mode == RadarDisplayMode.ICON;
        boolean playerListHeld = mode == RadarDisplayMode.HYBRID;
        boolean playerListDown = playerListHeld && minecraft.options.keyPlayerList.isDown();
        boolean expandAllIcons = showIcons || playerListDown;
        Set<Long> itemDots = new HashSet<>();
        int maxDistance = AtlasSession.maxRadarDistance();
        double maxDistanceSq = (double) maxDistance * maxDistance;

        List<Entity> targets = new ArrayList<>();
        Set<UUID> loadedPlayerIds = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            targets.add(entity);
            loadedPlayerIds.add(entity.getUUID());
        }
        for (var remote : ClientPlayerPositions.remotePlayers()) {
            if (loadedPlayerIds.add(remote.getUUID())) {
                targets.add(remote);
            }
        }

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Entity entity : targets) {
            boolean sharedPlayer = entity instanceof Player
                    && (RadarStyle.isTeammate(entity) || ClientPlayerPositions.sharedVisible(entity.getUUID()));
            if (entity == player || entity.isRemoved()
                    || (!sharedPlayer && entity.distanceToSqr(player) > maxDistanceSq)) {
                continue;
            }
            RadarCategory category = RadarCategory.classify(entity);
            if (category == null
                    || !RadarStyle.visible(config.radar.categories, category)) {
                continue;
            }
            float opacity = 1.0F;
            if (category == RadarCategory.PLAYER && !RadarStyle.isTeammate(entity)) {
                opacity = VisibilityGate.opacity(level, player, entity);
                if (opacity <= 0.0F) {
                    continue;
                }
            }
            // 已加载实体与小地图中心使用同一帧插值；同步代理不参与世界 tick，不能使用其旧坐标插值。
            Vec3 position = level.getEntity(entity.getId()) == entity
                    ? entity.getPosition(partialTick) : entity.position();
            double screenX = projection.screenX(position);
            double screenY = projection.screenY(position);
            boolean drawHead = opacity >= 1.0F && category == RadarCategory.PLAYER
                    && shouldDrawPlayerHead(mode, playerListDown);
            boolean drawIcon = opacity >= 1.0F && expandAllIcons
                    && category != RadarCategory.ITEM;
            double margin = drawHead ? Math.sqrt(2.0) * HEAD_SIZE / 2.0 + 1.0
                    : drawIcon ? Math.sqrt(2.0) * ICON_SIZE / 2.0 + 1.0 : 4.5;
            if (!projection.contains(screenX, screenY, margin)) {
                continue;
            }
            double[] fitted = projection.fit(screenX, screenY, margin);
            screenX = fitted[0];
            screenY = fitted[1];
            // 头像和物品图标没有透明度参数，渐隐阶段改用圆点保持同样的衰减规则。
            if (drawHead) {
                drawPlayerHead(graphics, entity, screenX, screenY);
                continue;
            }
            if (drawIcon && drawEntityIcon(graphics, entity, screenX, screenY)) {
                continue;
            }
            int color = applyOpacity(
                    RadarStyle.color(config.radar.categories, category, entity), opacity);
            if (category == RadarCategory.HOSTILE) {
                color = applyHeightShade(color, entity.getY() - player.getY());
            }
            if (category == RadarCategory.ITEM
                    && !itemDots.add(packScreen(screenX, screenY))) {
                continue;
            }
            if (category == RadarCategory.PLAYER && RadarStyle.isTeammate(entity)) {
                drawTeamOutline(graphics, screenX, screenY, entity);
            }
            drawDot(graphics, screenX, screenY, color);
        }
    }

    /// 地图坐标转换与视口裁剪。
    private interface MarkerProjection {
        double screenX(Vec3 position);

        double screenY(Vec3 position);

        boolean contains(double x, double y, double margin);

        /// 将标记中心限制在地图边界内，世界地图默认无需调整。
        default double[] fit(double x, double y, double margin) {
            return new double[] { x, y };
        }
    }

    /// 高度差明暗：高于玩家偏亮、低于偏暗，幅度限制在 70%~130%。
    private static int applyHeightShade(int argb, double deltaY) {
        double ratio = 1.0 + Math.max(-0.3, Math.min(0.3, deltaY / 64.0));
        int percent = (int) Math.round(ratio * 100.0);
        return MapPalette.scale(argb, percent);
    }

    /// 同队玩家圆点外层描边为队伍颜色。
    private static void drawTeamOutline(GuiGraphics graphics, double screenX, double screenZ,
            Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        var team = TeamProviders.active().teamIdOf(entity.getUUID());
        int color = TeamProviders.active().teamColor(team);
        MapMarkerRenderer.drawDot(graphics, screenX, screenZ, 7, color);
    }

    /// 把屏幕坐标按 4 像素粒度打包，用于合并同格掉落物。
    private static long packScreen(double screenX, double screenZ) {
        long gx = Math.round(screenX / ITEM_MERGE_PIXELS);
        long gz = Math.round(screenZ / ITEM_MERGE_PIXELS);
        return (gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    /// 按不透明度缩放颜色的 alpha 通道。
    private static int applyOpacity(int argb, float opacity) {
        if (opacity >= 1.0F) {
            return argb;
        }
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0F, opacity));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static void drawDot(GuiGraphics graphics, double screenX, double screenZ, int color) {
        MapMarkerRenderer.drawDot(graphics, screenX, screenZ, 5, color);
    }

    /// 生物图标：用刷怪蛋物品图标表示实体类型；无对应刷怪蛋时返回 false 由调用方回退圆点。
    private static boolean drawEntityIcon(GuiGraphics graphics, Entity entity, double screenX,
            double screenZ) {
        SpawnEggItem egg =
                SpawnEggItem.byId(entity.getType());
        if (egg == null) {
            return false;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) screenX, (float) screenZ);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE);
        graphics.renderItem(new ItemStack(egg), -8, -8);
        graphics.pose().popMatrix();
        return true;
    }

    /// 是否以玩家头像渲染玩家标记。
    ///
    /// 图标模式下恒为头像；混合联动模式下仅在按住玩家列表键时切换为头像。
    static boolean shouldDrawPlayerHead(RadarDisplayMode mode, boolean playerListDown) {
        return mode == RadarDisplayMode.ICON
                || (mode == RadarDisplayMode.HYBRID && playerListDown);
    }

    /// 玩家皮肤头像：基础层 + 帽子层。
    ///
    /// 远端皮肤尚未完成异步加载时回退到默认 Steven/Alex 占位纹理，
    /// 避免绘制空白或抛错。
    private static void drawPlayerHead(GuiGraphics graphics, Entity entity, double screenX,
            double screenZ) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        Identifier skinTexture = resolveSkinTexture(player);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (screenX - HEAD_SIZE / 2.0),
                (float) (screenZ - HEAD_SIZE / 2.0));
        // 玩家皮肤纹理中头部正面区域为 (8, 8, 8, 8)，帽子层为 (40, 8, 8, 8)。
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, 0, 0, 8.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skinTexture, 0, 0, 40.0F, 8.0F,
                HEAD_SIZE, HEAD_SIZE, 8, 8, 64, 64);
        graphics.pose().popMatrix();
    }

    /// 解析皮肤纹理；纹理不可用时返回默认皮肤。
    private static Identifier resolveSkinTexture(AbstractClientPlayer player) {
        var texture = player.getSkin().body();
        if (texture != null && texture.texturePath() != null) {
            return texture.texturePath();
        }
        return DEFAULT_SKIN_TEXTURE;
    }
}
