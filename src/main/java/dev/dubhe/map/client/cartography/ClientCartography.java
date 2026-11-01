package dev.dubhe.map.client.cartography;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.cartography.BannerMarker;
import dev.dubhe.map.cartography.Cartography;
import dev.dubhe.map.cartography.CartographyPayloads;
import dev.dubhe.map.cartography.ImportedMapStore;
import dev.dubhe.map.cartography.MapSnapshot;
import dev.dubhe.map.client.map.AtlasClientMap;
import dev.dubhe.map.client.map.WorldMapScreen;
import dev.dubhe.map.client.waypoint.ClientWaypoints;
import dev.dubhe.map.storage.AtlasPaths;
import dev.dubhe.map.storage.ServerKey;
import dev.dubhe.map.waypoint.Waypoint;
import dev.dubhe.map.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientCartography {
    private static final Map<String, ImportedMapStore> STORES = new HashMap<>();
    private static String serverKey;
    private static UUID pending;
    private static HeldMap pendingHeld;
    private static Consumer<CartographyPayloads.Result> callback;
    private static Object pendingScreen;
    private static long pendingSince;
    private ClientCartography() { }

    public static boolean available() {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(CartographyPayloads.ImportRequest.TYPE)
                && connection.hasChannel(CartographyPayloads.CraftRequest.TYPE)
                && connection.hasChannel(CartographyPayloads.Result.TYPE);
    }

    public static Component text(String key, Object... args) {
        return Component.translatable("aleeve_atlas.cartography." + key, args);
    }

    public static ImportedMapStore store(String dimension) {
        var mc = Minecraft.getInstance();
        String key = ServerKey.resolveMapCache(mc);
        if (!key.equals(serverKey)) { STORES.clear(); serverKey = key; }
        return STORES.computeIfAbsent(dimension, d -> new ImportedMapStore(
                AtlasPaths.dimensionDir(mc.gameDirectory.toPath(), key, ResourceLocation.parse(d)).resolve("imported_maps"), d));
    }

    public static ImportedMapStore currentStore() {
        var mc = Minecraft.getInstance();
        var level = mc == null ? null : mc.level;
        return level == null ? null : store(level.dimension().location().toString());
    }

    public static HeldMap held(Item item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(item)) return new HeldMap(hand, player.getInventory().selected,
                    player.level().dimension().location().toString(), stack.copy());
        }
        return null;
    }

    public record HeldMap(InteractionHand hand, int slot, String dimension, ItemStack original) {
        public boolean valid() {
            var player = Minecraft.getInstance().player;
            return player != null && player.isAlive() && player.getInventory().selected == this.slot
                    && player.level().dimension().location().toString().equals(this.dimension)
                    && ItemStack.isSameItemSameComponents(player.getItemInHand(this.hand), this.original)
                    && player.getItemInHand(this.hand).getCount() == this.original.getCount();
        }
    }

    public static boolean busy() {
        if (pending != null && (Minecraft.getInstance().screen != pendingScreen
                || pendingHeld == null || System.currentTimeMillis() - pendingSince > 15_000)) {
            var done = callback;
            clearPending();
            if (done != null) done.accept(new CartographyPayloads.Result(UUID.randomUUID(), "changed", -1, null));
        }
        return pending != null;
    }

    private static UUID begin(HeldMap held, Consumer<CartographyPayloads.Result> done) {
        if (!available() || busy() || !held.valid()) return null;
        pending = UUID.randomUUID(); pendingHeld = held; callback = done;
        pendingScreen = Minecraft.getInstance().screen; pendingSince = System.currentTimeMillis();
        return pending;
    }

    public static void importMap(WorldMapScreen screen) {
        HeldMap held = held(Items.FILLED_MAP);
        if (held == null) return;
        var id = held.original.get(DataComponents.MAP_ID);
        if (id == null) return;
        UUID request = begin(held, result -> {
            if (result.snapshot() != null && result.status().equals("imported")) {
                try {
                    MapSnapshot snapshot = result.snapshot();
                    store(snapshot.dimension()).put(snapshot);
                    int addedBanners = addTargets(snapshot, result.banners());
                    screen.onMapImported(snapshot);
                    if (!result.banners().isEmpty()) {
                        message("banners_imported", addedBanners);
                    }
                } catch (IOException | RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(ClientCartography.class).warn("Map import failed", e);
                    message("save_failed");
                }
            } else message(result.status());
        });
        if (request != null) PacketDistributor.sendToServer(new CartographyPayloads.ImportRequest(request, held.hand, held.slot, id.id()));
    }

    public static boolean craft(HeldMap held, int x, int z, byte[] colors, boolean locked,
            Consumer<CartographyPayloads.Result> done) {
        UUID request = begin(held, done);
        if (request == null) return false;
        PacketDistributor.sendToServer(new CartographyPayloads.CraftRequest(request, held.hand,
                held.slot, held.dimension, x, z, locked, colors));
        return true;
    }

    public static void handle(CartographyPayloads.Result result, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (pending == null || !pending.equals(result.requestId())) return;
            // 服务端成功制图会先同步物品变化，所以成功响应不能再用旧物品比对。
            var mc = Minecraft.getInstance();
            boolean valid = mc.screen == pendingScreen && mc.player != null
                    && mc.player.level().dimension().location().toString().equals(pendingHeld.dimension)
                    && (result.status().equals("crafted") || pendingHeld.valid());
            var done = callback;
            Object expectedScreen = pendingScreen;
            clearPending();
            if (done != null && mc.screen == expectedScreen) {
                done.accept(valid ? result : new CartographyPayloads.Result(result.requestId(), "changed", -1, null));
            }
        });
    }

    private static int addTargets(MapSnapshot snapshot, List<BannerMarker> banners) {
        var mc = Minecraft.getInstance();
        var dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(snapshot.dimension()));
        ClientWaypoints.flushAllDimensions();
        var existing = WaypointStore.open(mc.gameDirectory.toPath(), ServerKey.resolve(mc), dimension);
        for (var target : snapshot.targets()) {
            UUID id = UUID.nameUUIDFromBytes(("atlas-map/" + snapshot.dimension() + "/" + target.type()
                    + "/" + target.x() + "/" + target.z()).getBytes(StandardCharsets.UTF_8));
            if (existing.get(id).isPresent()) {
                continue;
            }
            int y = snapshot.referenceHeight();
            var manager = AtlasClientMap.manager();
            if (dimension.equals(mc.level.dimension()) && manager != null && manager.currentCache() != null) {
                var data = manager.currentCache().get(Math.floorDiv(target.x(), 16), Math.floorDiv(target.z(), 16)).orElse(null);
                if (data != null && Cartography.terrainPixel(data, target.x() & 15, target.z() & 15) != 0) {
                    y = data.heightAt(target.x() & 15, target.z() & 15);
                }
            }
            String type = ResourceLocation.parse(target.type()).getPath();
            String name = text("target." + type).getString() + " " + text("reference_height").getString();
            ClientWaypoints.upsertInDimension(new Waypoint(id, name, new BlockPos(target.x(), y, target.z()),
                    dimension, 0xFFFFCC55, "treasure", true, -1, false, "", false));
        }
        List<Waypoint> newBanners = BannerWaypoints.missing(banners, existing);
        for (Waypoint waypoint : newBanners) {
            ClientWaypoints.upsertInDimension(waypoint);
        }
        ClientWaypoints.flushAllDimensions();
        return newBanners.size();
    }

    public static byte[] preview(int centerX, int centerZ) {
        byte[] colors = new byte[MapSnapshot.PIXELS];
        var manager = AtlasClientMap.manager();
        var cache = manager == null ? null : manager.currentCache();
        var store = currentStore();
        List<MapSnapshot> maps = store == null ? List.of() : store.intersecting(centerX - 64, centerZ - 64, centerX + 63, centerZ + 63);
        for (int dz = 0; dz < 8; dz++) for (int dx = 0; dx < 8; dx++) {
            int cx = Math.floorDiv(centerX - 64, 16) + dx, cz = Math.floorDiv(centerZ - 64, 16) + dz;
            var data = cache == null ? null : cache.get(cx, cz).orElse(null);
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                byte color = data == null ? 0 : Cartography.terrainPixel(data, x, z);
                if ((Byte.toUnsignedInt(color) >> 2) == 0) color = ImportedMapStore.pixelAt(maps, cx * 16 + x, cz * 16 + z);
                colors[(dz * 16 + z) * 128 + dx * 16 + x] = color;
            }
        }
        return colors;
    }

    public static void message(String key, Object... args) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(text(key, args), false);
    }

    private static void clearPending() { pending = null; pendingHeld = null; callback = null; pendingScreen = null; }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearPending(); STORES.clear(); serverKey = null;
    }
}
