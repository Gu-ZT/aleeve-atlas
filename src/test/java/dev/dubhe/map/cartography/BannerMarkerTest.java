package dev.dubhe.map.cartography;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BannerMarkerTest {
    @Test
    void savedMapBannersKeepAllColorsNamesAndExactPositions() {
        List<MapBanner> banners = new ArrayList<>();
        for (DyeColor color : DyeColor.values()) {
            banners.add(new MapBanner(new BlockPos(-128 + color.getId(), -32, 64), color,
                    Optional.of(Component.literal("example " + color.getName()))));
        }
        var data = map(banners);
        var imported = BannerMarker.fromMap(data);
        assertEquals(banners.size(), imported.size());
        for (MapBanner banner : banners) {
            assertTrue(imported.contains(new BannerMarker(banner.pos(), banner.color(), banner.name().orElseThrow().getString())));
        }
        assertEquals(imported, BannerMarker.fromMap(data.locked()));
    }

    @Test
    void unnamedBannersAndLongUnicodeNamesAreHandledForWaypointSharing() {
        String longName = "x".repeat(BannerMarker.MAX_NAME_LENGTH - 1) + "\uD83D\uDEA9" + "example";
        var data = map(List.of(
                new MapBanner(new BlockPos(1, 60, 2), DyeColor.WHITE, Optional.empty()),
                new MapBanner(new BlockPos(2, 60, 2), DyeColor.RED, Optional.of(Component.literal(longName)))));
        var imported = BannerMarker.fromMap(data);
        assertTrue(imported.stream().anyMatch(marker -> marker.name().isEmpty()));
        assertTrue(imported.stream().anyMatch(marker -> marker.name().equals("x".repeat(BannerMarker.MAX_NAME_LENGTH - 1))));
    }

    @Test
    void coordinatesCannotWrapWhenSavedAsWaypointBlockPositions() {
        assertThrows(IllegalArgumentException.class,
                () -> new BannerMarker(new BlockPos(30_000_001, 64, 0), DyeColor.RED, "example"));
        assertThrows(IllegalArgumentException.class,
                () -> new BannerMarker(new BlockPos(0, 2048, 0), DyeColor.RED, "example"));
        var mutable = new BlockPos.MutableBlockPos(2, 3, 4);
        var marker = new BannerMarker(mutable, DyeColor.GREEN, "example");
        mutable.set(5, 6, 7);
        assertEquals(new BlockPos(2, 3, 4), marker.pos());
    }

    private static MapItemSavedData map(List<MapBanner> banners) {
        var data = MapItemSavedData.createFresh(0, 0, (byte) 4, true, false, Level.OVERWORLD);
        var registries = net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty());
        CompoundTag saved = data.save(new CompoundTag(), registries);
        ListTag entries = new ListTag();
        for (MapBanner banner : banners) {
            entries.add(MapBanner.CODEC.encodeStart(NbtOps.INSTANCE, banner).getOrThrow());
        }
        saved.put("banners", entries);
        return MapItemSavedData.load(saved, registries);
    }
}
