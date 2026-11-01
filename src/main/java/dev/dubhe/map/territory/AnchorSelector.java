package dev.dubhe.map.territory;

import net.minecraft.world.level.ChunkPos;

import java.util.Set;

/// 领地名称标签锚点计算。
///
/// 以最大内接圆距离场为评分基础，并用重心牵引项避免标签缩在偏远角落：
/// `Score(p) = d(p) - λ · Dist(p, Centroid_geo)`。
public final class AnchorSelector {
    /// 默认重心牵引权重。
    public static final float DEFAULT_LAMBDA = 0.4F;

    private AnchorSelector() {
    }

    /// 计算最优点，返回区块坐标；空集合返回 {@code null}。
    public static ChunkPos select(Set<ChunkPos> component, float lambda) {
        if (component.isEmpty()) {
            return null;
        }
        double centroidX = 0.0;
        double centroidZ = 0.0;
        for (ChunkPos pos : component) {
            centroidX += pos.x();
            centroidZ += pos.z();
        }
        centroidX /= component.size();
        centroidZ /= component.size();

        ChunkPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (ChunkPos pos : component) {
            double distanceToBoundary = distanceToBoundary(component, pos);
            double dx = pos.x() - centroidX;
            double dz = pos.z() - centroidZ;
            double distanceToCentroid = Math.sqrt(dx * dx + dz * dz);
            double score = distanceToBoundary - lambda * distanceToCentroid;
            // 得分相同时按 X 最小、再 Z 最小固定选取，保证重算不跳动。
            if (score > bestScore || (score == bestScore && betterTieBreak(pos, best))) {
                best = pos;
                bestScore = score;
            }
        }
        return best;
    }

    /// 区块到最近非领地区块（含外部与内部孔洞边界）的欧氏距离。
    ///
    /// 采用逐圈扩展搜索：当扩展半径已超过当前找到的最小距离时提前结束。
    static double distanceToBoundary(Set<ChunkPos> component, ChunkPos origin) {
        int maxRadius = maxSearchRadius(component);
        double best = Double.MAX_VALUE;
        for (int radius = 1; radius <= maxRadius; radius++) {
            // 搜索环半径为 radius 时，最近距离不可能超过 radius，可提前终止。
            if (radius - 1 >= best) {
                break;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    ChunkPos probe = new ChunkPos(origin.x() + dx, origin.z() + dz);
                    if (!component.contains(probe)) {
                        double distance = Math.sqrt((double) dx * dx + (double) dz * dz);
                        if (distance < best) {
                            best = distance;
                        }
                    }
                }
            }
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    private static int maxSearchRadius(Set<ChunkPos> component) {
        ChunkPos min = TerritoryComponents.minOf(component);
        int max = 1;
        for (ChunkPos pos : component) {
            max = Math.max(max, Math.abs(pos.x() - min.x()) + 1);
            max = Math.max(max, Math.abs(pos.z() - min.z()) + 1);
        }
        return max + 1;
    }

    private static boolean betterTieBreak(ChunkPos candidate, ChunkPos current) {
        if (current == null) {
            return true;
        }
        if (candidate.x() != current.x()) {
            return candidate.x() < current.x();
        }
        return candidate.z() < current.z();
    }
}
