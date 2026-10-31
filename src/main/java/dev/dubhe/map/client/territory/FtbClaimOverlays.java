package dev.dubhe.map.client.territory;

import dev.dubhe.map.api.territory.TerritoryOverlayApi;
import dev.dubhe.map.territory.SimpleTerritory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// 维护客户端获知的认领关系；按队伍与维度合并，每 tick 只重建发生变化的领地。
public final class FtbClaimOverlays {
    private final TerritoryOverlayApi overlays;
    private final Map<Position, Group> owners = new HashMap<>();
    private final Map<Group, Set<ChunkPos>> groups = new HashMap<>();
    private final Map<UUID, Appearance> appearances = new HashMap<>();
    private final Set<Group> dirty = new HashSet<>();

    public FtbClaimOverlays(TerritoryOverlayApi overlays) { this.overlays = overlays; }

    public record Position(ResourceKey<Level> dimension, ChunkPos chunk) { }
    private record Group(UUID team, ResourceKey<Level> dimension) {
        String id() { return "ftbchunks:" + team + "/" + dimension.identifier(); }
    }
    private record Appearance(Component name, int color) { }

    /// 同坐标转移队伍时，同时更新原领地与新领地；空队伍表示取消认领。
    public void update(Position position, UUID team) {
        Group next = team == null ? null : new Group(team, position.dimension());
        Group previous = owners.get(position);
        if (java.util.Objects.equals(previous, next)) return;
        if (previous != null) {
            groups.get(previous).remove(position.chunk());
            dirty.add(previous);
            owners.remove(position);
        }
        if (next != null) {
            owners.put(position, next);
            groups.computeIfAbsent(next, ignored -> new HashSet<>()).add(position.chunk());
            dirty.add(next);
        }
    }

    public void appearance(UUID team, Component name, int color) {
        Appearance next = new Appearance(name.copy(), color & 0xFFFFFF);
        if (!next.equals(appearances.put(team, next))) {
            groups.keySet().stream().filter(group -> group.team.equals(team)).forEach(dirty::add);
        }
    }

    public Set<UUID> teams() {
        Set<UUID> result = new HashSet<>();
        groups.keySet().forEach(group -> result.add(group.team));
        return result;
    }

    public void flush() {
        for (Group group : dirty) {
            Set<ChunkPos> chunks = groups.get(group);
            Appearance appearance = appearances.get(group.team);
            if (chunks == null || chunks.isEmpty()) {
                overlays.removeTerritory(group.id());
                groups.remove(group);
            } else if (appearance != null) {
                overlays.upsertTerritory(SimpleTerritory.of(group.id(), group.dimension, chunks,
                        appearance.name, appearance.color));
            }
        }
        dirty.clear();
        appearances.keySet().retainAll(teams());
    }

    /// 只清理由本适配器注册的领地，不影响其他模组的覆层。
    public void clear() {
        groups.keySet().forEach(group -> overlays.removeTerritory(group.id()));
        owners.clear();
        groups.clear();
        appearances.clear();
        dirty.clear();
    }
}
