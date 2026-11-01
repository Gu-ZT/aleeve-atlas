package dev.dubhe.map.storage;

import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;

/// 数据存储路径布局。
///
/// `<游戏目录>/data/aleeve_atlas/mapdata/<服务器标识或存档名>/<维度ID>/`
/// 保留存档名、UUID 和地址的可读性，只转义文件系统禁止的字符。
public final class AtlasPaths {
    private static final String DATA = "data";
    private static final String ROOT = "aleeve_atlas";
    private static final String MAPDATA = "mapdata";

    private AtlasPaths() {
    }

    /// 地图缓存与路径点的根目录。
    public static Path root(Path gameDir) {
        return gameDir.resolve(DATA).resolve(ROOT);
    }

    /// 单个服务器（或存档）的地图数据根目录。
    public static Path serverRoot(Path gameDir, String serverKey) {
        return root(gameDir).resolve(MAPDATA).resolve(sanitize(serverKey));
    }

    /// 某维度在小地图缓存中的目录；维度以 `ResourceLocation` 标识。
    public static Path dimensionDir(Path gameDir, String serverKey, ResourceLocation dimension) {
        return serverRoot(gameDir, serverKey).resolve(sanitize(dimension.toString().replace(':', '_')));
    }

    /// 使用可逆转义，避免中文名称或不同非法字符都被替换成下划线后发生目录碰撞。
    private static String sanitize(String raw) {
        StringBuilder result = new StringBuilder();
        int trailing = raw.length();
        while (trailing > 0 && (raw.charAt(trailing - 1) == '.' || raw.charAt(trailing - 1) == ' ')) trailing--;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < 32 || "\\/:*?\"<>|%".indexOf(c) >= 0 || i >= trailing) {
                result.append('%').append(String.format("%02X", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.isEmpty() ? "_" : result.toString();
    }
}
