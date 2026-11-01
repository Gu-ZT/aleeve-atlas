package dev.dubhe.map.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/// 路径点数据。
///
/// 属性：名称、三维坐标、所属维度、颜色与图标标识、启用状态、可见距离，
/// 以及"是否为死亡点"与跨客户端同步所需的稳定 UUID。
public record Waypoint(
        UUID id,
        String name,
        BlockPos pos,
        ResourceKey<Level> dimension,
        int colorArgb,
        String icon,
        boolean enabled,
        int visibleDistance,
        boolean deathPoint,
        String sourcePlayer,
        boolean shareable) {

    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoint");

    public static final String DEFAULT_ICON = "default";
    /// 死亡点图标标识。
    public static final String DEATH_ICON = "death";
    public static final String DEATH_NAME_KEY = "aleeve_atlas.waypoint.death";
    public static final int DEFAULT_VISIBLE_DISTANCE = -1;

    /// 新建一个普通路径点。
    public static Waypoint create(String name, BlockPos pos, ResourceKey<Level> dimension,
            int colorArgb) {
        return new Waypoint(UUID.randomUUID(), name, pos, dimension, colorArgb, DEFAULT_ICON,
                true, DEFAULT_VISIBLE_DISTANCE, false, "", true);
    }

    /// 新建死亡点。
    public static Waypoint deathPoint(BlockPos pos, ResourceKey<Level> dimension) {
        return new Waypoint(UUID.randomUUID(), DEATH_NAME_KEY, pos, dimension, 0xFFFF5555,
                "death", true, DEFAULT_VISIBLE_DISTANCE, true, "", true);
    }

    /// 玩家可见的路径点名称；兼容旧存档中未翻译的死亡点名称。
    public Component displayName() {
        if (this.deathPoint && ("death".equals(this.name) || DEATH_NAME_KEY.equals(this.name))) {
            return Component.translatable(DEATH_NAME_KEY);
        }
        return Component.literal(this.name);
    }

    public Waypoint withEnabled(boolean newEnabled) {
        return new Waypoint(this.id, this.name, this.pos, this.dimension, this.colorArgb, this.icon, newEnabled,
                this.visibleDistance, this.deathPoint, this.sourcePlayer, this.shareable);
    }

    /// 更新表单可编辑属性，保留 UUID、维度、图标、死亡点与来源信息。
    public Waypoint withDetails(String newName, BlockPos newPos, int newColorArgb,
            int newVisibleDistance, boolean newEnabled, boolean newShareable) {
        return new Waypoint(this.id, newName, newPos, this.dimension, newColorArgb, this.icon, newEnabled,
                newVisibleDistance, this.deathPoint, this.sourcePlayer, newShareable);
    }

    public Waypoint withSource(String source) {
        return new Waypoint(this.id, this.name, this.pos, this.dimension, this.colorArgb, this.icon, this.enabled, this.visibleDistance,
                this.deathPoint, source, this.shareable);
    }

    public Waypoint withShareable(boolean newShareable) {
        return new Waypoint(this.id, this.name, this.pos, this.dimension, this.colorArgb, this.icon, this.enabled, this.visibleDistance,
                this.deathPoint, this.sourcePlayer, newShareable);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", this.id.toString());
        tag.putString("name", this.name);
        tag.putLong("pos", this.pos.asLong());
        tag.putString("dim", this.dimension.location().toString());
        tag.putInt("color", this.colorArgb);
        tag.putString("icon", this.icon);
        tag.putBoolean("enabled", this.enabled);
        tag.putInt("visible", this.visibleDistance);
        tag.putBoolean("death", this.deathPoint);
        tag.putString("source", this.sourcePlayer);
        tag.putBoolean("shareable", this.shareable);
        return tag;
    }

    /// 读取路径点时需要维度键的解析函数。
    public static Waypoint load(CompoundTag tag,
            Function<String, ResourceKey<Level>> dimensionResolver) {
        String dim = tag.getStringOr("dim", "minecraft:overworld");
        return new Waypoint(
                UUID.fromString(tag.getStringOr("id", UUID.randomUUID().toString())),
                tag.getStringOr("name", "waypoint"),
                BlockPos.of(tag.getLongOr("pos", 0L)),
                dimensionResolver.apply(dim),
                tag.getIntOr("color", 0xFFFFFFFF),
                tag.getStringOr("icon", DEFAULT_ICON),
                tag.getBooleanOr("enabled", true),
                tag.getIntOr("visible", DEFAULT_VISIBLE_DISTANCE),
                tag.getBooleanOr("death", false),
                tag.getStringOr("source", ""),
                tag.getBooleanOr("shareable", true));
    }

    /// 序列化路径点列表。
    public static ListTag saveAll(Iterable<Waypoint> waypoints) {
        ListTag list = new ListTag();
        for (Waypoint waypoint : waypoints) {
            list.add(waypoint.save());
        }
        return list;
    }

    /// 从 `ListTag` 读取路径点列表。
    public static List<Waypoint> loadAll(ListTag list,
            Function<String, ResourceKey<Level>> dimensionResolver) {
        List<Waypoint> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Tag tag = list.get(i);
            if (!(tag instanceof CompoundTag compound)) {
                continue;
            }
            // 单条记录损坏不应让整个文件加载失败：跳过并记录，其余路径点仍可用。
            try {
                result.add(load(compound, dimensionResolver));
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping malformed waypoint at index {}", i, e);
            }
        }
        return result;
    }
}
