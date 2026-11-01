package dev.dubhe.map.ping;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/// 临时标点核心数据结构。
///
/// 包含唯一 ID、发起者信息、精确三维坐标、可选跟随实体、图标类型、高亮颜色与生命周期元数据。
public record MapPingData(
        UUID pingId,
        @Nullable UUID senderId,
        String senderName,
        int teamColorArgb,
        String dimensionId,
        Vec3 position,
        @Nullable UUID targetEntityId,
        PingIcon icon,
        long createdAtMillis,
        long lifetimeMillis) {

    /// 标点图标类型。
    public enum PingIcon {
        DANGER,
        WARNING,
        GENERAL,
        GOTO;

        public Component displayName() {
            return Component.translatable("aleeve_atlas.ping.type."
                    + name().toLowerCase(Locale.ROOT));
        }

        public int colorArgb() {
            return switch (this) {
                case DANGER -> 0xFFFF5555;
                case WARNING -> 0xFFFFAA00;
                case GENERAL -> 0xFF55FFFF;
                case GOTO -> 0xFF55FF55;
            };
        }
    }

    public static final long DEFAULT_LIFETIME_MILLIS = 8000L;

    /// 是否因跟随实体而需要更新位置。
    public boolean followsEntity() {
        return this.targetEntityId != null;
    }

    /// 相对给定时间是否已过期。
    public boolean expired(long nowMillis) {
        return nowMillis - this.createdAtMillis >= this.lifetimeMillis;
    }

    public MapPingData withPosition(Vec3 newPosition) {
        return new MapPingData(this.pingId, this.senderId, this.senderName, this.teamColorArgb, this.dimensionId,
                newPosition, this.targetEntityId, this.icon, this.createdAtMillis, this.lifetimeMillis);
    }

    /// 维度键工具：把维度标识写入数据。
    public static String dimensionId(ResourceKey<Level> dimension) {
        return dimension.location().toString();
    }
}
