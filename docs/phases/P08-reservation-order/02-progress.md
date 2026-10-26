# P08 · 菜品预订与双向订单管理 · 执行记录

- 本阶段需求源：`docs/phases/P08-reservation-order/01-requirements.md`
- 已确认业务与目标协议：[P08 订单契约](04-order-contract.md)
- 用户阶段验收：已接受
- 验收依据：2026-10-26 用户明确接受 P08 阶段；T01～T06 均 Verified，T06 R2 为最终干净轮次，详见下方。
  - 依据：T06 R2 全新独立 verifier 在真机完成 Remote/Mock 双账号 A～O 全链路（下单/接单/备货/取货/拒单/取消/CLOSED/整单取消/六类通知/失败重试/版本冲突/权限/账号隔离/大字体/键盘）全 PASS；后端 109 例、lib_network 70、lib_order 65、lib_chat 102、lib_relation 20；logcat 无 FATAL/ANR。
  - 残余风险：未真实演练“服务端已创建但客户端超时”的幂等重放；未部署云端；未做多设备真并发；ROM IME 变体/1.5+ 超大字体未覆盖；Mock 进程内且通知最多 200 条；P07 旧游标通知接口仅遗留兼容；当前 3000 开发后端仍为旧代码，手动使用需备份并重启迁移。
  - 既有技术验收与证据引用保留于本文件及 [03-history-001.md](03-history-001.md)、[03-history-002.md](03-history-002.md)。
- 下一动作：P08 阶段 Gate 已关闭，已按 `02-stage-lifecycle.md` 执行 `python scripts/phase_flow.py advance --user-accepted` 切换至 P09；P09 仅等待用户授权，不启动业务实现。

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
| P08-T06 | 本地真实后端联调、代码索引增量与阶段收尾：Remote 双用户链路、契约 §7 验收矩阵、code-auditor 增量更新索引、两轮独立 verifier 完整验证与 Gate 材料 | verifier + code-auditor + progress-maintainer | Verified | R1 两 FAIL（旧 `_nav*` 路由、弹窗键盘塌缩）→ F02 修复 → R2 全新独立 verifier、全新测试库/账号/数据、亲自安装 Remote/Mock，完整 A～O 全 PASS；Remote/Mock 真机、正常增量构建路由资产、键盘 1.0/1.3/小屏/长备注、失败/重试/幂等/版本冲突/权限/账号隔离均通过；证据 `build/p08-ui-smoke/r2/`；2026-10-26 Root 技术接受 |

- 依赖顺序：P08-T01 → P08-T02 → P08-T03/P08-T04/P08-T05（文件作用域互斥，重叠时顺序派发）→ P08-T06。

## 当前阻塞与待决事项

- P08-T01～T06 均已 Verified；无活动实现阻塞。2026-10-26 用户明确接受 P08 阶段，阶段 Gate 已关闭并切换至 P09。
- 后端旧版单菜品订单已由版本化迁移与回填覆盖；本次联调使用独立 3002 测试库，3000 开发服务与 dev DB 未触碰。
- T06 非阻断覆盖缺口见“实施结果、验证与 Gate”节；P07 旧游标接口仅为遗留兼容。

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
- P08-T05 实施与 R1/R2 验证明细已归档至 [03-history-002.md](03-history-002.md)；结论仍为 Verified（通知适配，UI/真实后端留 T06）。
- P08-T06 实施结果：code-auditor 增量更新 6 份索引；F02 重构预订弹窗——顶部居中全高悬浮窗、`BottomSheetBehavior` EXPANDED/skipCollapsed、`NestedScrollView` 滚动内容区 + 固定底部提交栏、按可用高度收缩滚动区、备注聚焦自动滚动；8 个受跟踪 `_nav*` 路由资产随本任务提交。
- P08-T06 正式验证：Root 顺序派发全新 verifier；每轮使用独立 3002 测试库与 A/B/C 新账号，`pm clear` 后重新造数，Remote/Mock 安装由当轮 verifier 亲自执行；3000 开发服务与 dev DB 未触碰。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | 正常增量构建 APK 内 `_nav*` 为旧版，缺 action://3005 与 action://6001（FAIL-1）；预订弹窗备注键盘弹出后塌缩、提交不可达（FAIL-2）；其余链路未完成全矩阵 |
| F02 | 本轮新派独立 verifier | 1 | 弹窗重构后真机 1.0/1.3 字体、小屏、键盘开合自测通过；证据 `build/p08-ui-smoke/fix-f02/`（不计新轮） |
| R2 | 本轮新派全新独立 verifier | 0 | 全新测试库/账号/数据，亲自安装 Remote/Mock；A～O 全 PASS；FAIL-1/FAIL-2 复测 PASS；正常增量构建 `_navorder` 含 2004/3005/6001；键盘 1.0/1.3、小屏、长备注不塌缩且提交可达；Remote 双账号下单/接单/备货/取货、拒单+取消 CLOSED、整单取消、通知、失败重试/幂等、版本冲突、权限、账号隔离、大字体 PASS；Mock 双账号、六类通知、演示横幅、后端订单数不增加、重启清空 PASS；logcat Remote/Mock 无 FATAL/AndroidRuntime/ANR；最终干净轮次；证据 `build/p08-ui-smoke/r2/` |

- P08-T06 R1 根因与 F02 修复：FAIL-1 为此前提交误将 KSP 生成资产还原为旧版，正常增量构建不会重写资产；处理为强制重新生成、解密核对 action 集合后保留正确文件。FAIL-2 为底部弹层在 IME 弹出后可用高度不足、内容区不可滚动；F02 改为全高悬浮窗 + 可收缩 `NestedScrollView` + 固定底部提交栏并自动滚动备注。
- 路由资产规则：`app/src/main/assets/nav/_nav*` 是受跟踪的 KSP 生成资产；路由变更后必须强制重新生成、解密核对 action 集合再提交；禁止用 `git checkout` 还原旧版。本次 8 个资产随 P08-T06 提交。
- P08-T06 数据与安装：R1/R2 各自使用独立 3002 测试库与 A/B/C 新账号，每轮及复测前 `pm clear` 并重新造数；Remote/Mock 由当轮 verifier 亲自安装；3000 开发服务与 dev DB 未触碰；测试数据与临时库已清理。
- P08-T06 非阻断风险：未覆盖“服务端已创建但客户端超时”的真幂等重放、云端部署、多设备并发、ROM IME 变体、1.5+ 超大字体；Mock 通知仍受 200 条上限约束；P07 旧游标接口仅为遗留兼容。
- 2026-10-26 Root 技术接受 P08-T06 为 Verified；同日用户明确接受整个 P08 阶段，阶段 Gate 关闭并切换 P09。

## 问题 / 修复历史及归档引用

- P08-RULES-01 两轮均未发现缺陷，无修复或复测记录。
- P08-T01 无实现缺陷修复；R2～R7 问题均位于验证工具/夹具/断言/命令侧，R7 覆盖缺口由 F01 补测关闭，R8 从头完整执行且全程无问题。
- P08-T02 两轮均未发现 FAIL，无修复或复测记录；Remote 专项单测、通知存储运行时边界、Mock 幂等演示上下文及报错优先级观察不构成 Verified 阻断，见上节。
- P08-T03 两轮均未发现 FAIL，无修复或复测记录；O1～O5 为非阻断观察，UI 真机与极端字体/IME/小屏确认留 T06。
- P08-T04 两轮未发现 FAIL；R1 发现 R3 超时未回查、R4 409 后按钮提前解锁两处非 FAIL 风险，由 F01 修复并同轮复测通过，R2 从头完整执行且全程无问题。
- P08-T05 两轮未发现 FAIL；R1 发现旧游标接口契约风险未记录，由 F01 补 KDoc 与索引并同轮复测通过，R2 从头完整执行且全程无问题。
- P08-T06：R1 发现 2 FAIL（旧 `_nav*` 路由资产；预订弹窗键盘塌缩），分别由强制重新生成并保留正确资产、F02 弹窗重构修复；R2 从头完整执行，两个 FAIL 复测均 PASS 且全程无新问题。
- P08-RULES-01、P08-CONTRACT-01、P08-T01、P08-T04 已完成明细见 [03-history-001.md](03-history-001.md)；P08-T05 已完成明细见 [03-history-002.md](03-history-002.md)。
