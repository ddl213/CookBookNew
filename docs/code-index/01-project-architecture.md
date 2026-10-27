# 01 · 项目结构与架构约定

> 本类盘点状态：已盘点。已读取应用入口、各模块 Gradle、Manifest、路由生成器与页面基类；`P03-T01` 增量更新菜谱列表/表单路由与参数，并因条目增长拆为两个子表；`P04-T02` 更新 `PAGE_COOK_BOOK_DETAIL` 为已实现，并补充详情页/分享弹窗与用户主页菜谱跳转；`P05-T02` 新增 `PAGE_INDEX_SEARCH`/`IndexSearchFragment` 与 `:lib_index` 首页/搜索页组（ARCH-015）；`P06-T02` 新增 `PAGE_CREATORS_PUBLIC`/`CreatorsFragment` 创作者与公开菜谱页组（ARCH-016），并将 02 子表按路由参数/模块入口再拆为两个子表；`P07-T02` 更新 `MyApplication` 启动 `ChatSessionGuard` 的接线（ARCH-001）；`P07-T03/T04` 新增 `:lib_chat` 聊天页面组（ARCH-017）与聊天详情路由参数（ARCH-018），并将 `PAGE_CHAT`/`PAGE_CHAT_DETAIL`/`PAGE_CHAT_NOTIFICATIONS` 更新为已实现；`P08-T04` 新增订单列表/详情页面组与 `KEY_BUNDLE_ORDER_ID` 参数约定（ARCH-020），并将 `PAGE_ORDER_DETAIL` 更新为已实现、`MineFragment` 订单入口更新为切入订单 Tab（ARCH-010）；`P09-T02`～`P09-T05` 新增 `lib_mine` 页面组（ARCH-021：我的页入口、资料编辑、设置/隐私/通知/帮助页）与我的收藏列表页（ARCH-013），并将 `PAGE_MINE_INFO`/`PAGE_MY_COLLECTIONS`/`PAGE_SETTING`/`PAGE_SETTING_PRIVACY`/`PAGE_SETTING_NOTIFICATION`/`PAGE_SETTING_HELP` 更新为已实现（ARCH-008）。以下为可复用架构入口，不代表页面业务已完成或 UI 已验收。

**收录范围：**真实模块与 sourceSet、应用入口、导航容器、公共基类、路由与页面路径约定。

**索引原则：**只索引有助于定位/复用的项目级入口；页面是否存在以源码与调用为准，不把路由常量当作已实现页面。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 应用入口与基础架构](01-project-architecture/01-app-shell.md) | MainActivity/Application、五标签容器、Gradle 自动 include、Base 类、NRoute、底部标签切换 | ARCH-001～ARCH-007、ARCH-010 |
| [02 模块、路由与页面参数](01-project-architecture/02-modules-routes.md) | RoutePath、`lib_auth`/`lib_relation`/`lib_cookbook`/`lib_index`/`lib_chat`/`lib_order`/`lib_mine` 模块页面入口、用户/菜谱/聊天/预订/订单路由参数 | ARCH-008、ARCH-009、ARCH-011～ARCH-021（子表：路由参数 / 模块入口 / 聊天页面与参数 / 预订与订单页面） |

## 覆盖与缺口

- 已覆盖 `app` 与 `subModules` 模块边界、Gradle 自动 include、应用入口、基类、路由框架和当前已实现的页面路由。
- `RoutePath` 中 `PAGE_NEAR_COOK_BOOK`、`PAGE_USER_COOK_BOOK`、`PAGE_ADDRESS_INFO`、`PAGE_ADD_ADDRESS` 仍无实现类；`PAGE_MINE_INFO`/`PAGE_SETTING`/`PAGE_SETTING_PRIVACY`/`PAGE_SETTING_NOTIFICATION`/`PAGE_SETTING_HELP` 已由 `MineInfoFragment`/`SettingsFragment`/3 个设置说明页实现（ARCH-021），`PAGE_MY_COLLECTIONS` 已由 `CollectedRecipesFragment` 实现（ARCH-013）；`PAGE_ORDER_INFO` 已由 `OrderInfoFragment` 实现（ARCH-019），`PAGE_ORDER` 与 `PAGE_ORDER_DETAIL` 已分别由 `OrderFragment`、`OrderDetailFragment` 实现（ARCH-020）；`PAGE_INDEX_CHILD` 只有 `@Route`，未被导航调用；`PAGE_COOK_BOOK_DETAIL` 已由 `CookBookDetailFragment` 实现并接入列表/用户主页入口（ARCH-008、ARCH-013）；`PAGE_INDEX_SEARCH` 已由 `IndexSearchFragment` 实现，并由首页折叠搜索入口跳转（ARCH-008、ARCH-015）；`PAGE_CREATORS_PUBLIC` 已由 `CreatorsFragment` 实现，并由首页“发现”入口跳转（ARCH-008、ARCH-016）；`PAGE_CHAT` 已由 `ChatFragment` 实现会话列表并被 `HomeFragment` 消息标签加载，`PAGE_CHAT_DETAIL` 已由 `ChatDetailFragment` 实现、`PAGE_CHAT_NOTIFICATIONS` 已由 `ChatNotificationsFragment` 实现（ARCH-008、ARCH-017）；聊天页面代码已接线，真机 UI 仍待手工验收。
- `BaseViewModel.launch` 仍无业务调用；页面协程目前直接使用 `lifecycleScope`，见各页面源码。
