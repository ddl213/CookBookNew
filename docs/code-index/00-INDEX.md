# Android 项目可复用能力索引

> **盘点状态：已盘点**：2026-10-22 完成首次全仓只读源码盘点，覆盖当时 `app` 与 `subModules` 下 12 个 `lib_*` 模块；最近同步到 `P10-T02-F01`（2026-10-29：全部通知与菜谱分享通知、通知页订单/菜谱双跳转、菜谱详情预订按钮固定栏、订单条目两行文本、`lib_campaign_common` 单测依赖）。完整盘点状态、增量历史与每次同步依据见 [increment-log.md](increment-log.md)。

## 分类导航

| 分类 | 主要内容 | 入口 | 当前状态 |
| --- | --- | --- | --- |
| 01 项目结构与约定 | 模块、应用入口、导航、基类、路由、我的/收藏/设置页面组 | [01-project-architecture.md](01-project-architecture.md) | 已盘点 |
| 02 工具类与扩展方法 | Utils、扩展、格式/密度/图片处理 | [02-utilities-extensions.md](02-utilities-extensions.md) | 已盘点 |
| 03 自定义 View 与 UI 组件 | 复合 View、Adapter、刷新、弹窗、图片查看 | [03-custom-views-ui.md](03-custom-views-ui.md) | 已盘点 |
| 04 布局、样式及资源 | 主题、颜色、selector、样式、attrs | [04-layouts-styles-res.md](04-layouts-styles-res.md) | 已盘点 |
| 05 网络、存储与异步 | REST 客户端、认证/用户/好友/菜谱（含公开/好友动态）/社交/聊天/通知/订单 API、Repository、分页、上传、系统选图读取、聊天加密 Room 缓存、订单 pending 加密、订单/全部通知适配与分享通知、账号隔离、键值存储 | [05-network-storage.md](05-network-storage.md) | 已盘点 |
| 06 公共业务能力 | 跨页面业务组件与状态（我的资料/收藏/设置入口、聊天页面、通知页订单/菜谱分享双跳转、预订入口与草稿、订单列表/详情及共享纯逻辑均已接线） | [06-business-components.md](06-business-components.md) | 已盘点 |
| 07 依赖与工程工具 | 版本目录、依赖、Gradle、校验脚本 | [07-dependencies-build.md](07-dependencies-build.md) | 已盘点 |
| 08 测试与辅助工具 | 单测配置、Robolectric/框架套件、业务回归测试、聊天/加密/账号隔离/共享 UI 测试、订单数据层/预订草稿/订单 UI 纯逻辑测试、订单通知与菜谱分享映射/作用域测试、模板测试 | [08-testing-tools.md](08-testing-tools.md) | 已盘点 |

## 首次盘点边界

- 扫描仓库：`D:\projects\CookBook`，Git 分支 `master`；扫描时工作树干净。**P00-T01 首次盘点快照**纳入 115 个受跟踪 Kotlin/Java 源文件和 61 个 XML（该计数不代表 `P01-T02` 及后续增量后的当前文件总数）；排除 `.git/`、`.gradle/`、`build/`、`.idea/`、`.kotlin/`、`hs_err_pid*.log`、`local.properties`、生成产物、二进制缓存与密钥。
- 已覆盖模块与 sourceSet（当前）：`:app` 与 `:lib_android_common`、`:lib_auth`（`P01-T02` 新增）、`:lib_campaign_common`、`:lib_chat`、`:lib_cookbook`、`:lib_index`、`:lib_mine`、`:lib_network`、`:lib_order`、`:lib_photo_view`、`:lib_relation`（`P02-T01` 新增）、`:lib_route_annotation`、`:lib_route_api`、`:lib_route_compiler`。`main` 全量核对；`test`/`androidTest` 已核对，多数模块仍含模板测试；业务测试范围见分类 08，`P03-T05` 为 `lib_relation` 增加真实 Fragment 返回/取消/账号隔离测试。`P03-T01` 已将 `:lib_cookbook` 的菜谱列表/表单从硬编码 mock 更新为真实 `/recipes/mine` 与创建/编辑/删除接线；`P05-T02` 已将 `:lib_index` 首页从静态占位更新为 `/recipes/public` 与 `/feed`，并新增 `IndexSearchFragment` 搜索页；`P06-T02` 已将首页“发现”入口接线到 `CreatorsFragment` 创作者/公开菜谱页；`P07-T01/T02` 为 `:lib_chat` 增加 `data/domain` 源码与聊天/加密/Room/账号隔离测试；`P07-T03/T04` 将 `ChatFragment`/`ChatNotificationsFragment`/`ChatDetailFragment` 接入 Repository，完成会话列表、独立通知、好友文字聊天、历史分页、发送失败重试与权限提示，Mock 与真实数据源按 `CHAT_DATA_SOURCE` 切换；实时消息仍仅预留不可用实现，无未读/已读/送达回执，聊天 UI 仍待真机手工验收。`P08-T03` 为 `:lib_order` 增加 UI：预订确认底部弹层、3.9 菜品预订详情页与纯逻辑草稿，并由 `:lib_cookbook` 菜谱详情、`:lib_relation` 好友主页入口调用；`P08-T04` 已接线订单列表/详情（“我的销售”为同一 Tab 的卖家视角），独立销售管理页仍未实现。
- 不能访问/未覆盖模块：没有缺失的源码模块；产品原型图片、Figma 文件未核对，外部后端不纳入本索引能力盘点；`P03-T05` 仅随现有 Android 详情 API 校对匿名契约。`app/src/main/assets/nav/_nav*` 为构建生成的加密路由资源，只核对存在性与格式，不展开内容。
- 盘点证据（实际源文件/调用位置）：逐文件读取上述模块的 Kotlin/Java、Gradle、Manifest、布局与 values；分类表“真实调用位置/核查证据”列记录页面调用、测试文件或源码核查结论。`lib_android_common` 的 Robolectric 测试与源码交叉核对见分类 08。
- 最近可信同步：2026-10-29 `P10-T02-F01`（全部通知 `listAllNotifications`、`recipe_share` 分享者 DTO/映射、`NotificationScope` 作用域缓存、通知页 `fetchAll` 与订单/菜谱双跳转、菜谱详情预订按钮固定栏、订单条目两行文本、`lib_campaign_common` 单测依赖）；此前各阶段（`P00`～`P09-T05`）的完整增量依据与逐项核查记录已迁移至 [increment-log.md](increment-log.md)，历史未删除。

## 日常使用约定

1. 先打开本文件，再读与当前任务相关的分类；只按命中条目的真实路径核对源码。
2. 索引是查找入口，不是可直接复制的结论；调用前核对签名、线程、生命周期、权限与资源依赖。
3. 新增、修改、删除可复用能力后，在当前任务中更新对应分类与真实路径；跨分类只引用 ID，不重复描述。
4. 分类文件接近 12 KiB 时按功能拆子表；单个 Markdown 严格不超过 16 KiB，分类首页保留子表导航。
5. 本索引不是阶段进度；产品阶段只在 `docs/PROGRESS.md` 维护。
