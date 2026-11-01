package dev.dubhe.map.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/// 路径点序列化与可见性过滤测试。
class WaypointTest {
    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;

    @Test
    void roundTripPreservesAllFields() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Base", new BlockPos(10, 64, -20),
                OVERWORLD, 0xFF00FF00, "home", true, 300, false, "Aleeve", true);

        CompoundTag tag = original.save();
        Waypoint restored = Waypoint.load(tag, WaypointTest::resolve);

        Assertions.assertEquals(original.id(), restored.id());
        Assertions.assertEquals("Base", restored.name());
        Assertions.assertEquals(new BlockPos(10, 64, -20), restored.pos());
        Assertions.assertEquals(OVERWORLD, restored.dimension());
        Assertions.assertEquals(0xFF00FF00, restored.colorArgb());
        Assertions.assertEquals("home", restored.icon());
        Assertions.assertTrue(restored.enabled());
        Assertions.assertEquals(300, restored.visibleDistance());
        Assertions.assertFalse(restored.deathPoint());
        Assertions.assertEquals("Aleeve", restored.sourcePlayer());
        Assertions.assertTrue(restored.shareable(), "私密标记必须随序列化保留");
    }

    @Test
    void deathPointFlagSurvivesSerialization() {
        Waypoint death = Waypoint.deathPoint(new BlockPos(0, 70, 0), OVERWORLD);
        Waypoint restored = Waypoint.load(death.save(), WaypointTest::resolve);
        Assertions.assertTrue(restored.deathPoint());
        Assertions.assertEquals("death", restored.icon());
    }

    @Test
    void listSerializationKeepsAllEntries() {
        List<Waypoint> source = List.of(
                Waypoint.create("A", new BlockPos(1, 2, 3), OVERWORLD, 0xFFFFFFFF),
                Waypoint.create("B", new BlockPos(4, 5, 6), OVERWORLD, 0xFFFF0000));
        ListTag list = Waypoint.saveAll(source);
        List<Waypoint> restored = Waypoint.loadAll(list, WaypointTest::resolve);

        Assertions.assertEquals(2, restored.size());
        Assertions.assertEquals("A", restored.get(0).name());
        Assertions.assertEquals("B", restored.get(1).name());
    }

    @Test
    void unknownDimensionFallsBackToOverworld() {
        CompoundTag tag = Waypoint.create("X", BlockPos.ZERO, OVERWORLD, 0xFFFFFFFF).save();
        tag.putString("dim", "not a valid id!");
        Waypoint restored = Waypoint.load(tag, WaypointTest::resolve);
        Assertions.assertEquals(OVERWORLD, restored.dimension());
    }

    private static ResourceKey<Level> resolve(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, id);
    }

    @Test
    void withShareableKeepsOtherFields() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Secret", new BlockPos(1, 70, 2),
                OVERWORLD, 0xFF123456, "default", true, 200, false, "Aleeve", true);
        Waypoint priv = original.withShareable(false);

        Assertions.assertFalse(priv.shareable(), "切换后应为私密");
        Assertions.assertEquals(original.id(), priv.id());
        Assertions.assertEquals(original.name(), priv.name());
        Assertions.assertEquals(original.pos(), priv.pos());
        Assertions.assertEquals(original.colorArgb(), priv.colorArgb());
        Assertions.assertEquals(original.visibleDistance(), priv.visibleDistance());
        Assertions.assertEquals(original.sourcePlayer(), priv.sourcePlayer());
    }

    @Test
    void withDetailsUpdatesEditableFieldsAndPreservesIdentity() {
        Waypoint original = new Waypoint(UUID.randomUUID(), "Old", new BlockPos(1, 70, 2),
                OVERWORLD, 0xFF123456, "custom", true, 200, true, "Aleeve", true);
        Waypoint edited = original.withDetails("New", new BlockPos(9, 80, -3),
                0xFFABCDEF, 500, false, false);

        Assertions.assertEquals(original.id(), edited.id());
        Assertions.assertEquals(OVERWORLD, edited.dimension());
        Assertions.assertEquals("custom", edited.icon());
        Assertions.assertTrue(edited.deathPoint());
        Assertions.assertEquals("Aleeve", edited.sourcePlayer());
        Assertions.assertEquals("New", edited.name());
        Assertions.assertEquals(new BlockPos(9, 80, -3), edited.pos());
        Assertions.assertEquals(0xFFABCDEF, edited.colorArgb());
        Assertions.assertEquals(500, edited.visibleDistance());
        Assertions.assertFalse(edited.enabled());
        Assertions.assertFalse(edited.shareable());
    }

    @Test
    void malformedEntryIsSkipped() {
        // 单条记录损坏（id 非法）不应让整个文件加载失败，其余路径点仍需可用。
        ListTag list = new ListTag();
        CompoundTag broken = new CompoundTag();
        broken.putString("id", "not-a-uuid");
        broken.putString("name", "broken");
        list.add(broken);
        CompoundTag good = new CompoundTag();
        good.putString("id", UUID.randomUUID().toString());
        good.putString("name", "good");
        list.add(good);

        List<Waypoint> loaded = Waypoint.loadAll(list, WaypointTest::resolve);
        Assertions.assertEquals(1, loaded.size(), "损坏条目应被跳过");
        Assertions.assertEquals("good", loaded.get(0).name());
    }
}
