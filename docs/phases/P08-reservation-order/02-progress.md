# P08 · 菜品预订与双向订单管理 · 执行记录

- 本阶段需求源：`docs/phases/P08-reservation-order/01-requirements.md`
- 已确认业务与目标协议：[P08 订单契约](04-order-contract.md)
- 用户阶段验收：未接受
- 验收依据：未提供
- 下一动作：派发 P08-T06 本地真实后端联调、真机 UI 验收与阶段收尾；阶段 Gate 仍未接受。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P08-RULES-01 | 维护正式验收轮次、测试数据重置与 verifier 自行安装规则；仅修改本任务批准的 7 份文档 | progress-maintainer 实施；Root 审核并顺序派发独立 verifier | Verified | R1、R2 完整验证均 PASS，R2 为最终干净轮次；Root 已技术接受，不启动 P08 业务 |
| P08-CONTRACT-01 | 归档精简订单契约，更新阶段需求/引用并完整同步当前任务；仅四份文档 | progress-maintainer 实施；Root 审核 | Verified | R1/R2 文档验证均 PASS，R2 为最终干净轮次；Root 已技术接受文档归档，业务代码及联调尚未执行 |
| P08-T01 | 后端多明细订单域与迁移：`D:\CookBookService` 内实现契约 §1～§5 后端部分（版本化迁移、六态明细与整单汇总 CLOSED、allowedActions/计数/总份数、多明细创建与幂等指纹 v2、明细动作与整单取消、expectedVersion、同事务事件与六类通知、category=order 过滤、旧入口处置、README）；不修改 Android 仓库 | deep-executor | Verified | 后端 `npm test` 109/109 PASS、`npm run check` 退出 0；R1/R8 完整干净，R2～R7 产品检查 PASS 且问题均在验证工具/覆盖侧、无实现缺陷；F01 补测闭环；Root 已技术接受 |
| P08-T02 | Android 订单网络与数据层：Order/OrderItem DTO、OrderApi、错误映射、OrderDataSource(Mock/Remote)、OrderRepository、orderDataSource 切换、幂等键持久化/账号数据源隔离、超时待确认与原键重放、分页/迟到响应；不修改 UI 页面或后端 | standard-executor | Verified | `lib_network` 70 例 + `lib_order` 38 例、`compileDebugKotlin` PASS；R1/R2 两轮独立、R2 最终干净；索引 DATA-052～068/DEP-014/TEST-030～035；非阻塞观察留待 T05/T06 |
| P08-T03 | 预订确认弹窗与预订入口：菜谱详情“加入购物车”接线、好友主页可预订菜品入口、多菜品/数量/删除/整单备注/提交中/结果待确认；不新增独立购物车/支付/配送 | standard-executor | Verified | lib_order 49 + lib_relation 20、app 编译 PASS；R1/R2 独立、R2 最终干净；UI 真机 NOT RUN 留 T06 |
| P08-T04 | 双向订单列表与详情页：我的订单/我的销售分页与状态筛选、订单详情、逐项接受/拒绝/备货/待取货/完成/取消、整单取消、双方一致、空/加载/失败/重试、账号切换与迟到响应 | standard-executor | Verified | `lib_order` 65 例 0 failures/errors/skipped、`lib_mine`/`app` 编译 PASS；R1→F01→R2，R2 为最终干净轮次（限定代码/构建/静态范围）；UI 与 F01 运行时留 T06 |
| P08-T05 | 六类订单通知适配与跳转：schemaVersion 2 payload、category=order 筛选与分页、旧 payload 兼容、同一订单详情跳转、账号/Mock/Remote/环境隔离、不增加未读已读 | standard-executor | Verified | `lib_network` 70 + `lib_chat` 102（P07 83 仍过）、`app` 编译 PASS；R1→F01→R2，R2 为最终干净轮次（限定代码/构建/静态/索引）；UI 与真实后端 NOT RUN 留 T06 |
| P08-T06 | 本地真实后端联调、代码索引增量与阶段收尾：Remote 双用户链路、契约 §7 验收矩阵、code-auditor 增量更新索引、两轮独立 verifier 完整验证与 Gate 材料 | verifier + code-auditor + progress-maintainer | 未开始 | 依赖：P08-T01～T05 完成；待派发 |

- 依赖顺序：P08-T01 → P08-T02 → P08-T03/P08-T04/P08-T05（文件作用域互斥，重叠时顺序派发）→ P08-T06。

## 当前阻塞与待决事项

- P08-T01 后端订单域、P08-T02 网络与数据层、P08-T03 预订弹窗/入口、P08-T04 双向订单列表与详情、P08-T05 六类订单通知适配均已 Verified；P08-T06 尚未实施，阶段验收仍未接受。
- 后端旧版单菜品订单已由版本化迁移与回填覆盖；本地 dev DB 实际迁移前仍必须先做一致性备份。
- 订单列表/详情 UI 真机运行、F01 失败回查时序运行时行为、极端字体/小屏尚未验证，留 T06。

## 用户决定与影响

- 2026-10-25：用户确认同好友多菜品/数量/备注、逐项取货闭环、待接受可取消及历史快照，完整决定见[订单契约](04-order-contract.md)；随后要求将精简契约放入本阶段并添加引用，项目既有规则只引用、不复述。本次授权范围为文档归档。

以下两条为 P08-RULES-01 当时的决定与影响记录，现行订单决定见上条。

- 2026-10-25：用户要求由 Root 先后派发独立测试 Agent，至少两轮通过；首轮问题修复复测通过后才能开第二轮，第二轮及以后有问题的轮次修复复测通过后仍须开新轮，直到某一后续完整轮次全程无问题。每轮及修复复测前清空专用测试数据并准备新数据，需要安装应用时由当轮测试 Agent 自行完成。
- 影响：详细流程统一写入 `.claude/rules/04-verification-gates.md`；保留获批最小范围及用户阶段 Gate。纯文档无数据/安装需求可注明不适用，仍需两个顺序独立验证轮次；当前 P08 业务下一动作仍等待用户授权。

## 实施结果、验证与 Gate

- P08-CONTRACT-01 已完成明细（契约归档、R1/R2 独立文档验证与 2026-10-25 技术接受）已完整归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified，仅指文档归档。
- P08-RULES-01 已完成明细（规则实施、R1/R2 两轮证据、2026-10-25 提交与计划登记）已完整归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified。
- P08-T01 已完成明细（后端多明细订单域、R1～R8 轮次、数据重置与 F01 补测、2026-10-25 技术接受）已完整归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified，阶段 Gate 未接受。
- P08-T02 实施与 R1/R2 验证明细已归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified（网络与数据层，R2 最终干净）。
- P08-T03 实施与 R1/R2 验证明细及 O1～O5 观察已归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified（预订弹窗/入口，UI 留 T06）。
- P08-T04 已完成明细（实施、F01、R1/R2 轮次、证据、O1～O9 观察与 2026-10-25 技术接受）已完整归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified（不含 UI 与 F01 运行时），UI 与 F01 运行时留 T06。
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

## 问题 / 修复历史及归档引用

- P08-RULES-01 两轮均未发现缺陷，无修复或复测记录。
- P08-T01 无实现缺陷修复；R2～R7 问题均位于验证工具/夹具/断言/命令侧，R7 覆盖缺口由 F01 补测关闭，R8 从头完整执行且全程无问题。
- P08-T02 两轮均未发现 FAIL，无修复或复测记录；Remote 专项单测、通知存储运行时边界、Mock 幂等演示上下文及报错优先级观察不构成 Verified 阻断，见上节。
- P08-T03 两轮均未发现 FAIL，无修复或复测记录；O1～O5 为非阻断观察，UI 真机与极端字体/IME/小屏确认留 T06。
- P08-T04 两轮未发现 FAIL；R1 发现 R3 超时未回查、R4 409 后按钮提前解锁两处非 FAIL 风险，由 F01 修复并同轮复测通过，R2 从头完整执行且全程无问题。
- P08-T05 两轮未发现 FAIL；R1 发现旧游标接口契约风险未记录，由 F01 补 KDoc 与索引并同轮复测通过，R2 从头完整执行且全程无问题。
- P08-RULES-01、P08-CONTRACT-01、P08-T01、P08-T04 已完成明细见 [03-history-001.md](03-history-001.md)。
