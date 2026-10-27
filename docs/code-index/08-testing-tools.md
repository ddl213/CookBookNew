# 08 · 测试与辅助工具

> 本类盘点状态：已盘点。已核对 `lib_android_common` 的 Robolectric 测试配置与框架测试，以及各模块模板测试；模板测试不计为业务覆盖。2026-10-22 `P01-T02` 增量登记 `lib_auth`、`lib_network` 的纯 JVM 测试；`P02-T01` 增量登记 `lib_relation` 关系模型与 `lib_network` 分页/Response 校验测试；`P03-T01` 增量登记菜谱表单校验与上传媒体规则测试；`P03-T05` 增量登记真实关系页 View 重建测试与其 Robolectric 配置；`P04-T02` 增量登记评论/收藏/分享参数校验与新增错误码映射测试，并因文件超过 12 KiB 按功能拆为两个子表；`P05-T02` 增量登记公开菜谱/好友动态查询参数归一化测试；`P06-T02` 增量登记创作者聚合测试（TEST-017）；`P07-T01/T02` 增量登记聊天错误映射、游标 DTO、数据源配置/Mock、Repository 加密缓存、AES-GCM 与账号隔离测试，并拆出聊天专用子表（TEST-018～TEST-026）；`P07-T03/T04` 将 Mock 稳定会话 ID、会话时间合并不回退、retry owner 复核纳入 TEST-021/TEST-023，并新增聊天共享 UI/路由参数纯函数测试（TEST-027～TEST-029）；`P07-T04` 计数同步为 `MockChatDataSourceTest` 16 例、`ChatRouteArgsTest` 7 例，`lib_chat` 共 83 例（不含夹具）；`P08-T02` 增量登记订单错误映射、数据源配置/Mock、Repository 委托/失败、创建幂等与待确认恢复、工厂测试，并拆出订单专用子表（TEST-030～TEST-035）；`P08-T03` 将 `UserProfileActionResolverTest` 更新为 5 例（TEST-007），新增预订草稿 `ReservationDraftTest` 11 例（TEST-036），并记录 `lib_order` 49 例、`lib_relation` 20 例的实际测试计数；`P08-T04` 新增订单列表/详情共享纯逻辑 `OrderUiSupportTest` 16 例（TEST-037），`lib_order` 基线更新为 65 例，T03/T04 计数分别列明；`P08-T05` 新增订单通知映射 `OrderNotificationMapperTest` 10 例与通知 Repository `OrderNotificationRepositoryTest` 9 例（TEST-038/TEST-039），拆出订单通知测试子表，`lib_chat` 基线更新为 102 例；2026-10-26 `P09-T01` 增量登记资料更新参数校验（TEST-040）；`P09-T02`～`P09-T05` 未新增自动化测试，我的收藏、资料编辑与设置页面组目前仅有构建验证，待正式 UI 验证。

**收录范围：**现有单测工具、Fixture、测试基类、mock/脚本/局部验证入口。

**索引原则：**不因为登记而执行全量测试；注明可安全执行的范围及输入前提。测试通过只证明该测试范围，不代表阶段验收。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 测试基础设施与框架测试](08-testing-tools/01-infrastructure.md) | Robolectric/Android 资源配置、Adapter/列表/刷新套件、模块模板测试 | TEST-001～TEST-004 |
| [02 业务能力回归测试](08-testing-tools/02-business-tests.md) | 认证、网络配置、关系、菜谱表单/上传、公开/feed 参数归一化、社交参数与错误码、创作者聚合、资料更新参数、真实 Fragment 返回 | TEST-005～TEST-017、TEST-040 |
| [03 聊天、加密与账号隔离测试](08-testing-tools/03-chat-storage-tests.md) | 聊天错误映射、游标 DTO、数据源配置/Mock、Repository 加密缓存与重试、AES-GCM、SessionGuard、聊天共享 UI/路由参数纯函数、测试夹具 | TEST-018～TEST-029 |
| [04 订单数据层、预订草稿与订单 UI 纯逻辑测试](08-testing-tools/04-order-tests.md) | 订单错误映射、数据源配置/Mock、Repository 委托/失败、创建幂等与待确认恢复、数据源工厂、预订草稿、订单列表/详情共享纯逻辑 | TEST-030～TEST-037 |
| [05 订单通知适配测试](08-testing-tools/05-order-notification-tests.md) | 订单通知 DTO 映射与通知 Repository 分页/数据源/账号隔离 | TEST-038～TEST-039 |

## 覆盖与缺口

- 已覆盖 `lib_android_common` 框架测试、`lib_auth`/`lib_network`/`lib_relation`/`lib_cookbook` 的纯 JVM 业务测试、`lib_relation` 的真实 Fragment 返回测试、`lib_chat` 的数据源、Repository、加密、账号隔离、聊天共享 UI/路由参数与订单通知映射/Repository 测试，以及 `lib_order` 的订单错误映射、数据源配置/Mock、Repository 委托/失败、创建幂等、pending 隔离与预订草稿测试。资料更新目前仅覆盖 `normalizedProfile` 纯函数（TEST-040），未覆盖 `SessionManager.updateUser` 的账号切换竞态或真实网络。
- 菜谱详情页、评论/收藏/分享目前只有参数校验与错误映射单测，没有 Detail Fragment 的 Robolectric/UI 测试；`ShareFriendDialog` 的分页、选择与失败分支也未覆盖。首页/搜索页目前同样没有 Robolectric/UI 测试，仅有 `normalizedFilter` 参数归一化覆盖；创作者/公开菜谱页仅有 `CreatorAggregator` 纯函数测试，缺少分页、标签切换与 View 重建的 UI 测试。
- 聊天三页已接线，测试覆盖数据层、共享 UI/路由参数纯函数（TEST-018～TEST-029）与订单通知映射/Repository（TEST-038～TEST-039），但没有 Fragment/Robolectric UI 测试（列表/通知/详情渲染、分页、发送/重试、权限分支、通知点击跳转、View 重建）；真实后端契约、Keystore 真机密钥、Room 迁移与多进程账号切换均未覆盖，实时消息与通知未读/已读/推送仍不可用，UI 仍需手工验收。
- 订单数据层、预订草稿与订单 UI 纯逻辑测试全部为纯 JVM/内存测试，没有订单/预订 Fragment 或 Robolectric UI 测试（含确认弹窗、3.9 页面、订单列表/详情渲染、筛选分页、入口显隐与 View 重建）、真实后端契约测试、MMKV 持久化测试或 Android Keystore 真机密钥测试；`MockOrderDataSourceTest` 只证明 Mock 行为，`ReservationDraftTest` 与 `OrderUiSupportTest` 只证明纯逻辑，不代表 Remote 契约、真实同步或 UI 已验收。`lib_order` 计数为 65 例（P08-T02 37 + P08-T03 11 + P08-T04 16 + 模板 1）。
- P09 新增/接线的“我的”资料编辑页、我的收藏列表页与设置/隐私/通知/帮助页没有 Fragment/Robolectric UI 测试或契约测试，目前只有构建验证；正式 UI 验证随后进行，不能把构建通过当作页面验收。
- 各模块仍保留模板测试（TEST-004），不作为功能已验证的证据；未登记任何全量回归或性能/压力测试入口。
