package dev.dubhe.map.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.UUID;

/// 本地主机使用实际存档目录名和种子；远程服务器优先使用其持久 UUID，未下发时使用地址。
public final class ServerKey {
    private static volatile UUID remoteCacheId;

    private ServerKey() {
    }

    public static void setRemoteCacheId(String cacheId) {
        try {
            remoteCacheId = cacheId == null ? null : UUID.fromString(cacheId);
        } catch (IllegalArgumentException e) {
            remoteCacheId = null;
        }
    }

    public static void clearRemoteCacheId() {
        remoteCacheId = null;
    }

    /// 地图、路径点和种子共用同一个存档或服务器目录。
    public static String resolve(Minecraft minecraft) {
        if (minecraft == null) return "unknown";
        IntegratedServer integrated = minecraft.getSingleplayerServer();
        // 局域网房主仍属于本地存档，即使收到了自己的握手包也不能切换目录。
        if (integrated != null) {
            return singleplayerKey(integrated.getWorldPath(LevelResource.ROOT),
                    integrated.overworld().getSeed());
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return multiplayerKey(server.ip);
        }
        return "unknown";
    }

    public static String resolveMapCache(Minecraft minecraft) {
        return resolve(minecraft);
    }

    static String multiplayerKey(String address) {
        UUID id = remoteCacheId;
        return "server_" + (id == null ? address.strip() : id.toString());
    }

    static String singleplayerKey(Path worldDirectory, long seed) {
        return "sp_" + levelId(worldDirectory) + "_" + seed;
    }

    /// ROOT 通常以 /./ 结尾；规范化后读取实际存档文件夹，而不是服务器工作目录。
    static String levelId(Path worldDirectory) {
        return worldDirectory.toAbsolutePath().normalize().getFileName().toString();
    }
}
