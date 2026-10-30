# 袖里舆图 (Aleeve Atlas) 技术与开发者文档

本文档面向参与 **袖里舆图（`aleeve_atlas`）** 开发的贡献者，以及需要接入本模组 API（如标点联动、领地覆盖层）的外部模组开发者。

* **适用版本**：Minecraft `1.21.11`（NeoForge `21.11.x`）
* **需求规格说明**：[袖里舆图（aleeve_atlas）需求规格说明书.md](<袖里舆图 (Aleeve Atlas) 需求规格说明书.md>)

---

## 一、 基础信息与依赖架构

### 1. 命名空间与坐标

* **Mod ID**：`aleeve_atlas`
* **Maven Group / Java Package**：`dev.dubhe.map`

### 2. AnvilLib 模块依赖（`>= 2.0.0`）

本模组强依赖对应 MC 1.21.11 版本的 **AnvilLib**，各子模块职责如下：

* `anvillib_config`（**双端必需**）：负责配置管理与服务端锁定项下发。
* `anvillib_wheel`（**客户端必需**）：提供世界内与全屏地图的 4 向标点轮盘 UI。
* `anvillib_rendering`（**客户端必需**）：提供底层渲染辅助支持。
* `anvillib_util`（**客户端必需**）：提供客户端 Tick 计数等基础工具。
* `anvillib_integration`（*可选*）：用于接入 FTB Teams 等外部组队模组。

---

## 二、 外部模组接入指南 (Public API)

### 1. 临时标点联动 API（如 Ping Wheel 等）

外部模组可通过 `MinimapPingAPI` 将自定义的 3D 标点推送到本模组的地图上，或监听本模组的标点事件以在 3D 世界中渲染自定义特效（如光柱）：

```java
import dev.dubhe.map.api.ping.MapPingCreatedEvent;
import dev.dubhe.map.api.ping.MinimapPingAPI;
import dev.dubhe.map.ping.MapPingData;

// 1. 向袖里舆图推送标点：小地图与世界地图会立即渲染对应的波纹标点
MinimapPingAPI.registerPing(
        position, 
        dimensionId, 
        MapPingData.PingIcon.GENERAL,
        senderId, 
        senderName, 
        teamColor
);

// 2. 监听袖里舆图发起的标点：例如在 3D 视界中生成自定义光柱
NeoForge.EVENT_BUS.addListener((MapPingCreatedEvent event) -> {
    // 处理标点事件...
});

// 3. 控制本模组自带的 3D 世界内标点渲染（需在客户端主线程调用）
// 禁用后仅隐藏 3D 画面中的圆形标点，小地图与世界地图上的标点仍正常显示
MinimapPingAPI.setWorldPingsEnabled(false);
boolean isEnabled = MinimapPingAPI.isWorldPingsEnabled();

```

所有标点 API 均必须在客户端主线程调用。`registerLocalPing(MapPingData)` 仅更新本地显示，不发送网络包；相同 `pingId` 仅保留一个标点，且仅在首次加入时触发 `MapPingCreatedEvent`。`activePings()` 返回只读快照，供外部世界内渲染使用；`removePing(UUID)` 用于撤回本地标点。

外部模组可通过 `setWorldInputEnabled(false)` 关闭内置世界内按键，并通过 `setMapInput(MapPingInput)` 接管地图短按与长按操作。`MapPingInput.ping(Vec3)` 接收鼠标对应的地图世界坐标，`openWheel(Screen, Vec3, Runnable)` 接收原地图界面、目标坐标和关闭回调。轮盘正常关闭时必须返回原地图界面，每次关闭或切换界面时均必须调用关闭回调，以清理按键状态并恢复鼠标位置。传入 `null` 可恢复内置地图操作。

`registerEntityPing(UUID, Vec3, String, PingIcon, UUID, String, int)` 允许指定初始坐标，原有重载方法继续可用。实体跟随仅在标点所属维度内更新，目标未加载时保留最后坐标。`MapPingData.senderId` 允许仅本地显示的外部标点省略发送者 UUID；通过网络注册的标点则必须提供发送者 UUID。

支持 Atlas 联动的 MiddleKeyPing 利用上述接口接管世界内及地图的操作、轮盘和网络广播，将收到的标点通过 `registerLocalPing` 写入 Atlas，并按 MiddleKeyPing 的数量上限与游戏刻有效期同步更新和删除。同时，Atlas 会自动关闭内置的世界内输入与临时标点覆层。

### 2. 领地覆盖层 API（如 FTB Chunks / OPAC 等）

领地或区块认领类模组可通过 `AtlasTerritory` 注册领地数据。本模组会自动计算连通分量、加权极点锚点（用于放置领地名称）、交界描边，并按 `zOrder` 叠加渲染：

```java
import dev.dubhe.map.api.territory.AtlasTerritory;
import dev.dubhe.map.territory.SimpleTerritory;

// 注册或更新客户端领地覆盖层
AtlasTerritory.client().upsertTerritory(
        SimpleTerritory.of("my_territory", Level.OVERWORLD, chunks, name, 0x33AAFF)
);

```

---

## 三、 核心模块与架构概览

| 模块 | 状态 | 核心机制说明 |
| --- | --- | --- |
| **双端自适应** | ✅ | 登录握手检测服务端状态并下发配置锁定项，自动在「纯客户端独立模式」与「双端协同模式」间切换。 |
| **小地图 HUD** | ✅ | 支持圆/方框切换、图标旋转补偿、NSWE 方向标、快捷键缩放、按住 `Z` 居中放大、北向锁定及底部信息栏。 |
| **全屏世界地图** | ✅ | 拖拽平移、滚轮缩放、四角星罗盘、实体雷达、客户端配置入口、双击/右键快捷建点与路径点列表。 |
| **地图采集与缓存** | ✅ | 基于方块状态色板（Palette）采样、按探索增量刷新、支持配色方案热重载、按存档/服务器与维度隔离、区域文件 NBT 持久化。 |
| **地图增量同步** | ✅ | 区块摘要分批上报、版本号比对、差异数据分片流式传输、服务端限速写入。 |
| **队伍适配层** | ✅ | 统一队伍接口，内置原版计分板（Scoreboard）实现，支持外部组队模组注册及配置切换。 |
| **实体雷达** | ✅ | 5 类实体分类、支持 HEX 颜色配置、玩家双层皮肤头像渲染、支持 `Tab` 键联动显示。 |
| **路径点系统** | ✅ | 增删改查表单、本地持久化、地图与 3D 视界标记（75% 不透明度 + 准星注视距离）、死亡点自动记录、同队分享三策略（自动/手动/忽略）。 |
| **临时标点 (Ping)** | ✅ | 支持 3D 视界（200 格内方块/实体）与地图内中键标点、4 类轮盘、常显距离、外部 API 接入。 |
| **领地覆盖层** | ✅ | 公开 API、连通域合并（支持孔洞与飞地）、加权极点标签锚点、交界处描边、`zOrder` 层级排序、进出领地提示。 |
| **可见性判定** | ✅ | 支持 `ALWAYS` / `NEVER` / `VISIBLE_ONLY` 三种策略；采用硬件 GPU 遮挡查询，异常时自动回退至多采样点射线检测（Raycast）。 |
| **国际化 (i18n)** | ✅ | `en_us` 由 Datagen 自动生成，`zh_cn` 人工维护，构建期自动校验双语键集一致性。 |

---

## 四、 关键技术实现细节

### 1. 客户端方块变更检测（Mixin 机制）

* **背景**：NeoForge 1.21.11 移除了客户端层面的方块变更事件，而地图需要在方块发生变化时重新采样对应区块。
* **实现**：`ClientLevel.setBlocksDirty` 是客户端接收方块更新的唯一入口。我们通过 Mixin 在该方法头部（`HEAD`）注入逻辑，将对应区块标记为“待重新采样”（Dirty）。
* **缓存与写盘优化**：
* 该 Mixin 仅声明在 `aleeve_atlas.mixins.json` 的 `client` 段中，专用服务端（Dedicated Server）不会加载。
* **地表层**与**洞穴层**维护两套独立的内存缓存；当区块发生方块变更时，**两套缓存会同时失效清除**，防止玩家切换地表/洞穴模式时读到旧像素。
* 磁盘持久化采用 **200 Tick（10 秒）周期批量写盘**，避免频繁更改方块时产生磁盘 I/O 卡顿。



### 2. 硬件级 GPU 遮挡查询（`VISIBLE_ONLY` 模式）

当实体雷达或标点开启 `VISIBLE_ONLY`（仅可见时显示）策略时，模组会对非同队玩家执行可见性检测：

* **渲染管线**：在不透明地形与实体深度缓冲区（Depth Buffer）绘制完成后，针对目标的代理包围盒（Proxy AABB）发起 OpenGL `GL_SAMPLES_PASSED` 硬件遮挡查询。代理管线会关闭颜色写入（Color Write）、深度写入（Depth Write）与面剔除（Face Culling），仅保留深度测试。
* **异步读取与限流**：每帧最多提交 **64 个**查询目标。查询结果就绪后异步读取；若当前帧结果尚未就绪，则沿用上一帧的有效判定结果。
* **降级回退（Fallback）**：若显卡不支持查询、GL 上下文异常或渲染流程冲突，系统会自动记录原因并无缝降级为**多采样点射线检测（Raycast）**。
* **视觉表现**：小地图、世界地图与临时标点共用同一套可见性判定结果。从未可见的目标不予绘制；曾可见的目标在被墙体遮挡或移出视锥体后，会按配置的时长平滑渐隐。

### 3. 标记渲染与旋转几何

* **图元抗锯齿**：实体圆点、路径点/警告菱形，以及 4 类临时标点的内层符号（危险-叉号、警告-菱形、一般-圆点、前往-箭头）与外圈扩散波纹均采用抗锯齿绘制。当路径点或临时标点超出小地图显示范围时，会在小地图内缩边缘渲染指向目标的平滑三角箭头，所有图标中心均严格约束在地图边框内部。
* **水滴形玩家指示器**：玩家自身指示器为水滴形状，其**旋转锚点严格对齐水滴半圆的圆心**。在“锁定北向”模式下，地图固定、水滴随玩家朝向旋转；在“随视角旋转”模式下，地图旋转、水滴固定朝向屏幕正上方。

### 4. 路径点表单与状态保留

* 路径点管理列表仅展示当前维度的路径点（已禁用的路径点仍保留在列表中以便重新开启），过长的名称与坐标在鼠标悬停时支持横向滚动预览。
* 编辑已有路径点并保存时，表单会严格保留原路径点的 `UUID`、所属维度、自定义图标以及分享来源信息，仅更新修改的字段。

---

## 五、 调试命令与验收环境变量

### 1. 客户端调试与工具命令

主命令为 `/aleeve_atlas`（同时提供无下划线短别名 `/aleeveatlas`）：

| 命令 | 作用说明 |
| --- | --- |
| `/aleeveatlas waypoint export <file>` | 将全部路径点导出至 `<游戏目录>/aleeve_atlas_exports/<file>` |
| `/aleeveatlas waypoint import <file>` | 从上述目录导入并合并路径点数据 |
| `/aleeveatlas waypoint accept|ignore <id>` | 在“手动确认”策略下，接受或忽略队友发来的路径点分享 |
| `/aleeveatlas territory demo|clear` | 生成或清除测试用演示领地（包含内部孔洞与跨区块飞地，用于验收渲染效果） |
| `/aleeveatlas pingdemo` | 在玩家正前方注入一个测试用临时标点，用于验收波纹渲染与动画 |
| `/aleeveatlas perf` | 在聊天栏输出小地图渲染帧耗时统计与标点网络通道诊断信息 |

### 2. 遮挡查询自检环境变量

开发或验收测试时，可通过配置系统环境变量触发内置自检：

* `ALEEVE_ATLAS_OCCLUSION_SELFTEST=1`：在单人世界中自动生成模拟远端玩家，并按顺序自动执行「开阔无遮挡 -> 筑墙遮挡 -> 拆墙恢复可见」全流程验证。
* `ALEEVE_ATLAS_FORCE_RAYCAST=1`：跳过 GPU 硬件遮挡查询，强制使用多采样点射线检测（Raycast）实现。

---

## 六、 本地构建与开发环境

### 1. 环境要求

* **JDK 21**：项目已配置 Gradle Toolchain，支持通过 Foojay 自动下载并解析所需的 JDK 21。
* **Gradle**：无需本地安装，直接使用项目根目录随附的 Gradle Wrapper（`gradlew` / `gradlew.bat`）。

### 2. 常用 Gradle 任务

| 执行命令（Windows 使用 `gradlew.bat`） | 任务说明 |
| --- | --- |
| `./gradlew build` | 构建正式模组 Jar 包（输出至 `build/libs/`），构建过程包含单元测试与双语语言文件键集一致性校验。 |
| `./gradlew runClient` | 启动开发环境 Minecraft 客户端。 |
| `./gradlew runServer` | 启动开发环境 Minecraft 专用服务端。 |
| `./gradlew runData` | 运行 DataGen 数据生成器（自动生成 `en_us.json` 并输出至 `src/generated/resources`）。 |
| `./gradlew test` | 仅运行单元测试与语言文件校验。 |

---

## 七、 开源许可

* **源代码**：遵循 `LGPL-3.0-only` 协议开源（详见 [LICENSE](../LICENSE)）。
* **美术与资源文件**（纹理、模型等）：保留所有权利 All Rights Reserved（详见 [ASSETS_LICENSE](../ASSETS_LICENSE)）。

## 结构记录与发现

结构模块使用独立的可选协议 `structures-1`，涵盖自然发现批次、队伍共享、服务端查询策略以及请求/响应机制。自然发现以 `ChunkWatchEvent.Sent` 为入口，必须通过 `getChunkNow` 访问关联区块和结构起点；缺失的引用会延后处理，严禁使用会触发区块加载的结构遍历方法。客户端仅在关联区块完成采样后才会确认发现。共享记录会直接合并，接收方不执行结构查询。

结构以维度、类型和起始区块确定唯一身份，独立写入地图维度目录下的 `structures.nbt`（格式版本为 1）。记录包含原版定位坐标、结构包围盒、发现来源及个人收藏状态；同一文件还保存当前维度的结构类型关注设置。合并记录时，本人自然发现优先于本人搜索和队伍共享，本人搜索优先于队伍共享，同时保留个人偏好设置。文件采用临时文件写入加原子替换机制，损坏的条目会被跳过并记录其路径与索引。

服务端查询使用 `findNearestMapStructure(level, targets, origin, 100, false)`，参数与原版指令保持一致。客户端请求仅传递结构类型和请求 ID，由服务端确定玩家位置及所属维度，并重新校验配置项、冷却时间和查询占用状态。该入口是唯一允许主动加载结构起点的结构功能。队伍共享沿用地图共享对客户端数据的信任机制，由服务端校验维度、类型、批次数量、坐标和包围盒。

在隔离的专用服务器上设置 `ALEEVE_ATLAS_STRUCTURE_SERVER_TEST=1` 可运行结构验收测试。验收流程会生成十类结构的测试区域，对比原版查找结果，并验证已加载区块扫描、缺失引用延后处理以及普通扫描前后的区块加载数量；测试通过后输出 `Structure server acceptance PASSED` 并自动停止服务器。切勿在正常存档中启用该变量，否则会生成测试区块并导致服务器停止。

## 原版地图桥接实现

原版地图导入和选区制图使用独立的可选协议 `cartography-1`，不影响现有的版本 5 队伍协议。三个新增负载分别处理导入请求、制图请求和结果；客户端通过通道协商检测服务端能力，若服务端不支持则隐藏相关入口。

导入功能会从服务端读取原版地图的中心坐标、维度、缩放比例、像素数据和固定目标。客户端快照独立保存原版色板，渲染时按“实际探索地形 > 高清导入图像 > 较新导入图像”的优先级合成。导入图像不会伪造 `ChunkMapData`，也不会阻断实际的地形采样。由于原版客户端地图数据包缺少真实的中心坐标和维度信息，因此不作为定位依据。

制图预览会冻结 16384 字节的原版色板数据，服务端在验证手持物品位置、维度、原版网格对齐、世界边界、像素格式及所需材料后，生成对应的原版地图。服务端会记录每位玩家最近 64 次的请求结果，遇到重复请求时直接返回原结果，并在玩家退出时清理记录。地图图像源自客户端缓存，服务端不验证像素数据是否对应真实的探索历史。

### 实机验收

在隔离测试存档或隔离专用服务器进程中设置 `ALEEVE_ATLAS_CARTOGRAPHY_TEST=1`（客户端同样需设置该变量）即可启动实机验收。验收夹具会清空测试玩家的背包、配发藏宝图与制图材料并关闭队伍同步，因此切勿在正常游玩存档中启用。验收流程会自动检查地图导入、路径点去重与编辑保留、选区预览、锁定材料扣除以及无玻璃板时的普通制图，测试截图保存至游戏目录下的 `screenshots/`，日志会输出 `Cartography acceptance PASSED` 或具体的失败步骤。

此外，可在隔离的专用服务器上设置 `ALEEVE_ATLAS_CARTOGRAPHY_SERVER_TEST=1`，利用真实服务端地图存档与模拟玩家背包，验证锁定地图制作、普通制图、创造模式免消耗、背包已满时掉落、错误参数拦截以及重复请求处理；日志输出 `Cartography dedicated transaction acceptance PASSED` 后会自动停止专用服务器。
