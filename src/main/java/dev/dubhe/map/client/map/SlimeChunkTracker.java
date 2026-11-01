package dev.dubhe.map.client.map;

import dev.dubhe.map.util.NbtValues;
import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.storage.AtlasPaths;
import dev.dubhe.map.storage.ServerKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// 按手动配置、主机存档和 `/seed` 返回结果解析史莱姆区块。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class SlimeChunkTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/slimechunks");
    private static final long SLIME_SALT = 987234911L;
    private static final Pattern SEED_PATTERN = Pattern.compile("-?\\d+");
    private static final Map<String, Long> LEARNED_SEEDS = new ConcurrentHashMap<>();
    private static final Set<String> LOADED_KEYS = ConcurrentHashMap.newKeySet();

    private SlimeChunkTracker() {
    }

    /// 手动种子优先，其次读取主机存档，最后使用管理员执行 `/seed` 的记录。
    public static Long currentSeed() {
        Minecraft minecraft = Minecraft.getInstance();
        String serverKey = ServerKey.resolve(minecraft);
        Long persisted = loadPersistedSeed(minecraft, serverKey);
        if (persisted != null) {
            return persisted;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            long seed = server.getWorldData().worldGenOptions().seed();
            saveSeed(minecraft, serverKey, seed);
            return seed;
        }
        return LEARNED_SEEDS.get(serverKey);
    }

    private static Long loadPersistedSeed(Minecraft minecraft, String serverKey) {
        if (!LOADED_KEYS.add(serverKey)) {
            return LEARNED_SEEDS.get(serverKey);
        }
        Path file = AtlasPaths.serverRoot(minecraft.gameDirectory.toPath(), serverKey)
                .resolve("slime_seed.nbt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            long seed = NbtValues.getLong(tag, "seed", Long.MIN_VALUE);
            if (seed != Long.MIN_VALUE) {
                LEARNED_SEEDS.put(serverKey, seed);
                return seed;
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to read slime chunk seed {}", file, e);
        }
        return null;
    }

    private static void saveSeed(Minecraft minecraft, String serverKey, long seed) {
        Long previous = LEARNED_SEEDS.put(serverKey, seed);
        LOADED_KEYS.add(serverKey);
        if (previous != null && previous == seed) {
            return;
        }
        Path file = AtlasPaths.serverRoot(minecraft.gameDirectory.toPath(), serverKey)
                .resolve("slime_seed.nbt");
        CompoundTag tag = new CompoundTag();
        tag.putLong("seed", seed);
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed(tag, file);
        } catch (IOException e) {
            LOGGER.warn("Failed to write slime chunk seed {}", file, e);
        }
    }

    public static void setSeed(long seed) {
        Minecraft minecraft = Minecraft.getInstance();
        String serverKey = ServerKey.resolve(minecraft);
        saveSeed(minecraft, serverKey, seed);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "aleeve_atlas.message.slime_seed_recorded", seed), true);
        }
    }

    public static boolean isSlimeChunk(long seed, int chunkX, int chunkZ) {
        return WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, seed, SLIME_SALT).nextInt(10) == 0;
    }

    @SubscribeEvent
    public static void onSystemChat(ClientChatReceivedEvent.System event) {
        if (!(event.getMessage().getContents() instanceof TranslatableContents contents)
                || !"commands.seed.success".equals(contents.getKey())
                || contents.getArgs().length == 0) {
            return;
        }
        Object argument = contents.getArgs()[0];
        String response = argument instanceof Component component
                ? component.getString() : String.valueOf(argument);
        Matcher matcher = SEED_PATTERN.matcher(response);
        if (!matcher.find()) {
            LOGGER.warn("Unable to read /seed response: {}", response);
            return;
        }
        try {
            long seed = Long.parseLong(matcher.group());
            SlimeChunkTracker.setSeed(seed);
        } catch (NumberFormatException e) {
            LOGGER.warn("Unable to read /seed response: {}", contents.getArgs()[0], e);
        }
    }
}
