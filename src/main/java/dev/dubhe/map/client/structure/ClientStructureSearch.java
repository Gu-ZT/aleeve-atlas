package dev.dubhe.map.client.structure;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.structure.StructureKind;
import dev.dubhe.map.structure.StructurePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/// 查询响应必须匹配当前连接与提交维度，防止旧结果改变新会话。
@EventBusSubscriber(modid = AleeveAtlas.MOD_ID, value = Dist.CLIENT)
public final class ClientStructureSearch {
    private static boolean allowed;
    private static @Nullable UUID pending;
    private static String dimension = "";
    private static long started;
    private static @Nullable Consumer<StructurePayloads.SearchResult> callback;

    private ClientStructureSearch() {
    }

    public static Component text(String key, Object... args) {
        return Component.translatable("aleeve_atlas.structure." + key, args);
    }

    public static boolean allowed() {
        var connection = Minecraft.getInstance().getConnection();
        return allowed && connection != null && connection.hasChannel(StructurePayloads.Search.TYPE)
                && connection.hasChannel(StructurePayloads.SearchResult.TYPE);
    }

    public static boolean busy() {
        return pending != null;
    }

    public static void policy(StructurePayloads.Policy policy, IPayloadContext context) {
        context.enqueueWork(() -> allowed = policy.allowed());
    }

    public static boolean search(StructureKind kind, Consumer<StructurePayloads.SearchResult> done) {
        var level = Minecraft.getInstance().level;
        if (level == null || !allowed() || busy()) {
            return false;
        }
        pending = UUID.randomUUID();
        dimension = level.dimension().location().toString();
        started = System.nanoTime();
        callback = done;
        ClientPacketDistributor.sendToServer(new StructurePayloads.Search(pending, kind));
        return true;
    }

    public static void result(StructurePayloads.SearchResult result, IPayloadContext context) {
        context.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (!result.requestId().equals(pending) || level == null
                    || !dimension.equals(level.dimension().location().toString())) {
                return;
            }
            if (result.record() != null) {
                if (!dimension.equals(result.record().dimension())) {
                    return;
                }
                ClientStructures.store(dimension).put(result.record());
                ClientStructures.flush();
            }
            var done = callback;
            clear();
            if (done != null) {
                done.accept(result);
            }
        });
    }

    public static Component status(StructurePayloads.SearchStatus status) {
        return text("status." + status.name().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (pending == null) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null || !dimension.equals(level.dimension().location().toString())) {
            clear();
        } else if (System.nanoTime() - started > 60_000_000_000L) {
            var done = callback;
            UUID request = pending;
            clear();
            if (done != null) {
                done.accept(new StructurePayloads.SearchResult(request, StructurePayloads.SearchStatus.FAILED, null));
            }
        }
    }

    private static void clear() {
        pending = null;
        callback = null;
        dimension = "";
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        allowed = false;
    }
}
