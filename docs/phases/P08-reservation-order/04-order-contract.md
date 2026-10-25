# P08 订单契约与实施要求

2026-10-25 用户确认的业务方案及待实施目标契约，补齐产品订单待确认项；新增能力尚未实现，进展见[阶段记录](02-progress.md)。通用规则只引用 [CLAUDE.md](../../../CLAUDE.md) 与[验证门禁](../../../.claude/rules/04-verification-gates.md)。

Android：`D:\projects\CookBook`；后端：`D:\CookBookService`。复用既有能力，先完成本地真实联调，云端沿用同一契约。

## 1. 已确认业务

### 1.1 创建与历史信息

- 好友免费预订；一单仅同一好友的多道菜。数量默认 1、正整数，重复菜品合并数量，合并/求和防溢出。
- 整单备注可选，trim 后最多 500 字，无逐菜备注。至少一道菜才可提交；成功后不修改菜品、数量或备注。
- “加入购物车”进入预订确认弹窗，可补选同好友菜品、调整数量、删除菜品。不新增独立购物车、跨提供方合单、库存扣减、限购、支付或配送。
- 创建校验好友、已发布且可访问、非本人菜谱、同一提供方；任一失败不创建部分订单、事件或通知。
- 创建时快照菜名、图片引用、数量、备注及双方 ID/名称。编辑、转草稿、删除不改快照或自动取消订单；原菜谱不可访问时仍可看订单，不绕过权限打开菜谱，缺图占位。
- 解除好友后禁止新预订，已有订单双方仍可查看并继续处理。评分、预计送达时间无真实来源，统一显示“暂无”。

### 1.2 明细流转与取消

每道菜一条明细，全部份数一起处理，不拆份接单/分批取货。`ACCEPTED` 沿用现有值，显示“备货中”。

| 当前状态 | 操作者 / 动作 | 新状态 |
| --- | --- | --- |
| `PENDING_ACCEPT` 待接受 | 提供方 / `accept` | `ACCEPTED` 备货中 |
| `PENDING_ACCEPT` | 提供方 / `reject` | `REJECTED` 已拒绝 |
| `PENDING_ACCEPT` | 预订方 / `cancel` | `CANCELLED` 已取消 |
| `ACCEPTED` | 提供方 / `ready` | `WAITING_PICKUP` 待取货 |
| `WAITING_PICKUP` | 预订方 / `complete` | `COMPLETED` 已完成 |

`REJECTED`、`CANCELLED`、`COMPLETED` 是终态，不可恢复；拒绝/取消后需重新预订。提供方不能代确认取货，不增加自动接受、超时取消或自动完成。

仅待接受明细可取消，其他明细已接受不影响它。整单取消要求全部待接受且原子成功/失败，不退化为部分取消；接单与取消并发仅一方成功。

### 1.3 整单汇总

按下列顺序取第一个满足条件的结果，由服务端计算：

1. 存在待接受明细 → `PENDING_ACCEPT`。
2. 否则存在备货中明细 → `ACCEPTED`。
3. 否则存在待取货明细 → `WAITING_PICKUP`。
4. 否则存在已完成明细 → `COMPLETED`。
5. 否则全部已拒绝 → `REJECTED`。
6. 否则全部已取消 → `CANCELLED`。
7. 否则为拒绝与取消混合 → `CLOSED`（只属于整单）。

同时显示各状态明细条数，总份数另计，如“备货中 · 1 道待取货”“已完成 · 1 道完成、1 道拒绝”，不暗示全部交付。

## 2. REST 与数据模型

以下路径省略 `/api/v1`；均需 Bearer Token，由登录信息确定身份。查询/操作成功 200，首次创建 201，同键创建重放 200。

| 方法 / 路径 | 参数或请求体 | 成功返回 |
| --- | --- | --- |
| `POST /orders` | `items`、可选 `note`；请求头 `Idempotency-Key` | `Order` |
| `GET /orders` | `role=buyer或seller`、可选 `status`、`page`、`pageSize` | `PageResponse<Order>` |
| `GET /orders/{orderId}` | 订单 ID | `Order` |
| `POST /orders/{orderId}/items/{itemId}/{action}` | `{"expectedVersion":1}`；`action` 仅 `accept/reject/ready/cancel/complete` | 更新后的完整 `Order` |
| `POST /orders/{orderId}/cancel` | `{"expectedVersion":1}` | 整单取消后的完整 `Order` |

### 2.1 创建与分页

```json
{"items":[{"recipeId":101,"quantity":2},{"recipeId":102,"quantity":1}],"note":"少放辣"}
```

- `items` 必填且非空；ID、数量为正整数。服务端再次合并重复菜品并校验结果，不只依赖客户端。
- `note` 可省略或传字符串，不传 `null`；规范化保留 trim 后字符串，省略对应空值，重试保持原内容。
- 不接受客户端指定双方身份、状态或价格。新客户端每次创建必须携带非空 `Idempotency-Key`（1～200 字符）。
- `role` 默认 `buyer`；`page` 默认 1；`pageSize` 默认 20，范围 1～50。列表按创建时间、订单 ID 倒序；`status` 筛选整单汇总状态，详情始终包含全部明细。
- 分页结构为 `{"items":[],"page":1,"pageSize":20,"total":0}`，复用 `PageResponse<T>`。

### 2.2 响应字段

```text
Order
  id: Long
  status: OrderStatus
  version: Long
  note: String?
  buyer: { id: Long, username: String, nickname: String }
  seller: { id: Long, username: String, nickname: String }
  items: OrderItem[]
  itemCounts: { total: Int, pendingAccept: Int, accepted: Int,
                waitingPickup: Int, completed: Int, rejected: Int, cancelled: Int }
  totalQuantity: Long
  allowedActions: String[]
  createdAt: String
  updatedAt: String

OrderItem
  id: Long
  recipe: { id: Long?, name: String, imageUrl: String }
  quantity: Int
  status: ItemStatus
  version: Long
  allowedActions: String[]
  createdAt: String
  updatedAt: String
```

`ItemStatus` 为 §1.2 的六种状态，`OrderStatus` 另含 `CLOSED`。`items` 不为空；`recipe.id` 可在菜谱删除后为空，名称/图片引用仍是快照，`imageUrl` 可为空字符串。时间为 UTC ISO-8601。`itemCounts` 必须包含全部字段及零值；数量求和不可溢出。评分/预计送达时间不伪装成现有 API 字段。

`allowedActions` 按访问者/状态生成：明细为对应动作，整单仅 `cancelAll` 或空数组；执行仍须校验。缺失关键字段/未知状态不得默认可操作。

### 2.3 版本、权限与错误

`expectedVersion`：明细动作取明细版本，整单取消取订单版本，初始均为 1。单项成功增加该明细及订单版本；整单取消使订单加一、各明细分别加一。不同明细独立操作，不因其他明细变化无条件冲突。

校验参与方、动作权限及 `itemId` 归属，第三方访问返回 404。错误为 `{"error":{"code":"...","message":"...","details":{}}}`，`details` 可省略。

| HTTP | code | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 空明细、数量非法/溢出、备注过长或参数错误 |
| 400 | `MIXED_SELLERS` | 多个提供方 |
| 400 | `CANNOT_ORDER_OWN_RECIPE` | 预订本人菜谱 |
| 401 | `UNAUTHORIZED` | 未登录/登录失效 |
| 403 | `NOT_FRIENDS` | 创建时不是好友 |
| 403 | `FORBIDDEN_ORDER_ACTION` | 参与方无权执行动作 |
| 404 | `RECIPE_NOT_FOUND` | 菜谱不存在/不可访问 |
| 404 | `ORDER_NOT_FOUND` | 订单不存在/非参与方 |
| 404 | `ORDER_ITEM_NOT_FOUND` | 明细不存在/不属于此订单 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 同键不同请求内容 |
| 409 | `ORDER_VERSION_CONFLICT` | 目标版本已变化 |
| 409 | `INVALID_ORDER_ITEM_STATUS` | 明细状态不允许该动作 |
| 409 | `ORDER_NOT_CANCELLABLE` | 不满足全部待接受 |

记录并测试旧请求/整单入口的兼容或停用方式，同步项目内调用；不得截取第一道菜或意外批量操作，发现外部调用方先记录兼容影响。

## 3. 幂等、事务与异常恢复

- 创建幂等键按预订方隔离。指纹包含规范化后的全部明细、数量和备注；重复菜品合并后按 `recipeId` 排序，因此顺序变化、合并前后等价请求具有同一指纹。
- 同键同内容返回原订单当前状态；同键不同内容返回冲突。创建重放不得重复写订单、事件或通知。
- 发送前保留键和规范化请求，超时/重建/恢复不换键；按账号、数据源、后端环境隔离，敏感内容落盘复用加密能力。
- 没有按幂等键查询接口；创建超时显示“结果待确认”，用原键原内容重放，不能假定结果后重新下单。
- 身份、版本和状态校验、明细更新、整单汇总/版本更新、事件及通知写入必须属于同一事务。复用现有 `BEGIN IMMEDIATE` 与带旧状态/版本条件的更新；创建资格校验和快照采集也在同一事务内。
- 状态操作超时/409 后重新读取订单。状态与版本未变可重试原操作；已变化则展示现状，不自动拿新版本强行重试。重复操作/冲突不产生第二次事件或通知。
- 按订单版本/请求归属处理迟到响应，旧列表不覆盖详情新状态；成功用完整订单更新并刷新相关入口。
- 网络或服务错误显示错误/重试，只有成功空列表显示空态；Remote 失败不得回退 Mock 或伪造成功。

## 4. 后端改造与旧数据

主要入口（相对 `D:\CookBookService`）：`src/routes/orders.js`、`src/services/orders.js`、`src/db/schema.js`、`src/db/index.js`、`src/routes/notifications.js`、`src/services/notifications.js`。

| 数据 | 职责 |
| --- | --- |
| `orders` | 双方身份/名称快照、整单备注、汇总状态、版本、幂等信息、时间 |
| `order_items` | 订单归属、可空菜谱关联、名称/图片快照、数量、明细状态/版本、时间 |
| `order_events` | 操作人、订单与受影响明细、前后状态、时间；保留旧审计历史 |
| `notifications` | 扩展既有通知事件，不另建一套订单通知系统 |

抽取可单独测试的状态汇总函数；整单状态/计数必须与明细一致。迁移要求：

1. 版本化迁移，准备一致性备份与恢复说明；不得删旧库重建。
2. 旧单菜品订单转成一条明细，兼容数量为 1；保留原订单 ID、双方信息、备注、快照、时间、状态及幂等键。旧 `ACCEPTED` 不改值。
3. 原菜谱 ID 已为空也保留明细快照。原事件和通知的 `orderId` 仍指向同一订单，迁移不生成通知。
4. 保留旧指纹并标明计算版本，不以新算法直接覆盖。历史请求信息不足以可靠验证旧键时返回明确的不可重放冲突，不猜内容或创建第二单。
5. 安全处理现有状态 CHECK、非空约束和外键；验证行数、关联及外键完整性。重复迁移/启动不得重复生成明细或事件。

## 5. 订单通知与 P07 适配

保留消息页独立预订通知入口，不插入好友聊天正文。

| type | 接收方 | 含义 |
| --- | --- | --- |
| `order_created` | 提供方 | 新订单 |
| `order_accepted` | 预订方 | 指定菜品接受 |
| `order_rejected` | 预订方 | 指定菜品拒绝 |
| `order_ready` | 预订方 | 指定菜品备好 |
| `order_cancelled` | 提供方 | 指定菜品/整单取消 |
| `order_completed` | 提供方 | 指定菜品确认取货 |

一次成功操作生成一条通知；创建/整单取消可含多明细，其余一条。`order_completed` 不代表其他菜品完成；文案/快照对应本次事件，点击查看订单最新详情。

新 payload：

```text
schemaVersion: 2
orderId: Long
orderStatus: OrderStatus
actor: { id: Long, nickname: String }
items: [{ itemId: Long, recipeId: Long?, recipeName: String,
          quantity: Int, fromStatus: ItemStatus?, toStatus: ItemStatus }]
```

- `GET /notifications` 保留默认行为，新增 `category=order` 筛选上述六类事件；筛选同时作用于 `items/total`，仍为 `page/pageSize` 分页。
- 适配实际 `type/payload/read/readAt/createdAt` 与 P07 的 `title/body/orderId`；保留游标接口时内部转换页码，不透传游标，不把过滤后空页判为末页。
- 兼容旧单菜品 payload，旧 `orderId` 链接继续有效。缓存按账号、Mock/Remote、后端环境隔离，避免通知 ID 碰撞，保留现有聊天记录与加密能力。
- 订单通知跟随订单数据源，不能被聊天默认 Mock 开关遮蔽。不增加未读计数、已读回执、自动标记已读或系统推送；进入页面、操作后和手动刷新时获取最新数据。

## 6. Android 接入与实施顺序

复用 `ApiClient/NetworkConfig`、会话、分页、路由及 UI 组件。`OrderRepository` 下接 `OrderDataSource` 的 Mock/Remote 实现，页面不直连 Retrofit/模拟数据；数据层与通知适配器避免 `lib_order/lib_chat` 循环依赖。

数据访问契约至少覆盖：

```text
createOrder(items, note, idempotencyKey) -> Order
listOrders(role, status?, page, pageSize) -> PageResponse<Order>
getOrder(orderId) -> Order
acceptItem / rejectItem / readyItem / cancelItem / completeItem
  (orderId, itemId, expectedVersion) -> Order
cancelOrder(orderId, expectedVersion) -> Order
```

页面与复用点（相对 Android 根目录）：

| 交付 | 现有入口 / 接入要求 |
| --- | --- |
| 菜谱预订 | `lib_cookbook` 的 `CookBookDetailFragment` 替换预订不可用提示 |
| 好友可预订菜品 | `UserRepository.profile/recipes`、好友主页；按权限展示，提交仍由后端验证 |
| 确认弹窗 | 多选、数量、删除、整单备注、提交中与结果待确认 |
| 双向订单与详情 | `lib_order` 的 `OrderFragment`；独立分页、完整明细与动作，区分整单/单项取消 |
| 通知 | `lib_chat` 的 `ChatNotificationsFragment` 跳转同一订单 |
| 路由 | 复用 `RoutePath.PAGE_ORDER/PAGE_ORDER_INFO/PAGE_ORDER_DETAIL`，不新增重复入口 |

重建/返回保留状态；切换账号清除旧页面数据和在途归属；迟到响应、待确认请求按 §3 处理，资源/权限变化如实反馈。

`orderDataSource=mock|remote`：本地联调/发布用 Remote；Mock 标明演示，双方共享同一存储，遵守同一权限、状态、幂等、版本及通知契约。复用 `apiBaseUrl`，初始化网络客户端前配置本地/云端地址，不写死地址；相同契约切换环境，不等待云部署才联调。

依赖顺序：固化契约及兼容方案 → 状态模型/迁移 → 后端接口与通知事务 → 客户端数据层 → 页面及 P07 通知 → 本地集成验证。旧数据迁移和新逻辑均先用定向测试证明业务分支。

## 7. P08 验收矩阵

| 范围 | 必验结果 |
| --- | --- |
| 创建 | 多菜/数量/备注、重复合并正确；空单、非法/溢出数量及 §1.1 所有创建资格拒绝路径；失败无部分业务 |
| 幂等/恢复 | 同键同单，排序/合并等价；改数量/备注冲突；服务端已创建但客户端超时，原请求重试无第二单 |
| 权限 | 第三方不可读写；双方不能执行对方操作；不能拼接其他订单明细 ID |
| 明细闭环 | 完整接受/备货/取货流程、拒绝/取消终态；同菜多份整体处理 |
| 取消/并发 | 已接单不可取消，其他待接受可取消；整单条件/原子性；同版本接单与取消唯一成功，无重复事件/通知 |
| 汇总 | 待接受+待取货、备货中+待取货、完成+拒绝、拒绝+取消、全拒绝、全取消与计数均正确 |
| 快照/迁移 | 编辑/草稿/删除/解除好友；旧 ID/事件/通知/指纹保留，空菜谱 ID、不可还原指纹及重复迁移 |
| 通知 | 六类事件接收方/明细正确，整单取消一条；旧 payload、分页/分类、链接、账号/环境隔离 |
| 客户端 | 重建/返回/账号切换不丢状态、串账号或回退；空/加载/失败/重试/提交/待确认、长菜名和大字体 |
| 模式 | Remote 真实请求本地后端，断网不回退；Mock 有标识；两种模式分别记录结果 |

人工链路：A 提供、好友 B 下多菜品单、C 越权；逐项接受/拒绝/取消/备好/取货，核对同单、混合终态和通知，另测整单取消及异常。验证相关模块测试/构建、本地 Remote、真机 UI；云端未部署单列未验证。
