package dev.dubhe.map;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AleeveAtlas.MOD_ID)
public class AleeveAtlas {
    public static final String MOD_ID = "aleeve_atlas";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AleeveAtlas(IEventBus modEventBus, ModContainer modContainer) {
    }
}
