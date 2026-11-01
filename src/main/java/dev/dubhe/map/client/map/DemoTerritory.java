package dev.dubhe.map.client.map;

import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.api.territory.BorderStyle;
import dev.dubhe.map.territory.SimpleTerritory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 临时演示领地注入（仅用于验收 AC-8/9/10，验证后应移除）。
///
/// 构造一个含内孔洞与外飞地的领地，覆盖连通分量拆分、轮廓描边与标签锚点场景。
final class DemoTerritory {
    private static final String ID = "demo_territory";
    private static final String OVERLAY_ID = "demo_territory_overlay";

    private DemoTerritory() {
    }

    static void registerIfDebugging() {
        // 仅在显式开启时注入：系统属性或环境变量均可（环境变量不受 Gradle 配置缓存影响）。
        if (!Boolean.getBoolean("aleeve_atlas.demo_territory")
                && System.getenv("ALEEVE_ATLAS_DEMO_TERRITORY") == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int baseX = minecraft.player.chunkPosition().x();
        int baseZ = minecraft.player.chunkPosition().z();
        Set<ChunkPos> chunks = new HashSet<>();
        // 主区域 9×9，中央挖空 3×3 形成环（验证孔洞内圈描边）。
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                chunks.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.remove(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        // 分离飞地（验证独立分量与独立标签）。
        chunks.add(new ChunkPos(baseX + 8, baseZ + 8));
        chunks.add(new ChunkPos(baseX + 8, baseZ + 9));
        // 对角相接飞地（8 邻域下仍属同一分量）。
        chunks.add(new ChunkPos(baseX + 5, baseZ + 5));

        AtlasTerritory.client().upsertTerritory(SimpleTerritory.of(ID,
                minecraft.level.dimension(), chunks,
                Component.translatable("aleeve_atlas.territory.demo_name"), 0x33AAFF));

        // 叠加一个 zOrder 更高的重叠领地，用于验证边框取舍。
        Set<ChunkPos> overlay = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                overlay.add(new ChunkPos(baseX + dx, baseZ + dz));
            }
        }
        AtlasTerritory.client().upsertTerritory(new SimpleTerritory(OVERLAY_ID,
                minecraft.level.dimension(), overlay,
                Component.translatable("aleeve_atlas.territory.demo_overlay_name"), 0xFFAA33,
                0.55F, new BorderStyle(0xFFFFAA33, 1.0F, true),
                List.of(), 10));
    }
}
