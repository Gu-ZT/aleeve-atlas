package dev.dubhe.map.map;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirtyChunksTest {
    @Test
    void samplingOneLayerDoesNotDiscardUpdatesToTheOtherLayer() {
        DirtyChunks surface = new DirtyChunks();
        DirtyChunks cave = new DirtyChunks();
        long key = ChunkPos.asLong(-23, 170);
        surface.mark(key);
        surface.mark(key);
        cave.mark(key);
        assertEquals(1, surface.size());
        surface.sampled(key);
        assertFalse(surface.contains(key));
        assertTrue(cave.contains(key));
        surface.mark(key);
        assertTrue(surface.contains(key));
    }

    @Test
    void longExplorationRetainsDistantUpdatesAndOnlyConsumesSuccessfulSamples() {
        DirtyChunks dirty = new DirtyChunks();
        // 相邻区块的 Long.hashCode 大量碰撞，过去 Set.copyOf 会在每 tick 放大复制成本。
        for (int z = 994; z < 1506; z++) {
            for (int x = 130; x < 162; x++) {
                dirty.mark(ChunkPos.asLong(x, z));
            }
        }
        for (int tick = 0; tick < 600; tick++) {
            for (int dz = 0; dz < 13; dz++) {
                for (int dx = 0; dx < 13; dx++) {
                    assertTrue(dirty.contains(ChunkPos.asLong(130 + dx, 994 + dz)));
                }
            }
        }
        // 未加载/采样失败的区块仍待更新；本 tick 成功的六个区块分别完成。
        for (int x = 130; x < 136; x++) dirty.sampled(ChunkPos.asLong(x, 994));
        assertEquals(16384 - 6, dirty.size());
        assertFalse(dirty.contains(ChunkPos.asLong(130, 994)));
        assertTrue(dirty.contains(ChunkPos.asLong(136, 994)));
        assertTrue(dirty.contains(ChunkPos.asLong(161, 1505)));
        dirty.clear();
        assertEquals(0, dirty.size());
        assertFalse(dirty.contains(ChunkPos.asLong(161, 1505)));
    }
}
