package dev.dubhe.map.client.waypoint;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.api.territory.BorderStyle;
import dev.dubhe.map.client.map.SlimeChunkTracker;
import dev.dubhe.map.client.profiling.AtlasProfiling;
import dev.dubhe.map.ping.ClientPings;
import dev.dubhe.map.ping.MapPingData;
import dev.dubhe.map.ping.PingChannelPolicy;
import dev.dubhe.map.team.TeamProviders;
import dev.dubhe.map.territory.SimpleTerritory;
import dev.dubhe.map.waypoint.WaypointTransfer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// 客户端命令。
///
/// 仅注册客户端命令，不污染服务端命令表：
/// `/aleeve_atlas waypoint accept|ignore <id>`、`/aleeve_atlas territory demo|clear`。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class AtlasClientCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/command");
    private static final String DEMO_ID = "aleeve_atlas_demo";
    private static final String DEMO_OVERLAY_ID = "aleeve_atlas_demo_overlay";

    private AtlasClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        // 主名用不含下划线的 aleeveatlas（便于输入），同时保留 aleeve_atlas 作为别名。
        dispatcher.register(build(Commands.literal("aleeveatlas")));
        dispatcher.register(build(Commands.literal("aleeve_atlas")));
    }

    /// 组装命令树。
    private static LiteralArgumentBuilder<CommandSourceStack> build(
            LiteralArgumentBuilder<CommandSourceStack> root) {
        return root
                .then(Commands.literal("waypoint")
                        .then(Commands.literal("accept")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> confirm(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                true))))
                        .then(Commands.literal("ignore")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(context -> confirm(context.getSource(),
                                                StringArgumentType.getString(context, "id"),
                                                false))))
                        .then(Commands.literal("export")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .executes(context -> exportWaypoints(context.getSource(),
                                                StringArgumentType.getString(context, "file")))))
                        .then(Commands.literal("import")
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .executes(context -> importWaypoints(context.getSource(),
                                                StringArgumentType.getString(context, "file"))))))
                .then(Commands.literal("territory")
                        .then(Commands.literal("demo")
                                .executes(context -> demo(context.getSource())))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource()))))
                .then(Commands.literal("slime_seed")
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(context -> setSlimeSeed(
                                        LongArgumentType.getLong(context, "seed")))))
                .then(Commands.literal("pingdemo")
                        .executes(context -> pingDemo(context.getSource())))
                .then(Commands.literal("perf")
                        .executes(context -> reportPerformance(context.getSource())));
    }

    private static int setSlimeSeed(long seed) {
        SlimeChunkTracker.setSeed(seed);
        return 1;
    }

    /// 在玩家前方注入一个临时标点，用于验收波纹扩散渲染。
    private static int pingDemo(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        var target = player.position().add(player.getViewVector(1.0F).scale(12.0));
        var ping = ClientPings.createLocal(target,
                MapPingData.PingIcon.GENERAL);
        return ping == null ? 0 : 1;
    }

    /// 输出渲染性能统计。
    private static int reportPerformance(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        player.sendSystemMessage(AtlasProfiling.minimapDiagnostic());
        player.sendSystemMessage(AtlasProfiling.canvasDiagnostic());
        // 标点通道诊断：确认是否存在与外部模组重复广播的风险。
        player.sendSystemMessage(PingChannelPolicy.describeComponent());
        if (PingChannelPolicy.duplicateRisk()) {
            player.sendSystemMessage(
                    Component.translatable("aleeve_atlas.message.ping_channel_duplicate"));
        }
        // 队伍适配层诊断：确认当前实现与所属队伍。
        player.sendSystemMessage(TeamProviders.describeComponent(player.getUUID()));
        AtlasProfiling.reset();
        return 1;
    }

    private static int confirm(CommandSourceStack source, String rawId, boolean accepted) {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid waypoint id for confirm: {}", rawId, e);
            return 0;
        }
        ClientWaypointSync.confirm(id, accepted);
        return 1;
    }

    /// 导出当前路径点到文件。
    private static int exportWaypoints(CommandSourceStack source, String fileName) {
        var waypointStore = ClientWaypoints.store();
        var player = source.getPlayer();
        if (waypointStore == null || player == null) {
            return 0;
        }
        try {
            var file = resolveExportFile(fileName);
            // 先把内存中的改动写入磁盘：allAcrossDimensions 以磁盘文件为准，
            // 未刷新的近期改动否则不会被导出。
            ClientWaypoints.flushAllDimensions();
            WaypointTransfer.exportTo(file,
                    ClientWaypoints.allAcrossDimensions());
            player.sendSystemMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_exported", file.toString()));
            return 1;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Failed to export waypoints to {}", fileName, e);
            player.sendSystemMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_export_failed"));
            return 0;
        }
    }

    /// 从文件导入路径点并合并。
    private static int importWaypoints(CommandSourceStack source, String fileName) {
        var waypointStore = ClientWaypoints.store();
        var player = source.getPlayer();
        if (waypointStore == null || player == null) {
            return 0;
        }
        try {
            var file = resolveExportFile(fileName);
            var imported = WaypointTransfer.importFrom(file);
            var result = WaypointTransfer.merge(imported,
                    ClientWaypoints.allAcrossDimensions());
            // 按各自维度写入，跨维度导入不会串到当前维度。
            for (var waypoint : result.added()) {
                ClientWaypoints.upsertInDimension(waypoint);
            }
            for (var waypoint : result.replaced()) {
                ClientWaypoints.upsertInDimension(waypoint);
            }
            player.sendSystemMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_imported",
                    result.added().size(), result.replaced().size()));
            return 1;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Failed to import waypoints from {}", fileName, e);
            player.sendSystemMessage(Component.translatable(
                    "aleeve_atlas.message.waypoint_import_failed"));
            return 0;
        }
    }

    /// 导入导出目录：客户端游戏目录下的 `aleeve_atlas_exports`。
    private static Path exportDir() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("aleeve_atlas_exports");
    }

    /// 把用户输入的文件名解析为导出目录内的文件，拒绝越出该目录的路径。
    ///
    /// 文件名来自命令参数，必须限制为单层文件名，避免 `..` 之类的输入写到其他位置。
    private static Path resolveExportFile(String fileName) {
        Path dir = exportDir().normalize();
        Path resolved = dir.resolve(fileName).normalize();
        if (!resolved.startsWith(dir) || resolved.equals(dir)) {
            throw new IllegalArgumentException("Invalid export file name: " + fileName);
        }
        return resolved;
    }

    /// 注册一个含飞地与内孔洞的演示领地，用于验证飞地与内孔洞的渲染。
    private static int demo(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null || source.getLevel() == null) {
            return 0;
        }
        final var dimension = source.getLevel().dimension();
        int baseX = player.chunkPosition().x();
        int baseZ = player.chunkPosition().z();

        Set<ChunkPos> chunks = new HashSet<>();
        // 主区域：7×7 实心方块。
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                chunks.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 内孔洞：中央 3×3 挖空（形成环形，验证内圈闭合边框）。
        for (int dx = 2; dx <= 4; dx++) {
            for (int dz = 2; dz <= 4; dz++) {
                chunks.remove(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 飞地 1：对角相接（8 邻域下仍属于同一领地，但独立渲染）。
        chunks.add(new ChunkPos(baseX + 8, baseZ + 8));
        // 飞地 2：完全分离的小岛。
        chunks.add(new ChunkPos(baseX - 3, baseZ - 3));
        chunks.add(new ChunkPos(baseX - 2, baseZ - 3));

        AtlasTerritory.client().upsertTerritory(SimpleTerritory.of(DEMO_ID, dimension, chunks,
                Component.translatable("aleeve_atlas.territory.demo_name"), 0x33AAFF));

        // 叠加一个 zOrder 更高的重叠领地，用于验证边框取舍。
        Set<ChunkPos> overlay = new HashSet<>();
        for (int dx = 1; dx <= 3; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                overlay.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        AtlasTerritory.client().upsertTerritory(new SimpleTerritory(DEMO_OVERLAY_ID, dimension,
                overlay, Component.translatable("aleeve_atlas.territory.demo_overlay_name"),
                0xFFAA33, 0.55F, BorderStyle.ofTheme(0xFFAA33), List.of(), 10));

        player.sendOverlayMessage(
                Component.translatable("aleeve_atlas.message.territory_demo_added"));
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        AtlasTerritory.client().removeTerritory(DEMO_ID);
        AtlasTerritory.client().removeTerritory(DEMO_OVERLAY_ID);
        var player = source.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(
                    Component.translatable("aleeve_atlas.message.territory_demo_cleared"));
        }
        return 1;
    }
}
