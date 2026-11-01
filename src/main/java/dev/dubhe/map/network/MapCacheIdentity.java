package dev.dubhe.map.network;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.UUID;

/// 保存服务端主世界对应的客户端地图缓存标识。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class MapCacheIdentity extends SavedData {
    private static final String NAME = AleeveAtlas.MOD_ID + "_map_cache_identity";
    private static final SavedData.Factory<MapCacheIdentity> TYPE = new SavedData.Factory<>(
            MapCacheIdentity::create, (tag, registries) -> new MapCacheIdentity(tag.getString("id")), null);

    private final String id;

    private MapCacheIdentity(String id) {
        this.id = id;
    }

    private static MapCacheIdentity create() {
        MapCacheIdentity identity = new MapCacheIdentity(UUID.randomUUID().toString());
        identity.setDirty();
        return identity;
    }

    public static MapCacheIdentity get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE, NAME);
    }

    /// 首次启动即落盘，专服无玩家登录和单机随后开放 LAN 时也已有稳定标识。
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initialize(event.getServer().overworld().getDataStorage());
    }

    static MapCacheIdentity initialize(DimensionDataStorage storage) {
        MapCacheIdentity identity = storage.computeIfAbsent(TYPE, NAME);
        if (identity.isDirty()) {
            storage.save();
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        }
        return identity;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("id", this.id);
        return tag;
    }

    public String id() {
        return this.id;
    }
}
