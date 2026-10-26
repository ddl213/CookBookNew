# P08 · 已完成历史归档 002

- 归档范围：P08-T05 六类订单通知适配的实施、F01、R1/R2 轮次、证据与非阻断观察，原文自 `02-progress.md` 完整迁入，结论与证据未改写。

## P08-T05 六类订单通知适配

- P08-T05 实施结果：`lib_network` 新增 `OrderNotificationDto`/payload/actor/item/buyer DTO 与 `NotificationApi.listOrderNotifications(category="order", page, pageSize)`；`lib_chat` 新增 `OrderNotificationMapper` 映射六类 `order_*` 与旧单菜品 payload，`OrderNotificationRepository` 跟随 `OrderDataSourceConfig` 选择 Remote（category=order 页码、失败不回退 Mock）/Mock（DATA-056 共享事件），按 owner/数据源/baseUrl 缓存隔离；通知页切换至该 Repository，支持首屏/下拉刷新、分页与空页跳过、Mock 横幅、同一订单详情跳转与 onResume 刷新；仍未增加未读/已读。
- P08-T05 正式验证：Root 顺序新派不同独立 verifier，每轮从头强制 `--rerun-tasks`；纯 JVM 用例使用独立内存 fake，无 App 持久化数据，安装 N/A。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | `--rerun-tasks` 构建成功；`:lib_network:testDebugUnitTest` 70 例、`:lib_chat:testDebugUnitTest` 102 例（P07 83 仍过），均 0 failures/errors/skipped；`:app:compileDebugKotlin` PASS；DTO/映射/Repository/页面/索引静态 PASS；F1 旧游标契约风险未记录转 F01；UI/真实后端 NOT RUN |
| F01 | 本轮新派独立 verifier | 1 | 仅补 `NotificationApi` KDoc 并更新索引 DATA-030/ARCH-017；强制重跑通过，静态复核 PASS（不计新轮） |
| R2 | 本轮新派独立 verifier | 0 | 全新 verifier 强制重跑 245 tasks，70/102 全绿 0 failures/errors/skipped；F01 KDoc/索引复核 PASS；完整清单无 FAIL；最终干净轮次（限定代码/构建/静态/索引范围）；UI/真实后端 NOT RUN 留 T06 |

- P08-T05-F01：为 P07 旧 `listNotifications(limit, cursor)` 与后端页码契约不一致补充 KDoc，并在索引 DATA-030/ARCH-017 记录遗留风险——旧接口仅 P07 兼容、P08 未使用、不得透传 cursor；后续复用需在客户端内部转页码。
- P08-T05 证据：索引 DATA-030/056/069/070/071、ARCH-017/008/020、TEST-038/039；`python -X utf8 scripts/code_index_check.py check` 与 `python -X utf8 scripts/phase_flow.py check` 均 PASS。数据重置/新数据为每例独立内存 fake，安装 N/A；UI 真机与真实后端联调留 T06。
- P08-T05 非阻断观察（不阻断 Verified，指向 T06）：O1 Mock 通知受 200 条上限约束；O2 Mock 横幅沿用聊天文案；O3 触底阈值沿用聊天模式；O4 DTO `read` 默认值不影响当前消费；O5 `04-chat-network.md` 12,138 bytes，低于硬限制、高于 12 KiB 建议线；O6 进度状态已由本任务更新为 Verified（已关闭）。
- 2026-10-25 Root 技术接受 P08-T05 为 Verified（不含 UI 与真实后端联调）；阶段 Gate 仍未接受。
