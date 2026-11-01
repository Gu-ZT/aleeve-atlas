package dev.dubhe.map.client.cartography;

import dev.dubhe.map.cartography.BannerMarker;
import dev.dubhe.map.waypoint.Waypoint;
import dev.dubhe.map.waypoint.WaypointStore;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/// 旗帜按所属维度和原始位置去重，保留已导入路径点的个人编辑。
@NullMarked
final class BannerWaypoints {
    private BannerWaypoints() {
    }

    static List<Waypoint> missing(List<BannerMarker> banners, WaypointStore existing) {
        List<Waypoint> result = new ArrayList<>();
        var seen = new HashSet<UUID>();
        for (BannerMarker banner : banners) {
            var pos = banner.pos();
            String identity = "atlas-banner/" + existing.dimension().location() + "/"
                    + pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
            UUID id = UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
            if (existing.get(id).isPresent() || !seen.add(id)) {
                continue;
            }
            String name = banner.name().isBlank()
                    ? Component.translatable("block.minecraft." + banner.color().getName() + "_banner").getString()
                    : banner.name();
            result.add(new Waypoint(id, name, pos, existing.dimension(),
                    0xFF000000 | banner.color().getTextureDiffuseColor(), Waypoint.DEFAULT_ICON,
                    true, Waypoint.DEFAULT_VISIBLE_DISTANCE, false, "", false));
        }
        return result;
    }
}
