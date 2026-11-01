package dev.dubhe.map.map;

import dev.dubhe.map.network.ChunkDeltaPayload;
import dev.dubhe.map.network.ChunkDigestPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/// 区块像素压缩与摘要/差异分片测试。
class ChunkPixelCodecTest {
    @Test
    void compressRoundTripPreservesPixels() {
        int[] colors = new int[ChunkMapData.PIXELS];
        Random random = new Random(42L);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF000000 | random.nextInt(0xFFFFFF);
        }
        int[] restored = ChunkPixelCodec.decompress(ChunkPixelCodec.compress(colors));
        Assertions.assertArrayEquals(colors, restored);
    }

    @Test
    void chunkPaletteRoundTripPreservesBlockStateAndHeight() {
        ChunkMapData data = new ChunkMapData(4, -9);
        data.setPixel(2, 3, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 72);
        data.setVersion(1234L);
        ChunkMapData restored = ChunkPixelCodec.decompressChunk(4, -9,
                ChunkPixelCodec.compressChunk(data), 1234L);
        Assertions.assertEquals(Blocks.STONE, restored.stateAt(2, 3).state().getBlock());
        Assertions.assertEquals(72, restored.heightAt(2, 3));
        Assertions.assertEquals(1234L, restored.version());
    }

    @Test
    void networkDecoderRejectsExcessiveNbt() throws IOException {
        ChunkMapData data = new ChunkMapData(0, 0);
        data.setPixel(0, 0, ChunkMapData.PixelState.of(Blocks.STONE.defaultBlockState()), 64);
        CompoundTag oversized = data.save();
        oversized.putIntArray("padding", new int[2_000_000]);
        byte[] packed;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(oversized, output);
            packed = output.toByteArray();
        }
        ChunkMapData decoded = ChunkPixelCodec.decompressChunk(0, 0, packed, 0L);
        Assertions.assertTrue(decoded.isEmpty());
    }

    @Test
    void flatTerrainCompressesWell() {
        int[] colors = new int[ChunkMapData.PIXELS];
        Arrays.fill(colors, 0xFF4C8B2B);
        byte[] packed = ChunkPixelCodec.compress(colors);
        // 单调地形应显著小于原始 1 KiB（16×16×4 字节）。
        Assertions.assertTrue(packed.length < 256,
                "expected strong compression, got " + packed.length + " bytes");
    }

    @Test
    void compassWithHeightsRoundTripsBothArrays() {
        int[] colors = new int[ChunkMapData.PIXELS];
        int[] heights = new int[ChunkMapData.PIXELS];
        Random random = new Random(7L);
        for (int i = 0; i < colors.length; i++) {
            colors[i] = 0xFF000000 | random.nextInt(0xFFFFFF);
            heights[i] = 40 + random.nextInt(60);
        }
        int[][] decoded = ChunkPixelCodec.decompressWithHeights(
                ChunkPixelCodec.compress(colors, heights));
        Assertions.assertArrayEquals(colors, decoded[0]);
        Assertions.assertArrayEquals(heights, decoded[1]);
    }

    @Test
    void negativeAndExtremeHeightsRoundTrip() {
        // 1.21.11 世界高度范围含负值（-64 起），高度按 int 大端序写入，必须支持负数与边界值。
        int[] colors = new int[ChunkMapData.PIXELS];
        int[] heights = new int[ChunkMapData.PIXELS];
        int[] samples = { -64, -1, 0, 1, 319, 320, Integer.MIN_VALUE, Integer.MAX_VALUE };
        for (int i = 0; i < heights.length; i++) {
            colors[i] = 0xFF000000 | (i * 31 & 0xFFFFFF);
            heights[i] = samples[i % samples.length];
        }
        int[][] decoded = ChunkPixelCodec.decompressWithHeights(
                ChunkPixelCodec.compress(colors, heights));
        Assertions.assertArrayEquals(colors, decoded[0]);
        Assertions.assertArrayEquals(heights, decoded[1], "负高度与极值必须无损往返");
    }

    @Test
    void digestSplitRespectsPacketLimit() {
        List<ChunkDigestPayload.Entry> entries =
                new ArrayList<>();
        for (int i = 0; i < 3000; i++) {
            entries.add(new ChunkDigestPayload.Entry(i, -i, i));
        }
        List<ChunkDigestPayload> packets =
                ChunkDigestPayload.split("minecraft:overworld", entries, null);
        Assertions.assertEquals(3, packets.size());
        int total = packets.stream().mapToInt(packet -> packet.entries().size()).sum();
        Assertions.assertEquals(3000, total);
        for (var packet : packets) {
            Assertions.assertTrue(packet.entries().size()
                    <= ChunkDigestPayload.MAX_ENTRIES_PER_PACKET);
        }
    }

    @Test
    void digestSplitKeepsBatchIndexesAndEntriesUnique() {
        // 服务端按 batchIndex 聚合分片，批次序号必须连续且不重不漏，否则聚合永远无法收齐。
        List<ChunkDigestPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            entries.add(new ChunkDigestPayload.Entry(i, -i, i));
        }
        List<ChunkDigestPayload> packets =
                ChunkDigestPayload.split("minecraft:overworld", entries, null);
        Set<Integer> seenIndexes = new HashSet<>();
        Set<Integer> seenChunks = new HashSet<>();
        for (var packet : packets) {
            Assertions.assertTrue(seenIndexes.add(packet.batchIndex()), "批次序号不得重复");
            Assertions.assertEquals(packets.size(), packet.batchCount());
            for (var entry : packet.entries()) {
                Assertions.assertTrue(seenChunks.add(entry.chunkX()), "条目不得跨分片重复");
            }
        }
        Assertions.assertEquals(packets.size(), seenIndexes.size(), "批次序号数量应与分片数一致");
        Assertions.assertEquals(2500, seenChunks.size(), "所有条目必须恰好出现一次");
    }

    @Test
    void deltaSplitRespectsPacketLimit() {
        List<ChunkDeltaPayload.ChunkData> chunks =
                new ArrayList<>();
        byte[] payload = new byte[64];
        for (int i = 0; i < 300; i++) {
            chunks.add(new ChunkDeltaPayload.ChunkData(i, i, i, payload));
        }
        List<ChunkDeltaPayload> packets =
                ChunkDeltaPayload.split("minecraft:overworld", chunks, null);
        Assertions.assertEquals(3, packets.size());
        int total = packets.stream().mapToInt(packet -> packet.chunks().size()).sum();
        Assertions.assertEquals(300, total);
    }

    @Test
    void chunkPosPackingIsLossless() {
        ChunkPos pos = new ChunkPos(-1234, 5678);
        long packed = (long) pos.x() << 32 | (pos.z() & 0xFFFFFFFFL);
        int x = (int) (packed >> 32);
        int z = (int) packed;
        Assertions.assertEquals(pos.x(), x);
        Assertions.assertEquals(pos.z(), z);
    }
}
