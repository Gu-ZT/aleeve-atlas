package dev.dubhe.map.cartography;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class ServerCartography {
    private static final Set<String> TARGETS = Set.of("red_x", "target_x", "target_point", "mansion", "monument",
            "village_desert", "village_plains", "village_savanna", "village_snowy", "village_taiga",
            "jungle_temple", "swamp_hut", "trial_chambers");
    private static final Map<UUID, LinkedHashMap<UUID, CartographyPayloads.Result>> RESULTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private ServerCartography() { }

    private static boolean begin(ServerPlayer player, UUID id) {
        var previous = RESULTS.getOrDefault(player.getUUID(), new LinkedHashMap<>()).get(id);
        if (previous != null) {
            sendResult(player, previous);
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_REQUEST.put(player.getUUID(), now);
        if (last != null && now - last < 250) {
            finish(player, new CartographyPayloads.Result(id, "busy", -1, null));
            return false;
        }
        return true;
    }

    private static void finish(ServerPlayer player, CartographyPayloads.Result result) {
        remember(player, result);
        sendResult(player, result);
    }

    private static void sendResult(ServerPlayer player, CartographyPayloads.Result result) {
        PacketDistributor.sendToPlayer(player,
                result.response(player.connection.hasChannel(CartographyPayloads.BannerResult.TYPE)));
    }

    private static CartographyPayloads.Result remember(ServerPlayer player, CartographyPayloads.Result result) {
        var cache = RESULTS.computeIfAbsent(player.getUUID(), key -> new LinkedHashMap<>());
        cache.put(result.requestId(), result);
        while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
        return result;
    }

    private static boolean validHand(ServerPlayer player, InteractionHand hand, int slot) {
        return player.isAlive() && slot >= 0 && slot < 9 && player.getInventory().getSelectedSlot() == slot;
    }

    public static void importMap(CartographyPayloads.ImportRequest request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !begin(player, request.requestId())) {
                return;
            }
            finish(player, executeImport(player, request));
        });
    }

    static CartographyPayloads.Result executeImport(ServerPlayer player, CartographyPayloads.ImportRequest request) {
        ItemStack held = player.getItemInHand(request.hand());
        var mapId = held.get(DataComponents.MAP_ID);
        if (!validHand(player, request.hand(), request.slot()) || !held.is(Items.FILLED_MAP)
                || mapId == null || mapId.id() != request.mapId()) {
            return new CartographyPayloads.Result(request.requestId(), "changed", -1, null);
        }
        var data = MapItem.getSavedData(mapId, player.level());
        if (data == null) {
            return new CartographyPayloads.Result(request.requestId(), "missing", -1, null);
        }
        try {
            var targets = new ArrayList<MapSnapshot.Target>();
            for (var target : held.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY).decorations().values()) {
                var key = target.type().unwrapKey().orElse(null);
                if (key != null && key.location().getNamespace().equals("minecraft")
                        && TARGETS.contains(key.location().getPath()) && Double.isFinite(target.x())
                        && Double.isFinite(target.z()) && Math.abs(target.x()) <= 30_000_000 && Math.abs(target.z()) <= 30_000_000
                        && targets.size() < MapSnapshot.MAX_TARGETS) {
                    targets.add(new MapSnapshot.Target(key.location().toString(), (int) Math.floor(target.x()), (int) Math.floor(target.z())));
                }
            }
            var mapLevel = player.level().getServer().getLevel(data.dimension);
            var snapshot = new MapSnapshot(mapId.id(), data.dimension.location().toString(), data.centerX,
                    data.centerZ, data.scale, mapLevel == null ? 63 : mapLevel.getSeaLevel(), data.colors, targets, 0);
            return new CartographyPayloads.Result(request.requestId(), "imported", mapId.id(), snapshot,
                    BannerMarker.fromMap(data));
        } catch (IllegalArgumentException e) {
            org.slf4j.LoggerFactory.getLogger(ServerCartography.class).warn(
                    "Cannot import map {} for player {} in dimension {}", mapId.id(), player.getUUID(), data.dimension, e);
            return new CartographyPayloads.Result(request.requestId(), "invalid", -1, null);
        }
    }

    public static void craftMap(CartographyPayloads.CraftRequest request, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !begin(player, request.requestId())) return;
            finish(player, executeCraft(player, request));
        });
    }

    static CartographyPayloads.Result executeCraft(ServerPlayer player, CartographyPayloads.CraftRequest request) {
        var cached = RESULTS.getOrDefault(player.getUUID(), new LinkedHashMap<>()).get(request.requestId());
        if (cached != null) return cached;
        String error = validate(player, request);
        if (error != null) {
            return remember(player, new CartographyPayloads.Result(request.requestId(), error, -1, null));
        }
        var level = player.level();
        // 完成所有可失败的输入校验后才分配 ID、保存并结算材料。
        var data = MapItemSavedData.createFresh(request.centerX(), request.centerZ(), (byte) 0, true, false, level.dimension());
        System.arraycopy(request.colors(), 0, data.colors, 0, MapSnapshot.PIXELS);
        if (request.locked()) data = data.locked();
        var mapId = level.getFreeMapId();
        level.setMapData(mapId, data);
        data.setDirty();
        ItemStack result = new ItemStack(Items.FILLED_MAP);
        result.set(DataComponents.MAP_ID, mapId);
        if (!player.getAbilities().instabuild) {
            if (request.locked()) player.getInventory().getItem(Cartography.glassSlot(player)).shrink(1);
            player.getItemInHand(request.hand()).shrink(1);
        }
        if (player.getItemInHand(request.hand()).isEmpty()) player.setItemInHand(request.hand(), result);
        else if (!player.getInventory().add(result)) player.drop(result, false);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.awardStat(Stats.ITEM_USED.get(Items.MAP));
        level.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1, 1);
        return remember(player, new CartographyPayloads.Result(request.requestId(), "crafted", mapId.id(), null));
    }

    static String validate(ServerPlayer player, CartographyPayloads.CraftRequest request) {
        if (!validHand(player, request.hand(), request.slot()) || !player.getItemInHand(request.hand()).is(Items.MAP)
                || !player.level().dimension().location().toString().equals(request.dimension())) return "changed";
        if (!Cartography.validRegion(player.level(), request.centerX(), request.centerZ())) return "region";
        try { MapSnapshot.validateColors(request.colors()); } catch (IllegalArgumentException e) { return "invalid"; }
        if (!Cartography.hasContent(request.colors())) return "empty";
        if (request.locked() && !player.getAbilities().instabuild && Cartography.glassSlot(player) < 0) return "glass_missing";
        return null;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        RESULTS.remove(event.getEntity().getUUID());
        LAST_REQUEST.remove(event.getEntity().getUUID());
    }
}
