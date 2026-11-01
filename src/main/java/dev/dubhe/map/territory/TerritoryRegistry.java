package dev.dubhe.map.territory;

import dev.dubhe.map.api.territory.BorderStyle;
import dev.dubhe.map.api.territory.TerritoryGroup;
import dev.dubhe.map.api.territory.TerritoryOverlayApi;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// 领地覆层注册中心。
///
/// 维护领地注册表与每个连通分量的重算缓存；局部区块变更只重算受影响的领地，
/// 不触发全图重算。
public final class TerritoryRegistry implements TerritoryOverlayApi {
    /// 单个领地某维度的分量缓存。
    private final Map<String, CachedTerritory> cache = new HashMap<>();
    private final Map<String, TerritoryGroup> territories = new LinkedHashMap<>();
    /// 加权极点的重心牵引权重 λ（由配置注入）。
    private float anchorLambda = AnchorSelector.DEFAULT_LAMBDA;

    @Override
    public void upsertTerritory(TerritoryGroup territory) {
        this.territories.put(territory.territoryId(), territory);
        this.cache.remove(territory.territoryId());
        this.recompute(territory);
    }

    @Override
    public void patchChunks(ResourceKey<Level> dimension, String territoryId,
            Set<ChunkPos> added, Set<ChunkPos> removed) {
        TerritoryGroup existing = this.territories.get(territoryId);
        if (existing == null || !existing.dimension().equals(dimension)) {
            return;
        }
        Set<ChunkPos> chunks = new HashSet<>(existing.chunks());
        chunks.removeAll(removed);
        chunks.addAll(added);
        TerritoryGroup patched = new PatchedTerritory(existing, chunks);
        this.territories.put(territoryId, patched);

        CachedTerritory previous = this.cache.get(territoryId);
        if (previous == null) {
            this.recompute(patched);
            return;
        }
        Set<ChunkPos> affected = new HashSet<>(added);
        affected.addAll(removed);
        if (affected.isEmpty()) {
            return;
        }
        Set<ChunkPos> expanded = expandAffected(affected);
        Set<ChunkPos> recalculationChunks = new HashSet<>();
        List<TerritoryComponent> components = new ArrayList<>();
        for (TerritoryComponent component : previous.components()) {
            if (intersects(expanded, component.chunks())) {
                recalculationChunks.addAll(component.chunks());
            } else {
                components.add(component);
            }
        }
        recalculationChunks.addAll(added);
        recalculationChunks.removeAll(removed);
        recalculationChunks.retainAll(chunks);
        components.addAll(this.computeComponents(recalculationChunks));
        components.sort(TerritoryRegistry::compareComponents);
        this.cache.put(territoryId, new CachedTerritory(patched, components));
    }

    /// 以 8 邻域扩展受影响区域，覆盖新增合并与删除分裂所需的旧分量。
    private static Set<ChunkPos> expandAffected(Set<ChunkPos> affected) {
        Set<ChunkPos> expanded = new HashSet<>();
        for (ChunkPos center : affected) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    expanded.add(new ChunkPos(center.x() + dx, center.z() + dz));
                }
            }
        }
        return expanded;
    }

    private static boolean intersects(Set<ChunkPos> first, Set<ChunkPos> second) {
        for (ChunkPos pos : first) {
            if (second.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static int compareComponents(TerritoryComponent left, TerritoryComponent right) {
        ChunkPos a = TerritoryComponents.minOf(left.chunks());
        ChunkPos b = TerritoryComponents.minOf(right.chunks());
        int byX = Integer.compare(a.x(), b.x());
        return byX != 0 ? byX : Integer.compare(a.z(), b.z());
    }

    @Override
    public void removeTerritory(String territoryId) {
        this.territories.remove(territoryId);
        this.cache.remove(territoryId);
    }

    @Override
    public Set<TerritoryGroup> registeredTerritories() {
        return Set.copyOf(this.territories.values());
    }

    /// 该维度下按 zOrder 升序排列的已注册领地。
    public List<TerritoryGroup> orderedForDimension(ResourceKey<Level> dimension) {
        List<TerritoryGroup> result = new ArrayList<>();
        for (TerritoryGroup territory : this.territories.values()) {
            if (territory.dimension().equals(dimension)) {
                result.add(territory);
            }
        }
        result.sort(Comparator.comparingInt(TerritoryGroup::zOrder));
        return result;
    }

    /// 查询某区块命中的全部领地，按 zOrder 从高到低。
    public List<TerritoryGroup> hitAt(ResourceKey<Level> dimension, ChunkPos pos) {
        List<TerritoryGroup> hits = new ArrayList<>();
        for (TerritoryGroup territory : this.territories.values()) {
            if (territory.dimension().equals(dimension) && territory.chunks().contains(pos)) {
                hits.add(territory);
            }
        }
        hits.sort(Comparator.comparingInt(TerritoryGroup::zOrder).reversed());
        return hits;
    }

    /// 某领地的分量缓存（含锚点）；未注册返回 {@code null}。
    public CachedTerritory cached(String territoryId) {
        return this.cache.get(territoryId);
    }

    /// 设置锚点算法的重心牵引权重。
    public void setAnchorLambda(float lambda) {
        this.anchorLambda = lambda;
        this.cache.clear();
        for (TerritoryGroup territory : this.territories.values()) {
            this.recompute(territory);
        }
    }

    public void clear() {
        this.territories.clear();
        this.cache.clear();
    }

    private void recompute(TerritoryGroup territory) {
        this.cache.put(territory.territoryId(),
                new CachedTerritory(territory, this.computeComponents(territory.chunks())));
    }

    /// 仅计算指定区块集合的分量与锚点，供局部更新复用。
    private List<TerritoryComponent> computeComponents(Set<ChunkPos> chunks) {
        List<Set<ChunkPos>> components = TerritoryComponents.split(chunks);
        List<TerritoryComponent> computed = new ArrayList<>(components.size());
        float lambda = this.anchorLambda;
        for (Set<ChunkPos> component : components) {
            ChunkPos anchor = AnchorSelector.select(component, lambda);
            computed.add(new TerritoryComponent(Set.copyOf(component), anchor));
        }
        return computed;
    }

    /// 单个连通分量：区块集合与其名称锚点。
    public record TerritoryComponent(Set<ChunkPos> chunks, ChunkPos anchor) {
    }

    /// 领地及其连通分量缓存。
    public record CachedTerritory(TerritoryGroup territory, List<TerritoryComponent> components) {
    }

    /// 局部区块更新后的不可变包装。
    private record PatchedTerritory(TerritoryGroup delegate, Set<ChunkPos> chunks)
            implements TerritoryGroup {
        @Override
        public String territoryId() {
            return this.delegate.territoryId();
        }

        @Override
        public ResourceKey<Level> dimension() {
            return this.delegate.dimension();
        }

        @Override
        public Set<ChunkPos> chunks() {
            return this.chunks;
        }

        @Override
        public Component displayName() {
            return this.delegate.displayName();
        }

        @Override
        public int themeColorRgb() {
            return this.delegate.themeColorRgb();
        }

        @Override
        public float fillOpacity() {
            return this.delegate.fillOpacity();
        }

        @Override
        public BorderStyle borderStyle() {
            return this.delegate.borderStyle();
        }

        @Override
        public List<Component> tooltip() {
            return this.delegate.tooltip();
        }

        @Override
        public int zOrder() {
            return this.delegate.zOrder();
        }
    }
}
