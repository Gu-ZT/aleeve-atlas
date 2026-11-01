package dev.dubhe.map.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.dubhe.map.AleeveAtlas;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.UUID;

/// 保存服务端主世界对应的客户端地图缓存标识。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID)
public final class MapCacheIdentity extends SavedData {
    private static final Codec<MapCacheIdentity> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(Codec.STRING.fieldOf("id").forGetter(MapCacheIdentity::id))
                    .apply(instance, MapCacheIdentity::new));
    private static final SavedDataType<MapCacheIdentity> TYPE = new SavedDataType<>(
            AleeveAtlas.id("map_cache_identity"), MapCacheIdentity::create, CODEC);

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
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /// 首次启动即落盘，专服无玩家登录和单机随后开放 LAN 时也已有稳定标识。
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        initialize(event.getServer().overworld().getDataStorage());
    }

    static MapCacheIdentity initialize(SavedDataStorage storage) {
        MapCacheIdentity identity = storage.computeIfAbsent(TYPE);
        if (identity.isDirty()) storage.saveAndJoin();
        return identity;
    }

    public String id() {
        return this.id;
    }
}
