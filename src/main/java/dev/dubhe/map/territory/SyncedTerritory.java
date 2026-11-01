package dev.dubhe.map.territory;

import dev.dubhe.map.api.territory.BorderStyle;
import dev.dubhe.map.api.territory.TerritoryGroup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 可经网络传输的领地对象，包含渲染与悬停所需的全部展示字段。
public record SyncedTerritory(String territoryId, ResourceKey<Level> dimension,
        Set<ChunkPos> chunks, Component displayName, int themeColorRgb, float fillOpacity,
        BorderStyle borderStyle, List<Component> tooltip, int zOrder) implements TerritoryGroup {

    /// 按维度标识构造，供负载解码使用。
    public SyncedTerritory(String territoryId, ResourceLocation dimensionId, Set<ChunkPos> chunks,
            Component displayName, int themeColorRgb, float fillOpacity, BorderStyle borderStyle,
            List<Component> tooltip, int zOrder) {
        this(territoryId, ResourceKey.create(Registries.DIMENSION, dimensionId), chunks,
                displayName, themeColorRgb, fillOpacity, borderStyle, tooltip, zOrder);
    }

    /// 替换区块集合并保留其余字段（分片发送用）。
    public SyncedTerritory withChunks(Set<ChunkPos> newChunks) {
        return new SyncedTerritory(this.territoryId, this.dimension, newChunks, this.displayName, this.themeColorRgb,
                this.fillOpacity, this.borderStyle, this.tooltip, this.zOrder);
    }

    /// 合并另一分片带来的区块，用于客户端重组。
    public SyncedTerritory mergeChunks(Set<ChunkPos> additional) {
        Set<ChunkPos> merged = new HashSet<>(this.chunks);
        merged.addAll(additional);
        return this.withChunks(merged);
    }
}
