package dev.dubhe.map.territory;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 领地区块的横向路段压缩。
///
/// 大面积领地的区块集合通常按行连续，逐区块 `fill` 会产生大量冗余绘制调用。
/// 本工具把同一 Z 行上连续的 X 区块合并为一段，绘制时每段只需一次填充。
public final class TerritoryRuns {
    private TerritoryRuns() {
    }

    /// 一条横向连续路段：`z` 行上 `[startX, endX]`（含两端）全部属于领地。
    public record Run(int startX, int endX, int z) {
        /// 该段包含的区块数。
        public int length() {
            return this.endX - this.startX + 1;
        }
    }

    /// 把区块集合压缩为横向路段列表。
    ///
    /// 输出按 `z` 升序、再按 `startX` 升序排列，保证绘制顺序稳定。
    public static List<Run> of(Set<ChunkPos> chunks) {
        Map<Integer, List<Integer>> rows = new HashMap<>();
        for (ChunkPos pos : chunks) {
            rows.computeIfAbsent(pos.z(), ignored -> new ArrayList<>()).add(pos.x());
        }
        List<Run> runs = new ArrayList<>();
        List<Integer> sortedZ = new ArrayList<>(rows.keySet());
        sortedZ.sort(Integer::compareTo);
        for (int z : sortedZ) {
            List<Integer> xs = rows.get(z);
            xs.sort(Integer::compareTo);
            int start = xs.get(0);
            int previous = start;
            for (int index = 1; index < xs.size(); index++) {
                int current = xs.get(index);
                if (current == previous + 1) {
                    previous = current;
                    continue;
                }
                runs.add(new Run(start, previous, z));
                start = current;
                previous = current;
            }
            runs.add(new Run(start, previous, z));
        }
        return runs;
    }

    /// 压缩后段数相对原始区块数的比例，用于诊断。
    public static double compressionRatio(Set<ChunkPos> chunks) {
        if (chunks.isEmpty()) {
            return 1.0;
        }
        return (double) of(chunks).size() / chunks.size();
    }
}
