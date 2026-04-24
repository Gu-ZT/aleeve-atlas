package dev.dubhe.map.data.lang;

import dev.anvilcraft.lib.v2.config.ConfigData;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.client.AleeveAtlasClientConfig;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class AleeveAtlasLanguageProvider extends LanguageProvider {
    public AleeveAtlasLanguageProvider(PackOutput output) {
        super(output, AleeveAtlas.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        ConfigData.readConfigClass(this, AleeveAtlasClientConfig.class);
        this.add("key.categories.aleeve_atlas", "Aleeve Atlas");
        this.add("key.aleeve_atlas.toggle_minimap", "Toggle Minimap");
        this.add("key.aleeve_atlas.toggle_rotation", "Toggle Minimap Rotation");
        this.add("key.aleeve_atlas.toggle_shape", "Toggle Minimap Shape");
        this.add("key.aleeve_atlas.reset_zoom", "Reset Minimap Zoom");
    }
}
