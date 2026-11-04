<div align="center">

<img src="src/main/resources/pack.png" width="256" height="256" alt="袖里舆图图标">

# 袖里舆图 Aleeve Atlas

**Minecraft 小地图、生物雷达与本地路径点。**

[English](README.md) | 简体中文

</div>

## 模组简介

袖里舆图 Aleeve Atlas 是适用于 Minecraft 1.21.8 的客户端小地图模组，提供地形显示、生物雷达与本地路径点。

## 环境要求与安装

- Minecraft 1.21.8、NeoForge 21.8.53 与 Java 21。
- 将 `build/libs/` 中的主 JAR 放入客户端的 `.minecraft/mods/` 目录。AnvilLib 模块已内嵌于 JAR。

## 功能

- 小地图支持调整形状、旋转、缩放、位置与尺寸。
- 可显示光照、洞穴地形、坐标与环境信息。
- 雷达支持分别显示敌对生物、友好生物、掉落物与玩家。
- 路径点保存在本地，并可显示在世界内。

## 操作与数据

按 `M` 打开设置。开发运行环境中，`B` 打开路径点列表，`N` 打开快速创建路径点界面。本分支仅在开发环境注册这两个路径点快捷键。

路径点保存在 `<游戏目录>/config/aleeve_atlas-waypoints.json`，备份该文件即可保留。配置入口与图标说明见[配置与资源指南](docs/CONFIG.md)。

## 从源码构建

使用 JDK 21 与项目自带的 Gradle wrapper，Linux/macOS 执行 `./gradlew build`，Windows 执行 `gradlew.bat build`。构建产物位于 `build/libs/`。

本分支从 `mavenLocal()` 解析 AnvilLib Config `2.0.0+snapshot.302`，构建前需要在本地 Maven 仓库准备该版本。

执行 `./gradlew runClient` 启动开发客户端，执行 `./gradlew runData` 生成数据。

## 许可证

- 源码：[GNU LGPL 3.0](LICENSE)。
- 美术资源：[保留所有权利](ASSETS_LICENSE)。
