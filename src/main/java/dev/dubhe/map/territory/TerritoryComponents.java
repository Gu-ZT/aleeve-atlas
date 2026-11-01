package dev.dubhe.map.territory;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 领地区块的连通分量判定。
///
/// 连接准则为 8 邻域（摩尔邻域）：正交相邻或对角相接均视为连通。
/// 拆分后每个连通分量各自独立计算轮廓与名称标签。
public final class TerritoryComponents {
    private static final int[][] NEIGHBOURS = {
        { -1, -1 }, { 0, -1 }, { 1, -1 },
        { -1, 0 }, { 1, 0 },
        { -1, 1 }, { 0, 1 }, { 1, 1 }
    };

    private TerritoryComponents() {
    }

    /// 把区块集合拆分为若干 8 邻域连通分量。
    ///
    /// 返回分量的顺序按其中最小 `(x, z)` 区块稳定排序，保证重算结果可复现。
    public static List<Set<ChunkPos>> split(Set<ChunkPos> chunks) {
        Set<ChunkPos> remaining = new HashSet<>(chunks);
        List<Set<ChunkPos>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            ChunkPos seed = remaining.iterator().next();
            Set<ChunkPos> component = new HashSet<>();
            Deque<ChunkPos> queue = new ArrayDeque<>();
            queue.add(seed);
            remaining.remove(seed);
            while (!queue.isEmpty()) {
                ChunkPos current = queue.poll();
                component.add(current);
                for (int[] offset : NEIGHBOURS) {
                    ChunkPos neighbour = new ChunkPos(current.x() + offset[0], current.z() + offset[1]);
                    if (remaining.remove(neighbour)) {
                        queue.add(neighbour);
                    }
                }
            }
            components.add(component);
        }
        components.sort((left, right) -> {
            ChunkPos a = minOf(left);
            ChunkPos b = minOf(right);
            int byX = Integer.compare(a.x(), b.x());
            return byX != 0 ? byX : Integer.compare(a.z(), b.z());
        });
        return components;
    }

    /// 分量中坐标最小（先 x 后 z）的区块，用于稳定排序。
    public static ChunkPos minOf(Set<ChunkPos> component) {
        ChunkPos min = null;
        for (ChunkPos pos : component) {
            if (min == null || pos.x() < min.x() || (pos.x() == min.x() && pos.z() < min.z())) {
                min = pos;
            }
        }
        return min;
    }
}
