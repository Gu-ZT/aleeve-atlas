package dev.dubhe.map.api.territory;

import dev.dubhe.map.AleeveAtlas;
import dev.dubhe.map.territory.SyncedTerritory;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// 领地下发负载。
///
/// 单个负载携带一个领地的完整区块集合与展示元数据；区块集合较大时按
/// {@link #MAX_CHUNKS_PER_PACKET} 拆分为多个负载依次发送。
public record TerritorySyncPayload(Action action, SyncedTerritory territory, int batchIndex,
        int batchCount) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/territory");
    /// 单包区块数上限：每区块 8 字节，4096 区块约 32 KiB，远低于 1 MiB 上限。
    public static final int MAX_CHUNKS_PER_PACKET = 4096;
    private static final int MAX_TOOLTIP_LINES = 64;

    public static final Type<TerritorySyncPayload> TYPE =
            new Type<>(AleeveAtlas.id("territory_sync"));

    /// 同步动作。
    public enum Action {
        UPSERT,
        REMOVE
    }

    public static final StreamCodec<FriendlyByteBuf, TerritorySyncPayload> STREAM_CODEC =
            StreamCodec.of(TerritorySyncPayload::write, TerritorySyncPayload::read);

    private static void write(FriendlyByteBuf buf, TerritorySyncPayload payload) {
        buf.writeEnum(payload.action);
        buf.writeInt(payload.batchIndex);
        buf.writeInt(payload.batchCount);
        buf.writeUtf(payload.territory.territoryId(), 128);
        buf.writeUtf(payload.territory.dimension().location().toString(), 256);
        buf.writeInt(payload.territory.themeColorRgb());
        buf.writeFloat(payload.territory.fillOpacity());
        buf.writeInt(payload.territory.borderStyle().colorArgb());
        buf.writeFloat(payload.territory.borderStyle().width());
        buf.writeBoolean(payload.territory.borderStyle().dashed());
        buf.writeVarInt(payload.territory.tooltip().size());
        for (Component line : payload.territory.tooltip()) {
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, line);
        }
        buf.writeInt(payload.territory.zOrder());
        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf,
                payload.territory.displayName());
        buf.writeVarInt(payload.territory.chunks().size());
        for (ChunkPos pos : payload.territory.chunks()) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.z);
        }
    }

    private static TerritorySyncPayload read(FriendlyByteBuf buf) {
        final Action action = buf.readEnum(Action.class);
        final int batchIndex = buf.readInt();
        final int batchCount = buf.readInt();
        final String territoryId = buf.readUtf(128);
        // 维度标识非法时回退主世界，避免解码抛异常导致连接断开。
        String rawDimension = buf.readUtf(256);
        ResourceLocation dimensionId = ResourceLocation.tryParse(rawDimension);
        if (dimensionId == null) {
            LOGGER.warn("Invalid dimension id '{}' in territory payload", rawDimension);
            dimensionId = Level.OVERWORLD.location();
        }
        final int themeColor = buf.readInt();
        final float fillOpacity = buf.readFloat();
        final BorderStyle borderStyle = new BorderStyle(buf.readInt(), buf.readFloat(), buf.readBoolean());
        int tooltipCount = buf.readVarInt();
        if (tooltipCount < 0 || tooltipCount > MAX_TOOLTIP_LINES) {
            throw new DecoderException("Invalid territory tooltip line count: " + tooltipCount);
        }
        List<Component> tooltip = new ArrayList<>(tooltipCount);
        for (int i = 0; i < tooltipCount; i++) {
            tooltip.add(ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf));
        }
        int zOrder = buf.readInt();
        Component displayName = ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf);
        int chunkCount = buf.readVarInt();
        // 上限与拆包阈值一致：超出说明数据损坏或伪造，直接拒绝避免按声明值分配内存。
        if (chunkCount < 0 || chunkCount > MAX_CHUNKS_PER_PACKET) {
            LOGGER.warn("Territory payload declares {} chunks, exceeding the {} limit; rejected",
                    chunkCount, MAX_CHUNKS_PER_PACKET);
            throw new DecoderException(
                    "Invalid territory chunk count: " + chunkCount);
        }
        Set<ChunkPos> chunks = new HashSet<>(Math.max(16, chunkCount));
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(new ChunkPos(buf.readInt(), buf.readInt()));
        }
        SyncedTerritory territory = new SyncedTerritory(territoryId, dimensionId, chunks,
                displayName, themeColor, fillOpacity, borderStyle, tooltip, zOrder);
        return new TerritorySyncPayload(action, territory, batchIndex, batchCount);
    }

    /// 把一个领地拆分为符合体积上限的负载序列。
    public static List<TerritorySyncPayload> split(Action action, SyncedTerritory territory) {
        List<ChunkPos> all = new ArrayList<>(territory.chunks());
        if (all.size() <= MAX_CHUNKS_PER_PACKET) {
            return List.of(new TerritorySyncPayload(action, territory, 0, 1));
        }
        int total = (all.size() + MAX_CHUNKS_PER_PACKET - 1) / MAX_CHUNKS_PER_PACKET;
        List<TerritorySyncPayload> packets = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int from = index * MAX_CHUNKS_PER_PACKET;
            int to = Math.min(all.size(), from + MAX_CHUNKS_PER_PACKET);
            SyncedTerritory slice = territory.withChunks(new HashSet<>(all.subList(from, to)));
            packets.add(new TerritorySyncPayload(action, slice, index, total));
        }
        return packets;
    }

    @Override
    public Type<TerritorySyncPayload> type() {
        return TYPE;
    }
}
