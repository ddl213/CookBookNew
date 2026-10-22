# 06 · 公共业务组件

> 本类盘点状态：已盘点。2026-10-22 `P01-T02` 增量登记跨页面登录态与认证表单校验能力；已核对 `lib_network/session`、`lib_auth` 与调用页面。

**收录范围：**跨页面复用的登录、好友、菜谱、聊天、订单业务组件与状态展示。

**索引原则：**不新增未批准的业务行为；组件具有真实共享入口和适用边界时才登记。页面骨架、列表基础设施、数据模型分别归入分类 01、03、05。

**核实结论：**

- 菜谱页只有“已发布/草稿”两个标签，两个标签都创建同一个 `CookBookChildFragment`，该 Fragment 未读取 `KEY_BUNDLE_PAGE_ID`，使用同一个硬编码 6 条 `Recipe` 列表；不是可复用的已发布/草稿业务组件。
- 首页 `IndexFragment` 的标签、分类、今日上新、热门推荐四个 RecyclerView 都填入同一组 `”推荐”`、`”关注”` 字符串，条目布局为空；`IndexChildFragment` 有列表实现但未被任何页面导航调用。
- 消息、订单页面仍只有 `TitleBar` 占位；“我的”页已按 `AuthState` 渲染未登录/已登录区块，登录、退出与占位入口已接线，但收藏、好友、订单、设置仍只提示后续开放。
- 登录/注册已由 `lib_auth` 实现并通过 `SessionManager` 共享登录态；好友关系、预订/购物车、订单状态机、聊天组件仍未实现。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| BIZ-001 | `SessionManager.state`、`restore/login/register/logout/currentToken` | 全局登录态入口：启动恢复、登录、注册、退出；页面通过同一个 `StateFlow<AuthState>` 渲染，避免各自维护登录状态。 | Kotlin `object` 进程内单例；`state` 初始 `Unknown`，UI 不能把 Unknown 当作未登录。`restore()` 有缓存时先发 `LoggedIn(cached)` 再请求 `/auth/me`；401 清会话，网络失败但有缓存时保持登录，无缓存则 `LoggedOut`。`login/register` 为 suspend，成功后保存 token/用户并更新状态；`logout()` 同步清本地会话并递增 generation，防止进行中的 restore 写回。内部 `Mutex` 串行化会话操作；调用方需在协程中执行，UI 收集应使用 `repeatOnLifecycle(STARTED)`。 | `subModules/lib_network/src/main/java/com/example/network/session/SessionManager.kt` | `MainActivity.initData` 调用 `restore()`；`LoginFragment`/`RegisterFragment` 调用 `login/register`；`MineFragment` 收集 `state` 并调用 `logout()`；底部鉴权头见 DATA-004，Repository 见 DATA-010。 |
| BIZ-002 | `AuthState`（`Unknown` / `LoggedOut` / `LoggedIn`） | 页面统一消费的登录态模型，配合 BIZ-001 做加载、未登录、已登录三态切换。 | `sealed interface`；`LoggedIn` 持有 DATA-007 `UserDto`。`Unknown` 只表示启动恢复尚未完成，应显示过渡状态而不是“未登录”；`LoggedOut` 才是确定未登录。页面应在 view 存在时再更新控件。 | `subModules/lib_network/src/main/java/com/example/network/session/AuthState.kt` | `SessionManager` 发射三种状态；`MineFragment.render()` 对三种状态做穷尽 `when` 并切换 `pbSession`、`llLoggedOut`、`llLoggedIn`。 |
| BIZ-003 | `AuthValidator.validateLogin/validateRegister`、`ValidationResult` | 登录/注册表单的纯 Kotlin 预校验，在发请求前给出字段提示；不替代后端校验。 | 用户名校验前 trim，长度 3-32；密码不 trim、至少 6 位；昵称 trim 后可选、最多 32；注册确认密码必填且必须一致。返回 `ValidationResult(isValid, errorMessage?)`，用 `valid`/`invalid(message)` 构造。规则与后端可能变化，服务端 400 仍由 DATA-009 映射。 | `subModules/lib_auth/src/main/java/com/rhys/auth/AuthValidator.kt` | `LoginFragment.submitLogin()`、`RegisterFragment.submitRegister()` 调用；`AuthValidatorTest` 12 例（TEST-005）。 |
