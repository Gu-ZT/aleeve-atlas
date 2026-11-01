package dev.dubhe.map.waypoint;

import dev.dubhe.map.AleeveAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// 路径点同步负载。
///
/// 携带完整路径点字段与操作类型（创建/更新/删除），接收方按自身接收策略处理。
public record WaypointSyncPayload(Action action, Waypoint waypoint) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("aleeve_atlas/waypoints");
    public static final Type<WaypointSyncPayload> TYPE =
            new Type<>(AleeveAtlas.id("waypoint_sync"));

    /// 同步动作。
    public enum Action {
        UPSERT,
        DELETE
    }

    public static final StreamCodec<FriendlyByteBuf, WaypointSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeEnum(payload.action);
                        buf.writeUUID(payload.waypoint.id());
                        buf.writeUtf(payload.waypoint.name(), 128);
                        buf.writeLong(payload.waypoint.pos().asLong());
                        buf.writeUtf(payload.waypoint.dimension().location().toString(), 256);
                        buf.writeInt(payload.waypoint.colorArgb());
                        buf.writeUtf(payload.waypoint.icon(), 32);
                        buf.writeBoolean(payload.waypoint.enabled());
                        buf.writeVarInt(payload.waypoint.visibleDistance());
                        buf.writeBoolean(payload.waypoint.deathPoint());
                        buf.writeUtf(payload.waypoint.sourcePlayer(), 64);
                        buf.writeBoolean(payload.waypoint.shareable());
                    },
                    buf -> {
                        Action action = buf.readEnum(Action.class);
                        var id = buf.readUUID();
                        String name = buf.readUtf(128);
                        BlockPos pos = BlockPos.of(buf.readLong());
                        // 维度标识非法时回退主世界，避免解码抛异常导致连接断开。
                        String rawDimension = buf.readUtf(256);
                        ResourceLocation dimensionId = ResourceLocation.tryParse(rawDimension);
                        if (dimensionId == null) {
                            LOGGER.warn("Invalid dimension id '{}' in waypoint payload",
                                    rawDimension);
                            dimensionId = Level.OVERWORLD.location();
                        }
                        int color = buf.readInt();
                        String icon = buf.readUtf(32);
                        boolean enabled = buf.readBoolean();
                        int visible = buf.readVarInt();
                        boolean death = buf.readBoolean();
                        String source = buf.readUtf(64);
                        boolean shareable = buf.readBoolean();
                        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                                dimensionId);
                        return new WaypointSyncPayload(action,
                                new Waypoint(id, name, pos, dimension, color, icon, enabled,
                                        visible, death, source, shareable));
                    });

    @Override
    public Type<WaypointSyncPayload> type() {
        return TYPE;
    }
}
