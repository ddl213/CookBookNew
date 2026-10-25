# 08-05 · 订单通知适配测试

> 返回分类首页：[08-testing-tools.md](../08-testing-tools.md)。通用配置见 [01-infrastructure.md](01-infrastructure.md)，聊天/加密测试见 [03-chat-storage-tests.md](03-chat-storage-tests.md)，订单数据层测试见 [04-order-tests.md](04-order-tests.md)。

**收录范围：**`P08-T05` 订单通知 DTO → `ChatNotification` 纯映射，以及订单通知 Repository 的数据源选择、分页、账号/环境隔离与失败路径。

**索引原则：**测试通过只证明所列范围；全部为纯 JVM/内存测试，不访问真实后端、Room、MMKV 或 Android Keystore，不代表 UI 或阶段验收。

**计数基线（P08-T05）：**`lib_chat` 共 102 例（P07-T04 的 83 例 + 本子表 19 例）；本子表 `OrderNotificationMapperTest` 10 例、`OrderNotificationRepositoryTest` 9 例。`lib_network` 共 70 例（含 P08-T02 订单 27 例，见 [04-order-tests.md](04-order-tests.md)）。计数只随实际源码测试方法同步，不代表已执行通过。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| TEST-038 | `OrderNotificationMapperTest`（10 例） | 验证订单通知纯映射：六类 `order_*` 中文标题/正文；显式 title/body 优先；旧单菜品 payload 用 `recipeName` 与 buyer 昵称；buyer 昵称空回退用户名；未知 type 通用文案；payload 缺失安全兜底；`id<=0` 返回 null；顶层 orderId 优先于 payload 并用正数；payload orderId 兜底；明细最多两项并追加“等”。 | 纯 JVM JUnit4，无 Android/Robolectric/Coroutines；执行 `./gradlew :lib_chat:testDebugUnitTest --tests com.rhys.chat.data.OrderNotificationMapperTest`。只覆盖 DATA-070 纯函数，不覆盖网络/Store 读取、Fragment 渲染、真实后端字段或通知已读状态；断言含中文文案，改文案需同步测试。 | `subModules/lib_chat/src/test/java/com/rhys/chat/data/OrderNotificationMapperTest.kt` | 被测入口 `subModules/lib_chat/src/main/java/com/rhys/chat/data/OrderNotificationMapper.kt`（DATA-070）；DTO 见 DATA-069；页面消费见 ARCH-017。 |
| TEST-039 | `OrderNotificationRepositoryTest`（9 例） | 验证通知 Repository：Remote 以 `category=order` 拉取两页并透传页码/页大小；Mock 按接收方隔离、按时间倒序分页；`isMock` 跟随 `OrderDataSourceConfig`；未登录不调 API 抛 `UNAUTHORIZED`；Remote 失败映射可读错误且不回退 Mock；401 原样传播；页码/页大小归一化为 `>=1` 与 `1..50`；缓存按 owner/数据源/baseUrl 隔离并在变化后失效；其他接收方得到空页。 | 纯 JVM JUnit4 + `runBlocking`；执行 `./gradlew :lib_chat:testDebugUnitTest --tests com.rhys.chat.data.OrderNotificationRepositoryTest`。使用 `FakeNotificationApi` 与独立 `InMemoryOrderNotificationStore`，不访问真实网络、Room 或 MMKV；不覆盖真实后端契约、UI 刷新/跳转、通知未读/已读或跨进程 Mock 共享。 | `subModules/lib_chat/src/test/java/com/rhys/chat/data/OrderNotificationRepositoryTest.kt` | 被测入口 `subModules/lib_chat/src/main/java/com/rhys/chat/data/OrderNotificationRepository.kt`（DATA-071）；接口 DATA-030、配置 DATA-055、Mock 通知存储 DATA-056。 |

> 跨分类引用：订单通知 DTO/Mapper/Repository 见 DATA-069～DATA-071；通知页与详情跳转见 ARCH-017/ARCH-020；`ChatNotification` 领域模型与聊天 Repository 见 DATA-039～DATA-042；订单数据层测试见 TEST-030～TEST-037；`lib_chat` 构建与测试配置见 DEP-013。
>
> 未覆盖：没有通知页 Fragment/Robolectric UI 测试（列表渲染、下拉/分页、Mock 横幅、`onResume` 刷新、详情跳转与旧通知提示）；没有真实后端 `category=order` 契约、`read/readAt` 行为、未读/已读/红点/推送测试。
