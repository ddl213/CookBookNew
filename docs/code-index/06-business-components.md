# 06 · 公共业务组件

> 本类盘点状态：已盘点。`P01-T02` 增量登记跨页面登录态与认证表单校验能力；`P02-T01` 增量登记用户主页操作决策、好友申请状态与 `lib_relation` 内部 UI 复用工具；`P03-T01` 增量登记菜谱表单校验与固定选项映射，并更正 `lib_cookbook` 的旧 mock 描述；`P04-T02` 登记 `lib_cookbook` 详情页/分享弹窗共用的模块内 UI 工具，并更新详情页核实结论；`P05-T02` 登记 `lib_index` 首页/搜索页共用的模块内 UI 工具，并更新首页核实结论；`P06-T02` 登记 `lib_index` 创作者聚合 `CreatorAggregator`（BIZ-011），并因条目增长按功能拆为两个子表；`P07-T01/T02` 核对聊天数据层已实现但消息页 UI 仍为占位；`P07-T03/T04` 依据聊天页面实际接线，新增 `lib_chat` 模块内共享 UI 规则（BIZ-012）与好友主页发消息入口（BIZ-013），并更新消息页核实结论；`P08-T03` 更新用户主页操作决策（BIZ-004 增加 `canReserveDishes`），登记预订草稿纯逻辑（BIZ-014）并补充菜谱详情/好友主页/3.9 页面预订入口结论；`P08-T04` 新增订单列表/详情共享纯逻辑（BIZ-015），并更新订单页面接线结论；`P08-T05` 更新通知页数据源与订单详情跳转结论（不新增 BIZ 条目，数据层见 DATA-069～DATA-071）；`P10-T02-F01` 更新通知页为全部通知（订单 + `recipe_share`）与订单/菜谱双跳转，仍复用 BIZ-012，不新增 BIZ 条目。已核对其真实调用页面。

**收录范围：**跨页面复用的登录、好友、菜谱、聊天、订单业务组件与状态展示。

**索引原则：**不新增未批准的业务行为；组件具有真实共享入口和适用边界时才登记。页面骨架、列表基础设施、数据模型分别归入分类 01、03、05。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 登录态、会话与关系操作](06-business-components/01-auth-relation.md) | SessionManager、AuthState、认证表单校验、用户主页操作/预订入口决策、好友申请状态、`RelationUiSupport`、预订草稿 | BIZ-001～BIZ-006、BIZ-014 |
| [02 菜谱表单、详情与首页/创作者展示](06-business-components/02-recipes-index.md) | 菜谱表单校验/选项、详情页 UI 工具、首页/搜索页共享 UI、创作者聚合 | BIZ-007～BIZ-011 |
| [03 聊天页面共享 UI 与好友入口](06-business-components/03-chat-ui.md) | `ChatUiSupport` 展示/参数规则、好友主页发消息入口 | BIZ-012、BIZ-013 |
| [04 订单页面共享 UI 与纯逻辑](06-business-components/04-order-ui.md) | `OrderUiSupport` 状态/动作映射、`allowedActions` 过滤、计数摘要、去重与时间格式化 | BIZ-015 |

**核实结论：**

- 菜谱页不再是硬编码 mock：`CookBookFragment` 的“已发布/草稿”Tab 通过 `KEY_BUNDLE_PAGE_ID` 创建 `CookBookChildFragment`，后者按状态请求 `/recipes/mine?status`，具备分页、下拉刷新、空/加载/错误/登录态，卡片“更多”菜单可编辑或删除；卡片正文点击进入 `CookBookDetailFragment`（`PAGE_COOK_BOOK_DETAIL`）。
- 新建/编辑由 `AddRecipesFragment` 实现：支持 `PickVisualMedia` 选图、上传/移除、固定+自定义分类、可见性、草稿/已发布、食材/步骤多行解析与逐字段校验；保存失败/加载失败有独立反馈。
- 菜谱详情由 `CookBookDetailFragment` 实现：展示封面/作者/标签/食材/步骤，登录后可收藏、作者可分享、评论分页与发表；匿名只读公开内容，草稿隐藏评论与收藏/预订按钮，404 统一显示“不存在或无权查看”；`ShareFriendDialog` 负责好友列表与选择，分享请求由详情页发起。
- 预订已接线（P08-T03）：菜谱详情“加入购物车（预订）”在登录、非本人、非草稿且 `canReserve=true` 时打开 `ReservationConfirmDialog`，否则分别登录引导或提示本人/非好友；好友主页 `canReserveDishes` 仅在已登录、非本人且好友时显示“预订菜品”，携带 `KEY_USER_ID` 进入 3.9 `OrderInfoFragment`；3.9 页仅展示好友资料与 `canReserve=true` 菜品，条目重新打开确认弹窗。确认弹窗支持同好友多菜、数量、整单备注、提交、Mock 演示标识与待确认重试/放弃，数据经 `OrderRepository`（DATA-063），不做支付/购物车/陌生人下单。
- 首页 `IndexFragment` 已接线公开菜谱与好友动态：六个标签（推荐/好友动态/热门/家常菜/早餐/甜品）中公开标签调用 DATA-024 `listPublic`（推荐/热门按后端默认顺序，不实现推荐算法），好友动态调用 `listFeed` 且未登录不请求、展示登录引导；具备折叠搜索、分类筛选、分页、空/错误/登录态与 `requestGeneration` 迟到响应丢弃。搜索页 `IndexSearchFragment` 支持菜谱/用户双模式：菜谱搜索匿名可用，用户搜索需登录，双列表独立分页，模式切换/账号切换清理旧结果。`IndexChildFragment` 仍有列表实现但未被页面导航调用。
- 创作者/公开菜谱页 `CreatorsFragment` 已接线：首页标题右侧“发现”入口跳转；推荐大师由公开菜谱作者按首次出现顺序去重聚合（`CreatorAggregator`，纯展示，不含评分/认证/附近/个性化推荐），公开菜谱复用 DATA-024 `listPublic`；双标签独立分页、匿名可浏览，切换标签丢弃迟到响应，View 重建重新加载当前标签首页。
- 消息页已接线（ARCH-017）：`ChatFragment` 以 `@Route(PAGE_CHAT)` 注册并被 `HomeFragment` 作为“消息”标签，展示 Room 观察流驱动的会话列表与顶部独立通知入口（订单 + 菜谱分享），支持登录/账号切换重建、首屏/下拉刷新、游标加载更多、空/加载/错误与内联重试，`isMock=true` 显示演示横幅；`ChatNotificationsFragment`（`PAGE_CHAT_NOTIFICATIONS`）提供独立通知列表，数据源为 DATA-071 `OrderNotificationRepository`，跟随订单 Mock/Remote 配置经 `fetchAll` 拉取不带 `category` 的全部通知（订单 + `recipe_share`），点击按 `orderId`/`recipeId` 分别跳订单/菜谱详情，无有效关联时只提示；`ChatDetailFragment`（`PAGE_CHAT_DETAIL`）提供好友一对一文字聊天，支持会话或好友 ID 入口、历史“加载更早”锚点（显式新消息滚动优先于分页锚点）、发送中/失败/点击重试、403/404 权限提示，并明确显示实时消息不可用。Debug 默认 Mock、Release 默认 remote，Mock 仅本地数据；未读/已读、送达回执与真实 WebSocket 均未实现（DATA-038）。聊天数据层、加密缓存、账号隔离见分类 05（DATA-039～DATA-051）。页面代码已接线，真机 UI 仍待手工验收，不要把数据层测试当作聊天功能已验收。订单 Tab 已接线（ARCH-020）：`OrderFragment` 提供“我的订单/我的销售”双标签、七种整单状态筛选、分页、登录/空/错误态、Mock 演示标识与返回刷新；`OrderDetailFragment` 展示买卖双方/状态/计数/备注/明细，并按服务端 `allowedActions` 生成动作与整单取消；两页共用 BIZ-015，但均无 Fragment/Robolectric UI 测试。
- “我的”页已按 `AuthState` 渲染未登录/已登录区块，登录、退出已接线；“我的好友”跳转 `PAGE_FRIENDS`，“我的订单”经 ARCH-010 `switchTab(3)` 切入订单 Tab（DEP-015）；资料卡→`PAGE_MINE_INFO`、我的收藏→`PAGE_MY_COLLECTIONS`、设置→`PAGE_SETTING` 均已接线，统一 `openPage()` 在路由失败时 Toast（ARCH-021），不再提示后续开放。
- 登录/注册、好友列表/搜索/申请/主页、个人菜谱发布/管理与菜谱详情（含评论/收藏/分享）、我的收藏列表、我的资料编辑、设置/隐私/通知/帮助页、创作者/公开菜谱页、消息/通知/聊天详情、预订确认、3.9 菜品预订页与订单列表/详情均已接线；“我的销售”复用同一订单 Tab 的卖家视角，独立销售管理页未实现，无购物车、支付、配送或陌生人下单，聊天无未读/已读与实时推送。预订 UI 与数据层均未做 Fragment/Robolectric 测试，真机 UI 仍待手工验收。

**覆盖与缺口：**只登记当前有真实共享入口的业务组件；分类 05 的接口/DTO/Repository、分类 03 的列表与控件不在此重复。创作者页没有独立 UI/Robolectric 测试，仅 `CreatorAggregator` 纯函数有单测（TEST-017）；推荐算法、创作者认证、附近创作者与公开菜谱搜索排序均未实现。聊天页面已接线并有数据层与共享 UI 纯函数测试（TEST-018～TEST-029），通知页另有订单通知/菜谱分享映射与仅订单/全部通知 Repository 测试（TEST-038～TEST-039），但没有 Fragment/Robolectric UI 测试（含分享/订单双跳转与空页跳过）；未读/已读、红点、推送、送达回执、真实 WebSocket、语音/图片/群聊均未实现，UI 仍需手工验收。预订只有 `ReservationDraft` 纯逻辑测试（TEST-036）与好友主页决策测试（TEST-007），确认弹窗、3.9 页面、入口显隐与真实后端均无 UI/契约测试；订单列表/详情只覆盖 `OrderUiSupport` 纯逻辑（TEST-037），页面渲染、筛选、分页、动作确认与 View 重建均无 UI/契约测试。我的收藏、资料编辑与设置/隐私/通知/帮助页目前只有构建验证，没有 Fragment/Robolectric UI 测试或契约测试，待正式 UI 验证。
