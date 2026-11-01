package dev.dubhe.map.structure;

import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/// 结构注册名称与原版地图装饰资源的对应关系。
public enum StructureKind {
    DESERT_VILLAGE("village_desert", "village_desert", "desert_village"),
    PLAINS_VILLAGE("village_plains", "village_plains", "plains_village"),
    SAVANNA_VILLAGE("village_savanna", "village_savanna", "savanna_village"),
    SNOWY_VILLAGE("village_snowy", "village_snowy", "snowy_village"),
    TAIGA_VILLAGE("village_taiga", "village_taiga", "taiga_village"),
    MANSION("mansion", "mansion", "woodland_mansion"),
    MONUMENT("monument", "monument", "ocean_monument"),
    JUNGLE_TEMPLE("jungle_pyramid", "jungle_temple", "jungle_temple"),
    SWAMP_HUT("swamp_hut", "swamp_hut", "swamp_hut"),
    TRIAL_CHAMBERS("trial_chambers", "trial_chambers", "trial_chambers");

    private final ResourceLocation structureId;
    private final ResourceLocation decorationId;
    private final ResourceLocation sprite;

    StructureKind(String structure, String decoration, String sprite) {
        this.structureId = ResourceLocation.withDefaultNamespace(structure);
        this.decorationId = ResourceLocation.withDefaultNamespace(decoration);
        this.sprite = ResourceLocation.withDefaultNamespace(sprite);
    }

    public ResourceLocation structureId() {
        return this.structureId;
    }

    public ResourceLocation decorationId() {
        return this.decorationId;
    }

    public ResourceLocation sprite() {
        return this.sprite;
    }

    public String translationKey() {
        return "aleeve_atlas.structure.type." + this.structureId.getPath();
    }

    public static @Nullable StructureKind fromId(ResourceLocation id) {
        for (StructureKind kind : values()) {
            if (kind.structureId.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
