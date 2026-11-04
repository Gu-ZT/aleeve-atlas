# 客户端配置与模组资源

按 `M` 打开客户端设置。客户端配置由 `AleeveAtlasClientConfig` 定义，包含小地图形状、旋转、缩放、显示、光照、洞穴地形、坐标、环境信息、屏幕位置、尺寸，以及雷达和各类实体的显示选项。

路径点保存在 `<游戏目录>/config/aleeve_atlas-waypoints.json`。备份此文件可保留路径点；修改前请退出游戏。

## 模组图标

模组列表与 README 共用 `src/main/resources/pack.png`，图片尺寸为 256×256。构建时将图片复制到 JAR 根目录，`neoforge.mods.toml` 通过 `logoFile = "pack.png"` 引用，并设置 `logoBlur = false`，使缩放后的像素边缘保持清晰。
