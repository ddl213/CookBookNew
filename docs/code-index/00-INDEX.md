# Android 项目可复用能力索引

> **盘点状态：已盘点**：2026-10-22 完成首次全仓只读源码盘点，覆盖 `app` 与 `subModules` 下 12 个 `lib_*` 模块；同日按 `P01-T02` 实际改动增量同步认证、会话、网络与“我的”登录态能力，并在纳入新增 `:lib_auth` 后覆盖 13 个 `lib_*` 模块。分类表均基于实际源码、Gradle、Manifest 与资源文件核查；索引是查找入口，不代表运行时功能已验证，也不替代阶段进度与用户验收。

## 分类导航

| 分类 | 主要内容 | 入口 | 当前状态 |
| --- | --- | --- | --- |
| 01 项目结构与约定 | 模块、应用入口、导航、基类、路由 | [01-project-architecture.md](01-project-architecture.md) | 已盘点 |
| 02 工具类与扩展方法 | Utils、扩展、格式/密度/图片处理 | [02-utilities-extensions.md](02-utilities-extensions.md) | 已盘点 |
| 03 自定义 View 与 UI 组件 | 复合 View、Adapter、刷新、弹窗、图片查看 | [03-custom-views-ui.md](03-custom-views-ui.md) | 已盘点 |
| 04 布局、样式及资源 | 主题、颜色、selector、样式、attrs | [04-layouts-styles-res.md](04-layouts-styles-res.md) | 已盘点 |
| 05 网络、存储与异步 | 数据模型、Room 骨架、键值存储 | [05-network-storage.md](05-network-storage.md) | 已盘点 |
| 06 公共业务能力 | 跨页面业务组件与状态 | [06-business-components.md](06-business-components.md) | 已盘点 |
| 07 依赖与工程工具 | 版本目录、依赖、Gradle、校验脚本 | [07-dependencies-build.md](07-dependencies-build.md) | 已盘点 |
| 08 测试与辅助工具 | 单测配置、Robolectric 套件、模板测试 | [08-testing-tools.md](08-testing-tools.md) | 已盘点 |

## 首次盘点边界

- 扫描仓库：`D:\projects\CookBook`，Git 分支 `master`；扫描时工作树干净。**P00-T01 首次盘点快照**纳入 115 个受跟踪 Kotlin/Java 源文件和 61 个 XML（该计数不代表 `P01-T02` 增量后的当前文件总数）；排除 `.git/`、`.gradle/`、`build/`、`.idea/`、`.kotlin/`、`hs_err_pid*.log`、`local.properties`、生成产物、二进制缓存与密钥。
- 已覆盖模块与 sourceSet（当前）：`:app` 与 `:lib_android_common`、`:lib_auth`（`P01-T02` 新增）、`:lib_campaign_common`、`:lib_chat`、`:lib_cookbook`、`:lib_index`、`:lib_mine`、`:lib_network`、`:lib_order`、`:lib_photo_view`、`:lib_route_annotation`、`:lib_route_api`、`:lib_route_compiler`。`main` 全量核对；`test`/`androidTest` 已核对，除 `lib_android_common` 外多数为模板测试。
- 不能访问/未覆盖模块：没有缺失的源码模块；产品原型图片、Figma 文件与后端接口不属于本仓库，未核对。`app/src/main/assets/nav/_nav*` 为构建生成的加密路由资源，只核对存在性与格式，不展开内容。
- 盘点证据（实际源文件/调用位置）：逐文件读取上述模块的 Kotlin/Java、Gradle、Manifest、布局与 values；分类表“真实调用位置/核查证据”列记录页面调用、测试文件或源码核查结论。`lib_android_common` 的 Robolectric 测试与源码交叉核对见分类 08。
- 最近一次可信同步依据：2026-10-22 `P00-T01` 首次全仓盘点；2026-10-22 `P01-T02` 按实际改动增量维护（`lib_auth` 新模块、`lib_network` 认证/会话、`RoutePath` 登录注册、`MainTabController`、主色与按钮资源、Retrofit/OkHttp 依赖、两个 JVM 测试类），并核对相关调用位置。后续仅按当前任务实际变更文件增量维护，不在每个任务后重新全仓扫描。

## 日常使用约定

1. 先打开本文件，再读与当前任务相关的分类；只按命中条目的真实路径核对源码。
2. 索引是查找入口，不是可直接复制的结论；调用前核对签名、线程、生命周期、权限与资源依赖。
3. 新增、修改、删除可复用能力后，在当前任务中更新对应分类与真实路径；跨分类只引用 ID，不重复描述。
4. 分类文件接近 12 KiB 时按功能拆子表；单个 Markdown 严格不超过 16 KiB，分类首页保留子表导航。
5. 本索引不是阶段进度；产品阶段只在 `docs/PROGRESS.md` 维护。
