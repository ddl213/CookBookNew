# 05 · 网络、存储与异步能力

> 本类盘点状态：已盘点。已核对 `lib_network`、`lib_cookbook` 的 Room 依赖、`lib_chat` 的加密聊天缓存与 `lib_android_common` 的存储工具。`P01-T02` 增量新增 Retrofit/OkHttp 认证客户端、认证 Repository、MMKV 会话存储与媒体 URL 解析；`P02-T01` 增量新增用户/好友接口、关系 DTO、分页、关系 Repository 与错误映射；`P03-T01` 增量新增菜谱接口、图片上传、菜谱写请求/Repository、媒体规则与菜谱错误码；`P04-T02` 增量新增社交接口（评论/收藏/分享）、DTO 与错误码；`P05-T02` 增量新增公开菜谱/好友动态接口与查询参数归一化；`P07-T01` 增量新增聊天/通知 REST 接口、游标分页、聊天 DTO、错误映射、可替换 Remote/Mock 数据源与实时源预留；`P07-T02` 增量新增聊天 Repository、AES-GCM/Keystore 字段加密、Room 三表缓存、账号隔离守卫与应用启动接线；`P07-T03/T04` 校正聊天数据层与页面集成后的行为：会话预览/更新时间按时间合并不回退（DATA-049）、`retryMessage` 读取前后复核 owner（DATA-039）、Mock 跨实例稳定会话 ID（DATA-037），并确认聊天三页为 Repository 的实际消费方（ARCH-017）；`P08-T02` 增量新增订单 REST 接口、订单 DTO/状态动作常量、错误映射、构建期数据源配置、Mock 通知存储、可替换订单数据源、进程内 Mock 共享存储、订单 Repository、MMKV pending 存储与 AES-GCM/Keystore 加密；`P08-T04` 将 `OrderFragment`/`OrderDetailFragment` 确认为 `listOrders/getOrder/actOnItem/cancelOrder` 的实际页面消费方，未新增数据层能力；`P08-T05` 增量新增订单通知 DTO 兼容模型（DATA-069）、`OrderNotificationMapper` 六类/旧 payload 映射（DATA-070）与 `OrderNotificationRepository` 跟随订单数据源的页码分页/作用域缓存（DATA-071），并更新 `NotificationApi` 的 `listOrderNotifications`（DATA-030）与 Mock 通知存储消费方（DATA-056）；同任务记入遗留风险：旧 `NotificationApi.listNotifications`（P07 游标接口）后端仅支持 `page/pageSize`，`nextCursor` 为 null，未发现 main 页面调用方，未来复用不得透传 cursor、须在客户端转换页码。因内容增长，本分类拆为十一个子表。

**收录范围：**API/网络包装、Repository、Room/本地数据、缓存、协程/异步共用封装。

**索引原则：**记录权限、异常/重试、线程和数据安全边界，不擅自推断真实接口；骨架与未接线能力必须注明。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 数据模型、认证与存储](05-network-storage/01-foundation-auth.md) | 本地模型、Room 骨架、NetworkConfig、ApiClient、AuthApi/DTO、认证错误映射、AuthRepository、SessionStore | DATA-001～DATA-011 |
| [02 用户、好友关系与分页](05-network-storage/02-relation.md) | UserApi/FriendApi、关系 DTO、PageResponse、RelationErrorMapper、RelationApiCall/requireSuccessful、UserRepository/FriendRepository | DATA-012～DATA-021 |
| [03 菜谱发布、上传与写操作](05-network-storage/03-recipes.md) | RecipeApi（含公开/feed）、UploadApi、SocialApi、RecipeRepository、RecipeMediaRules、菜谱写请求/上传响应/社交 DTO、删除 204 校验与上传约束 | DATA-022～DATA-028 |
| [04 聊天与通知网络接口](05-network-storage/04-chat-network.md) | ChatApi、NotificationApi（含订单通知页码接口）、CursorPage、聊天/通知 DTO、ChatErrorMapper、订单通知 DTO/Mapper/Repository | DATA-029～DATA-033、DATA-069～DATA-071 |
| [05 可替换聊天数据源](05-network-storage/05-chat-datasource.md) | ChatDataSource 边界与归一化、ChatDataSourceConfig、Remote/Mock 数据源、实时源预留 | DATA-034～DATA-038 |
| [06 聊天 Repository 与领域模型](05-network-storage/06-chat-repository.md) | ChatRepository（加密缓存、发送状态、重试幂等、owner 复核）、领域模型、owner 来源、ChatTime | DATA-039～DATA-042 |
| [07 聊天加密与 Room 缓存](05-network-storage/07-chat-crypto-room.md) | ChatCipher、AES-256-GCM、Keystore、三表实体、ChatDao、ChatDatabase、实体映射 | DATA-043～DATA-049 |
| [08 聊天账号隔离](05-network-storage/08-chat-session.md) | ChatOwnerStore、ChatSessionGuard/清理规则与启动接线 | DATA-050～DATA-051 |
| [09 订单网络接口](05-network-storage/09-order-network.md) | OrderApi、订单 DTO/状态/动作、OrderErrorMapper、OrderDataSourceConfig、Mock 订单通知存储 | DATA-052～DATA-056 |
| [10 可替换订单数据源](05-network-storage/10-order-datasource.md) | OrderDataSource 与归一化、Remote/Mock、MockOrderStore、工厂、actor/owner 来源 | DATA-057～DATA-062 |
| [11 订单 Repository 与待确认请求](05-network-storage/11-order-repository-pending.md) | OrderRepository、MMKV pending、AES-GCM/Keystore、codec 与 owner/数据源/baseUrl 隔离 | DATA-063～DATA-068 |

## 覆盖与缺口

- 已覆盖 `lib_network` 当前全部接口、DTO、Repository、错误映射与会话存储源码，`lib_chat` 的聊天数据源、Repository、订单通知适配/Mapper/Repository、加密与 Room 缓存源码，以及 `lib_order` 的订单数据源、Repository、pending 存储/加密源码；认证、关系、菜谱、聊天与订单链路共享 DATA-004 的 Retrofit/OkHttp 实例和鉴权拦截器。
- 订单接口与 Android 数据层已定义：默认 `remote`，`-PorderDataSource=mock` 才启用进程内演示数据源（DATA-055/DEP-008）；创建请求使用持久化幂等键与 owner/数据源/baseUrl 隔离的加密 pending，超时/IO/5xx 保留待确认，明确 4xx 清理（DATA-063～DATA-068）。订单列表/详情页已接线（ARCH-020）；通知页已按订单数据源适配六类通知并可跳详情（DATA-069～DATA-071/ARCH-017），但未读/已读、红点、推送、真实后端联调、退款/配送/评价与订单编辑尚未实现。聊天/通知接口已定义并有加密本地缓存，聊天三页（`ChatFragment`/`ChatNotificationsFragment`/`ChatDetailFragment`）已接线（ARCH-017）；通知页改用订单通知 Repository，不再使用聊天 Room 通知缓存。未读/已读、送达回执与实时消息仍未实现，实时源仅为预留的不可用实现（DATA-038）；聊天 Debug 默认 Mock、Release 默认 remote，订单通知跟随 `orderDataSource`，Mock 只代表本地数据。菜谱评论删除/回复、收藏列表页、站外分享与图片压缩/上传进度尚未实现。没有统一重试队列、全链路请求去重、离线待发队列或乐观更新封装；`lib_cookbook` 的 Room 仍为空骨架（DATA-002），`lib_chat` 已有真实 Room 实现（DATA-046～DATA-048）。
- 好友关系、菜谱可见性/草稿范围、菜谱所有权、预订资格、聊天会话参与方与消息幂等均由后端判定；索引中的 `isFriend`、`visibility`、`status`、`canReserve`、`isMock` 只是展示或本地状态字段，不能作为客户端授权依据。`isMock=true` 时必须明确标识演示模式，不得当作真实同步。
