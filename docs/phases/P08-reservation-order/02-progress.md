# P08 · 菜品预订与双向订单管理 · 执行记录

- 本阶段需求源：`docs/phases/P08-reservation-order/01-requirements.md`
- 已确认业务与目标协议：[P08 订单契约](04-order-contract.md)
- 用户阶段验收：未接受
- 验收依据：未提供
- 下一动作：派发 P08-T03 预订确认弹窗、菜谱详情入口、3.9 菜品预订详情页与好友主页入口；阶段 Gate 仍未接受。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P08-RULES-01 | 维护正式验收轮次、测试数据重置与 verifier 自行安装规则；仅修改本任务批准的 7 份文档 | progress-maintainer 实施；Root 审核并顺序派发独立 verifier | Verified | R1、R2 完整验证均 PASS，R2 为最终干净轮次；Root 已技术接受，不启动 P08 业务 |
| P08-CONTRACT-01 | 归档精简订单契约，更新阶段需求/引用并完整同步当前任务；仅四份文档 | progress-maintainer 实施；Root 审核 | Verified | R1/R2 文档验证均 PASS，R2 为最终干净轮次；Root 已技术接受文档归档，业务代码及联调尚未执行 |
| P08-T01 | 后端多明细订单域与迁移：`D:\CookBookService` 内实现契约 §1～§5 后端部分（版本化迁移、六态明细与整单汇总 CLOSED、allowedActions/计数/总份数、多明细创建与幂等指纹 v2、明细动作与整单取消、expectedVersion、同事务事件与六类通知、category=order 过滤、旧入口处置、README）；不修改 Android 仓库 | deep-executor | Verified | 后端 `npm test` 109/109 PASS、`npm run check` 退出 0；R1/R8 完整干净，R2～R7 产品检查 PASS 且问题均在验证工具/覆盖侧、无实现缺陷；F01 补测闭环；Root 已技术接受 |
| P08-T02 | Android 订单网络与数据层：Order/OrderItem DTO、OrderApi、错误映射、OrderDataSource(Mock/Remote)、OrderRepository、orderDataSource 切换、幂等键持久化/账号数据源隔离、超时待确认与原键重放、分页/迟到响应；不修改 UI 页面或后端 | standard-executor | Verified | `lib_network` 70 例 + `lib_order` 38 例、`compileDebugKotlin` PASS；R1/R2 两轮独立、R2 最终干净；索引 DATA-052～068/DEP-014/TEST-030～035；非阻塞观察留待 T05/T06 |
| P08-T03 | 预订确认弹窗与预订入口：菜谱详情“加入购物车”接线、好友主页可预订菜品入口、多菜品/数量/删除/整单备注/提交中/结果待确认；不新增独立购物车/支付/配送 | standard-executor | 未开始 | 依赖：P08-T02；待派发 |
| P08-T04 | 双向订单列表与详情页：我的订单/我的销售分页与状态筛选、订单详情、逐项接受/拒绝/备货/待取货/完成/取消、整单取消、双方一致、空/加载/失败/重试、账号切换与迟到响应 | standard-executor | 未开始 | 依赖：P08-T02；待派发 |
| P08-T05 | 六类订单通知适配与跳转：schemaVersion 2 payload、category=order 筛选与分页、旧 payload 兼容、同一订单详情跳转、账号/Mock/Remote/环境隔离、不增加未读已读 | standard-executor | 未开始 | 依赖：P08-T02；待派发 |
| P08-T06 | 本地真实后端联调、代码索引增量与阶段收尾：Remote 双用户链路、契约 §7 验收矩阵、code-auditor 增量更新索引、两轮独立 verifier 完整验证与 Gate 材料 | verifier + code-auditor + progress-maintainer | 未开始 | 依赖：P08-T01～T05 完成；待派发 |

- 依赖顺序：P08-T01 → P08-T02 → P08-T03/P08-T04/P08-T05（文件作用域互斥，重叠时顺序派发）→ P08-T06。

## 当前阻塞与待决事项

- P08-T01 后端多明细订单域、P08-T02 Android 订单网络与数据层均已 Verified；订单 UI 与真实后端联调尚未实施，阶段验收仍未接受。
- 后端旧版单菜品订单已由版本化迁移与回填覆盖；本地 dev DB 实际迁移前仍必须先做一致性备份。
- 客户端订单 UI 仍为占位，P08-T03 起尚未接线。

## 用户决定与影响

- 2026-10-25：用户确认同好友多菜品/数量/备注、逐项取货闭环、待接受可取消及历史快照，完整决定见[订单契约](04-order-contract.md)；随后要求将精简契约放入本阶段并添加引用，项目既有规则只引用、不复述。本次授权范围为文档归档。

以下两条为 P08-RULES-01 当时的决定与影响记录，现行订单决定见上条。

- 2026-10-25：用户要求由 Root 先后派发独立测试 Agent，至少两轮通过；首轮问题修复复测通过后才能开第二轮，第二轮及以后有问题的轮次修复复测通过后仍须开新轮，直到某一后续完整轮次全程无问题。每轮及修复复测前清空专用测试数据并准备新数据，需要安装应用时由当轮测试 Agent 自行完成。
- 影响：详细流程统一写入 `.claude/rules/04-verification-gates.md`；保留获批最小范围及用户阶段 Gate。纯文档无数据/安装需求可注明不适用，仍需两个顺序独立验证轮次；当前 P08 业务下一动作仍等待用户授权。

## 实施结果、验证与 Gate

- P08-CONTRACT-01：新增 `04-order-contract.md`，仅保留 P08 特有业务、接口、改造/接入及验收要求；阶段需求和当前任务同步引用，原订单待确认描述已按用户决定更新。正式文档验证已完成，用户阶段验收仍为未接受。
- P08-CONTRACT-01 验证：R1 `/root/p08_contract_verify_r1`、R2 `/root/p08_contract_verify_r2` 均 PASS、复测 0、全程无问题，R2 为最终干净轮次。两轮各自完整核查四文件内容/去重、引用、UTF-8/预算、副本一致、历史与修改范围；`python -X utf8 scripts/phase_flow.py check` 与 `git --no-optional-locks diff --check` 均退出 0，仅既有 LF→CRLF 提示。
- P08-CONTRACT-01 证据：本会话上述两轮独立报告；数据清空/新造数、设备及安装均 N/A（纯文档归档，无运行数据或安装需求）。2026-10-25 Root 审阅实际契约、入口 diff 和两轮证据后技术接受为 Verified，仅指文档归档。
- P08-RULES-01 已完成明细（规则实施、R1/R2 两轮证据、2026-10-25 提交与计划登记）已完整归档至 [03-history-001.md](03-history-001.md)；结论仍为 Verified。
- P08-T01 实施结果：`D:\CookBookService` 内完成多明细订单域——`orders`/`order_items`/`order_events` 版本化迁移与旧单菜品回填；明细六态与整单 `CLOSED` 汇总；`allowedActions`、计数与总份数；多明细创建与幂等指纹 v2（兼容旧 v1）；明细动作、整单取消与 `expectedVersion`；同事务事件及六类通知（`category=order`）；旧入口 410；`ASSUMPTIONS.md`/`README.md` 同步。新增纯规则模块 `src/services/order-status.js`。
- P08-T01 正式验证：每轮由 Root 顺序新派不同独立 verifier 从头执行，数据每轮重建，安装 N/A（纯后端）。实现方与最终轮运行 `npm test` 109/109 PASS、`npm run check` 退出 0。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | 完整流程 PASS，无问题 |
| R2 | 本轮新派独立 verifier | 0 | 产品检查 PASS；verifier 夹具期望错误，非干净轮次 |
| R3 | 本轮新派独立 verifier | 0 | 产品检查 PASS；verifier 断言期望错误，非干净轮次 |
| R4 | 本轮新派独立 verifier | 0 | 产品检查 PASS；verifier 探针工具缺陷，非干净轮次 |
| R5 | 本轮新派独立 verifier | 0 | 产品检查 PASS；verifier 探针断言错误，非干净轮次 |
| R6 | 本轮新派独立 verifier | 0 | 产品检查 PASS；verifier 命令自身错误，非干净轮次 |
| R7 | 本轮新派独立 verifier | 0 | 实现无缺陷；CLOSED 筛选、备注 500 边界、seller 分页缺运行时用例（清单未跑全），转 F01 补测 |
| R8 | 本轮新派独立 verifier | 0 | 完整流程从零执行 PASS，无 FAIL/无漏项；最终干净轮次 |

- 数据重置/新数据：每轮及复测前独立创建临时 SQLite 库并重新造数，未触碰真实 `data/cookbook.db*`；测试制品为后端源码，安装 N/A（无设备或应用制品）。
- P08-T01-F01：仅修改 `test/orders.test.js`，新增/扩展 CLOSED 筛选、备注 500 边界与 seller 分页用例；测试 107→109，业务源码未改，补测已闭环。
- 2026-10-25 Root 审阅实现结果、R1～R8 轮次结论与 F01 补测后，技术接受 P08-T01 为 Verified；阶段 Gate 仍未接受，未启动 P08-T02。
- P08-T02 实施结果：`lib_network` 新增订单 DTO/API（多明细、六态、allowedActions/计数/总份数、expectedVersion、分页）、错误映射、共享 `orderDataSource` 配置（默认 remote）与订单通知存储；`lib_order` 新增 OrderDataSource 抽象、Remote/Mock 双实现、共享 Mock store、Factory、OrderRepository；加密 pending 采用 AES-GCM+Keystore，按 owner+kind+baseUrl 隔离，超时保留、明确 4xx 清理、原幂等键重放。Mock 覆盖六态/汇总/幂等/版本/取消/六类通知/双账号共享。
- P08-T02 正式验证：Root 顺序新派不同独立 verifier，每轮从头强制重跑 Gradle（R2 使用 `--rerun-tasks`）；纯 JVM 用例每例独立内存 store/临时 cipher，无持久化 App 数据，安装 N/A。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | `:lib_network:testDebugUnitTest` 70 例、`:lib_order:testDebugUnitTest` 38 例，0 failures/errors/skipped；`compileDebugKotlin` PASS；源码/索引无 FAIL |
| R2 | 本轮新派独立 verifier | 0 | 完整重跑 BUILD SUCCESSFUL，70+38 全绿；契约 §2/§3/§6 与 DTO/API/错误映射/配置/Remote/Mock/Pending/Repository/通知存储/索引逐项 PASS；最终干净轮次 |

- P08-T02 证据：两轮均 0 failures/errors/skipped；`python -X utf8 scripts/code_index_check.py check`、`python -X utf8 scripts/phase_flow.py check` 均 PASS。代码索引 DATA-052～068、DEP-014、TEST-030～035。非阻塞观察：Remote 无专项单测留待 T06 真实联调；通知存储边界仅源码核查留待 T05；Mock 幂等在演示上下文丢失时可能先报 RECIPE_NOT_FOUND（不产生第二单），留待 T03/T06 验证；版本校验先于权限的报错优先级细节。
- 2026-10-25 Root 审阅两轮独立证据后技术接受 P08-T02 为 Verified；阶段 Gate 仍未接受。

## 问题 / 修复历史及归档引用

- P08-RULES-01 两轮均未发现缺陷，无修复或复测记录。
- P08-T01 无实现缺陷修复；R2～R7 问题均位于验证工具/夹具/断言/命令侧，R7 覆盖缺口由 F01 补测关闭，R8 从头完整执行且全程无问题。
- P08-T02 两轮均未发现 FAIL，无修复或复测记录；Remote 专项单测、通知存储运行时边界、Mock 幂等演示上下文及报错优先级观察不构成 Verified 阻断，见上节。
- P08-RULES-01 已完成明细见 [03-history-001.md](03-history-001.md)。
