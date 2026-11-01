package dev.dubhe.map.structure;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 隔离服务器验收入口，显式环境变量启用后生成测试结构并在完成时关闭服务器。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class StructureServerAcceptance {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureServerAcceptance.class);
    private static int index;
    private static boolean finished;

    private StructureServerAcceptance() {
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (finished || !"1".equals(System.getenv("ALEEVE_ATLAS_STRUCTURE_SERVER_TEST"))
                || event.getServer().getTickCount() < 20) {
            return;
        }
        try {
            var level = event.getServer().overworld();
            require(!AleeveAtlas.CONFIG.structures.allowUnexploredSearch, "默认禁止迷雾查询");
            if (index == StructureKind.values().length) {
                LOGGER.info("Structure server acceptance PASSED: all ten types, vanilla locate, loaded-only scans, delayed references");
                finished = true;
                event.getServer().halt(false);
                return;
            }
            StructureKind kind = StructureKind.values()[index++];
            var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(kind.structureId()).orElseThrow();
            require(BuiltInRegistries.MAP_DECORATION_TYPE.get(kind.decorationId()).isPresent(), "原版地图装饰存在");
            var record = ServerStructureSearch.locate(level, BlockPos.ZERO, kind);
            require(record != null, "原版搜索找到 " + kind);
            if (record == null) {
                return;
            }
            var vanilla = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(structure), BlockPos.ZERO, 100, false);
            require(vanilla != null && vanilla.getFirst().getX() == record.position().getX()
                    && vanilla.getFirst().getZ() == record.position().getZ(), "与原版 locate 坐标相同 " + kind);
            // 只在验收准备阶段加载结构，用于随后检查正式扫描入口的加载副作用。
            var chunk = level.getChunk(record.chunkX(), record.chunkZ());
            ChunkPos tested = new ChunkPos(Math.floorDiv(record.minX(), 16), Math.floorDiv(record.minZ(), 16));
            level.getChunk(tested.x(), tested.z());
            int before = level.getChunkSource().getLoadedChunksCount();
            var scan = ServerStructures.scanLoaded(level, tested);
            require(scan.records().stream().anyMatch(r -> r.key().equals(record.key())), "从相交区块识别 " + kind);
            require(level.getChunkSource().getLoadedChunksCount() == before, "自然扫描不增加已加载区块");
            ChunkPos distant = new ChunkPos(1_000_000, -1_000_000);
            require(ServerStructures.scanLoaded(level, distant).records().isEmpty(), "未加载区块不产生记录");
            require(level.getChunkSource().getLoadedChunksCount() == before, "查询未加载区块不触发加载");
            chunk.addReferenceForStructure(structure.value(), distant.pack());
            require(!ServerStructures.scanLoaded(level, chunk.getPos()).complete(), "未加载的引用留待后续处理");
            require(level.getChunkSource().getLoadedChunksCount() == before, "解析缺失引用不触发加载");
            chunk.getReferencesForStructure(structure.value()).remove(distant.pack());
            LOGGER.info("结构验收通过 {} {}", kind, record.position());
        } catch (RuntimeException e) {
            finished = true;
            LOGGER.error("Structure server acceptance FAILED at type {}", index, e);
            event.getServer().halt(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
