# 01 · 项目结构与架构约定

> 本类盘点状态：已盘点。已读取应用入口、各模块 Gradle、Manifest、路由生成器与页面基类；`P03-T01` 增量更新菜谱列表/表单路由与参数，并因条目增长拆为两个子表；`P04-T02` 更新 `PAGE_COOK_BOOK_DETAIL` 为已实现，并补充详情页/分享弹窗与用户主页菜谱跳转；`P05-T02` 新增 `PAGE_INDEX_SEARCH`/`IndexSearchFragment` 与 `:lib_index` 首页/搜索页组（ARCH-015）。以下为可复用架构入口，不代表页面业务已完成。

**收录范围：**真实模块与 sourceSet、应用入口、导航容器、公共基类、路由与页面路径约定。

**索引原则：**只索引有助于定位/复用的项目级入口；页面是否存在以源码与调用为准，不把路由常量当作已实现页面。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 应用入口与基础架构](01-project-architecture/01-app-shell.md) | MainActivity/Application、五标签容器、Gradle 自动 include、Base 类、NRoute、底部标签切换 | ARCH-001～ARCH-007、ARCH-010 |
| [02 模块、路由与页面参数](01-project-architecture/02-modules-routes.md) | RoutePath、`lib_auth`/`lib_relation`/`lib_cookbook`/`lib_index` 模块入口、用户与菜谱路由参数 | ARCH-008、ARCH-009、ARCH-011～ARCH-015 |

## 覆盖与缺口

- 已覆盖 `app` 与 `subModules` 模块边界、Gradle 自动 include、应用入口、基类、路由框架和当前已实现的页面路由。
- `RoutePath` 中 `PAGE_NEAR_COOK_BOOK`、`PAGE_USER_COOK_BOOK`、`PAGE_ORDER_INFO`、`PAGE_CHAT_DETAIL`、`PAGE_ORDER_DETAIL`、`PAGE_SETTING`、`PAGE_ADDRESS_INFO`、`PAGE_ADD_ADDRESS`、`PAGE_MINE_INFO` 仍无实现类；`PAGE_INDEX_CHILD` 只有 `@Route`，未被导航调用；`PAGE_COOK_BOOK_DETAIL` 已由 `CookBookDetailFragment` 实现并接入列表/用户主页入口（ARCH-008、ARCH-013）；`PAGE_INDEX_SEARCH` 已由 `IndexSearchFragment` 实现，并由首页折叠搜索入口跳转（ARCH-008、ARCH-015）。
- `BaseViewModel.launch` 仍无业务调用；页面协程目前直接使用 `lifecycleScope`，见各页面源码。
