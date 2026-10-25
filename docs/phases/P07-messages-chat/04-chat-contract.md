# P07 · 聊天与预订通知客户端契约（v1）

> 状态：客户端契约已定义，服务端接口尚未实现；本文只描述协议与客户端约束，不代表后端已可用。
> 适用范围：好友一对一文字聊天、消息列表中的独立预订通知。P07-T01 只落地契约与可替换数据层，不含 UI、真实 WebSocket 连接或加密本地存储。

## 1. 范围与非目标

- 仅文字消息；`type` 固定为 `TEXT`。
- 不含群聊、语音、视频、图片消息、未读计数、已读回执、消息撤回或在线状态。
- 预订通知是消息列表中的独立项，不并入聊天会话；本期不实现未读/已读。
- 实时消息只预留 WebSocket 扩展能力；本期不建立连接、不重连、不推送。
- 聊天记录加密本地存储属于 P07-T02，不在本契约实现。

## 2. 配置与数据源切换

### 2.1 服务地址

- 统一复用现有 `NetworkConfig.baseUrl`；构建期由 Gradle 属性 `apiBaseUrl` 写入 `lib_network` 的 `BuildConfig.API_BASE_URL`，默认 `http://10.0.2.2:3000/api/v1/`（仅 Android 模拟器访问宿主机）。
- 聊天模块不得硬编码服务地址。REST 路径均相对 `NetworkConfig.baseUrl`。
- 生产必须使用 HTTPS/WSS；开发期 HTTP/WS 仅限本地模拟器或局域网联调。

### 2.2 数据源开关

- Gradle 属性：`-PchatDataSource=mock|remote`。
- 未显式指定时：debug 默认 `mock`，release 默认 `remote`。
- 非法值回退 `remote` 并记录警告；不因拼写错误静默启用 Mock。
- Mock 仅用于本地开发与验收，`isMock=true`。UI 必须明确标识演示模式，发送成功只代表内存写入，不得呈现为“已同步到服务器”。

## 3. 鉴权

- 所有 REST 接口沿用 `Authorization: Bearer <token>`；无 token 或 token 失效返回 `401`。
- WebSocket 握手同样使用 `Authorization` 头；不得把 token 放在 URL 查询参数中（预留，本期不连接）。
- 好友关系、会话参与方与消息归属由服务端校验；客户端隐藏按钮不能替代权限控制。

## 4. 数据模型

时间字段统一为 ISO-8601 字符串（例如 `2026-10-20T09:02:00Z`）。游标对客户端不透明，原样回传，不得自行解析或拼接。

### 4.1 `ChatConversationDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 会话 ID |
| `peer` | UserDto? | 会话另一方（好友）的公开资料；客户端容忍缺省，展示时使用安全占位 |
| `lastMessage` | ChatMessageDto? | 最近一条消息；无消息时可为空 |
| `updatedAt` | String? | 最近更新时间，会话列表按其倒序 |

### 4.2 `ChatMessageDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 服务端消息 ID |
| `conversationId` | Long | 所属会话 |
| `clientMessageId` | String | 客户端生成的幂等键，重试时必须保持不变 |
| `senderId` | Long | 发送方用户 ID |
| `receiverId` | Long? | 接收方用户 ID，可省略 |
| `type` | String | 固定 `TEXT` |
| `content` | String | 消息正文，已由发送方 trim |
| `createdAt` | String? | 创建时间 |

契约不包含已读、送达、发送状态字段；客户端不得自行推断服务端已读状态。

### 4.3 `ChatNotificationDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 通知 ID |
| `type` | String | 固定 `RESERVATION` |
| `title` | String | 通知标题 |
| `body` | String | 通知正文 |
| `orderId` | Long? | 关联订单 ID；无真实订单事件时为空 |
| `createdAt` | String? | 创建时间 |

契约不包含已读字段；本期不做未读计数与已读回执。

### 4.4 统一游标分页

```json
{ "items": [], "nextCursor": "opaque-cursor-or-null" }
```

- `nextCursor` 为空或 `null` 表示没有下一页。
- `limit` 默认 20，最大 50；客户端在请求前归一化到 1..50。

## 5. REST 接口

以下路径相对 `NetworkConfig.baseUrl`（已包含 `/api/v1/`）。

### 5.1 会话列表

`GET chat/conversations?limit=&cursor=`

- 返回 `CursorPage<ChatConversationDto>`，按 `updatedAt` 倒序。
- 仅返回当前用户参与的会话。

### 5.2 打开或创建会话

`POST chat/conversations`

```json
{ "peerUserId": 2 }
```

- 返回 `ChatConversationDto`。
- 仅允许与好友创建会话；已存在时返回现有会话，不重复创建。
- 服务端校验好友关系；非好友返回 `403 NOT_FRIENDS`。

### 5.3 历史消息

`GET chat/conversations/{conversationId}/messages?limit=&before=`

- 返回 `CursorPage<ChatMessageDto>`。
- `before` 为空时返回最新一页；返回项按 `createdAt` 升序。
- `nextCursor` 指向更早一页，下一次请求作为 `before` 回传。
- 非参与方访问返回 `403 CHAT_FORBIDDEN`；会话不存在返回 `404 CHAT_NOT_FOUND`。

### 5.4 发送消息

`POST chat/conversations/{conversationId}/messages`

```json
{ "clientMessageId": "uuid-or-client-key", "type": "TEXT", "content": "周六见" }
```

- 返回 `ChatMessageDto`。
- 服务端按 `clientMessageId` 幂等：同一用户重复提交相同键必须返回同一条消息，不新增记录。
- 若服务端未实现幂等并返回 `409`，客户端不得把重试结果显示为第二条新消息。
- 好友关系或会话权限不足返回 `403`；内容不合法返回 `400/422 VALIDATION_ERROR`。

### 5.5 预订通知列表

`GET notifications?limit=&cursor=`

- 返回 `CursorPage<ChatNotificationDto>`。
- 本期类型仅 `RESERVATION`，作为消息列表独立项展示。

## 6. 内容约束

- 正文 trim 后必须非空，长度 1..2000 字符。
- 客户端在发送前做防御性校验并 trim；服务端仍为最终校验方。
- `clientMessageId` trim 后必须非空；重试必须复用同一个值。
- 不在日志、异常文案或分析事件中记录消息正文与 token。

## 7. 权限与错误

| HTTP | code | 客户端处理 |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | 提示登录失效并回到登录流程 |
| 403 | `NOT_FRIENDS` | 提示“你们还不是好友，无法发送消息” |
| 403 | `CHAT_FORBIDDEN` | 提示“没有权限访问该会话” |
| 404 | `CHAT_NOT_FOUND` | 提示会话不存在或已被删除 |
| 400/422 | `VALIDATION_ERROR` | 提示内容不合法；保留输入供修改 |
| 409 | 任意 | 视为幂等冲突，刷新或按同一 `clientMessageId` 重试，不新增第二条 |
| 429 | 任意 | 提示请求过于频繁 |
| 5xx | 任意 | 提示服务器暂不可用，可重试 |
| — | `IOException` | 提示无法连接服务器，可重试 |

客户端不得把失败请求伪造成成功消息；重试前必须保留原 `clientMessageId`。

## 8. WebSocket 预留（本期不实现）

- 地址：由 `NetworkConfig.baseUrl` 的 scheme/host/port 派生，路径 `/ws/chat`；HTTPS 对应 `wss`，HTTP 对应 `ws`（仅本地开发）。
- 握手鉴权：`Authorization: Bearer <token>` 请求头，不使用 URL token。
- 事件信封：

```json
{ "type": "message.created", "data": { } }
```

- 本期至少定义类型：`message.created`（`data` 为 `ChatMessageDto`）、`notification.created`（`data` 为 `ChatNotificationDto`）。
- 本期只定义接口与不可用实现：不连接、不重连、不推送；UI 必须明确显示实时消息暂不可用，不能伪造已连接状态。

## 9. 客户端实现映射

- `lib_network`：`ChatApi`、`NotificationApi`、`CursorPage`、聊天/通知 DTO、`ChatErrorMapper`、`ApiClient.chatApi/notificationApi`。
- `lib_chat`：`ChatDataSource`、`RemoteChatDataSource`、`MockChatDataSource`、`ChatRealtimeSource` / `UnavailableChatRealtimeSource`、`ChatDataSourceConfig`、`ChatRepository`。
- 后端就绪后只替换数据源实现与配置值，不重构 UI 或页面调用方式。

## 10. 变更记录

- 2026-10-25：v1 初稿。依据用户已确认决策：先定契约；REST 发送/历史 + WebSocket 预留；Mock/远程可切换；预订通知独立项；本期不做未读/已读与加密存储。
