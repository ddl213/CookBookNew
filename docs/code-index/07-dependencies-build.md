# 07 · 已有依赖与构建工具

> 本类盘点状态：已盘点。已核对版本目录、根/模块 Gradle、wrapper、Manifest 与 `scripts/`；版本以仓库文件为源，不按常见版本猜测。`P07-T02` 增量登记 `lib_chat` 的 Room/Robolectric/`CHAT_DATA_SOURCE` 构建配置，并收窄 DEP-003 的“全仓无 Room”旧结论；`P08-T02` 增量登记 `lib_order` 的 `lib_network` 依赖与 `lib_network` 的 `ORDER_DATA_SOURCE` 构建字段。因内容增长，本分类拆为两个子表。

**收录范围：**项目已引入依赖、版本管理入口、Gradle convention、模块构建、构建/自动化脚本与工具链。

**索引原则：**以实际仓库文件为源；写清库用途与版本来源，不照网上常见版本猜测；构建期写入字段修改后必须重新构建。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 版本目录、公共库与工具链](07-dependencies-build/01-versions-core.md) | 版本目录、公共 UI/基础库、Room 骨架、选择器、路由编译、校验脚本、仓库/JDK 配置 | DEP-001～DEP-007 |
| [02 模块构建与网络/数据源配置](07-dependencies-build/02-module-builds.md) | 网络栈与 `apiBaseUrl`/`ORDER_DATA_SOURCE`、认证/关系/菜谱/聊天/订单模块构建、debug 明文 HTTP | DEP-008～DEP-014 |

## 覆盖与缺口

- 已覆盖 `gradle/libs.versions.toml`、根与各模块 `build.gradle.kts`、wrapper 与 `gradle.properties`、`AndroidManifest` 明文配置、`scripts/` 下校验脚本；依赖版本均可在仓库文件核对。
- `lib_network` 的 Retrofit 依赖为 `implementation`，不向业务模块传递；业务模块需要接口时依赖 `:lib_network`，不要重复引入。`ORDER_DATA_SOURCE` 与 `apiBaseUrl` 均为构建期常量，默认订单 remote、聊天 debug mock/release remote，两者可在同一次构建中通过不同 Gradle 属性分别覆盖。
- `lib_order` 当前只有数据层与纯 JVM 测试，没有 Fragment/UI、Robolectric 或仪器测试配置；若后续增加订单页面，沿用 ARCH-003 的自动聚合与本表 DEP-014 的模块边界，不新建重复工具库。
- `lib_cookbook` 的 Room 仍是空骨架（DEP-003），不要把 `lib_chat`/`lib_order` 的本地存储能力视为全仓通用 Room 方案；`lib_order` 目前使用 MMKV 保存加密 pending，不引入 Room。
- Gradle 仓库存在带前导单引号的 URL（DEP-007），本机 JDK 路径为绝对路径；换机器或 CI 前需核对。
