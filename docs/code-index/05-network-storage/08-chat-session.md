# 05-08 · 聊天账号隔离与清理

> 返回分类首页：[05-network-storage.md](../05-network-storage.md)。Repository 见 [06-chat-repository.md](06-chat-repository.md)，Room 缓存见 [07-chat-crypto-room.md](07-chat-crypto-room.md)。

**收录范围：**聊天缓存 owner 持久化、登录态守卫的清理规则与应用启动接线。

**索引原则：**只清理明确退出或账号切换；`Unknown` 或 token 残留不得误删；不保存 token 或聊天内容。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| DATA-050 | `ChatOwnerStore`、`MmkvChatOwnerStore` | 持久化“上一次登录并写入聊天缓存的用户 ID”，供退出/切换账号时决定是否清理。 | 接口 `readOwnerId/writeOwnerId/clearOwnerId`；生产实现使用 MMKV Key `chat_local_owner_user_id`，只接受 >0 的 owner，其他读取为 null。未登录不得写入；只保存 owner ID，不保存 token、聊天正文或用户资料。不要用该 Key 推断当前登录态，当前登录态以 BIZ-001 `SessionManager` 为准。测试可注入内存实现。 | `subModules/lib_chat/src/main/java/com/rhys/chat/data/session/ChatOwnerStore.kt` | `ChatSessionGuard` 默认 ownerStore（DATA-051）；`FakeOwnerStore`（TEST-026）在 TEST-025 中替代 MMKV。 |
| DATA-051 | `ChatSessionGuard`、`ChatSessionRules`、`ChatSessionAction` | 应用级登录态守卫：切换账号或明确退出时清空全部聊天缓存，避免旧账号数据残留。 | 纯判定 `ChatSessionRules.decide(previousOwnerId, state, hasToken)`：`LoggedIn` 且 owner 与上次不同 → `CLEAR_AND_RECORD_OWNER`；同账号/无历史 → `RECORD_OWNER`；`LoggedOut` 且 token 为空（明确退出）→ `CLEAR_AND_FORGET_OWNER`；`LoggedOut` 但 token 仍在、`Unknown` → `NONE`。`handle` 为 internal 供测试直接驱动；`start()` 收集 `SessionManager.state`，重复调用返回同一 Job；`start(context)` 创建应用单例，必须在 MMKV 与 `AppHelper` 初始化后调用。清理异常交给 `onError` 记录，不导致应用崩溃；不记录 token 或聊天内容。切换账号时清空所有 owner 的聊天表，随后记录新 owner。 | `subModules/lib_chat/src/main/java/com/rhys/chat/data/session/ChatSessionGuard.kt` | `MyApplication.onCreate` 在 `MMKV.initialize`/`AppHelper.init` 后调用 `ChatSessionGuard.start(this)`（ARCH-001）；`ChatSessionGuardTest`（TEST-025）覆盖记录、同账号、明确退出、token 残留、Unknown、切换账号、收集流与异常不上抛。 |

> 跨分类引用：登录态与 `AuthState` 见 BIZ-001/BIZ-002；DAO 的 `clearAll` 见 DATA-047；owner 来源见 DATA-041；应用启动顺序见 ARCH-001。
>
> 未覆盖：没有多进程/多实例清理协调；登录态流异常后的恢复依赖下次状态变化；不处理系统清除 MMKV 或数据库文件损坏的恢复提示。
