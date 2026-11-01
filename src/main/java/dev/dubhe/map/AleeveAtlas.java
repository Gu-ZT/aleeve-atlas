package dev.dubhe.map;

import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.dubhe.map.config.AtlasClientConfigs;
import dev.dubhe.map.config.AtlasServerConfig;
import dev.dubhe.map.team.AtlasIntegrations;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

@Mod(AleeveAtlas.MOD_ID)
public final class AleeveAtlas {
    public static final String MOD_ID = "aleeve_atlas";

    /// 网络协议版本，握手包据此判定双端兼容性。
    public static final String PROTOCOL_VERSION = "5";

    /// 服务端配置。在 `@Mod` 类加载时注册，此时 `ModList` 已就绪。
    public static final AtlasServerConfig CONFIG =
            ConfigManager.register(MOD_ID, AtlasServerConfig::new);

    public AleeveAtlas(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLEnvironment.dist.isClient()) {
            // 客户端配置只在物理客户端加载，保证专用服务端不解析客户端配置类。
            AtlasClientConfigs.load();
        }
        // 扫描并加载可选模组集成（FTB Teams 等），未安装时自动跳过。
        AtlasIntegrations.initialize();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
