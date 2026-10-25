# P08 · 已完成历史归档 001

- 归档范围：P08-RULES-01 已完成、无活动依赖的实施与验证明细，及 2026-10-25 文档提交/计划登记；P08-CONTRACT-01 契约归档验证；P08-T01 后端实施与 R1～R8 验证明细；P08-T04 双向订单列表/详情实施、F01、R1/R2 与 O1～O9 观察。原文自 `02-progress.md` 完整迁入，结论与证据未改写。

- P08-RULES-01 实施结果：已同步 `CLAUDE.md`、`.claude/rules/02-stage-lifecycle.md`、`03-subagent-routing.md`、`04-verification-gates.md`、`.claude/agents/verifier.md`、`progress-maintainer.md` 及本记录；明确完整清单、顺序独立轮次、复测计数、最终干净轮次、清理/安装职责与证据边界。
- 正式验证：Root 收到 R1 通过结果后才顺序派发 R2；两轮由不同独立 verifier 从头完成，未复用前轮结果抵扣本轮检查。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | `/root/verify_acceptance_round1` | 0 | PASS，完整流程全程无问题 |
| R2 | `/root/verify_acceptance_round2` | 0 | PASS，完整流程全程无问题；最终干净轮次 |

- 两轮各自完整核对七文档范围与全文 diff、需求与角色/状态一致性、R1/R2/R3 失败分支及历史证据禁抵扣、数据清理与安装权限、UTF-8/Markdown/YAML/引用/预算及当前阶段边界；全部满足，七份文档均符合建议预算和硬限制。
- 两轮各自运行 `python -X utf8 scripts/phase_flow.py check`，均退出 0：阶段总数 11、已完成 8、当前 P08，索引、完整需求副本、验收记录与文件上限一致。各自运行 `git --no-optional-locks diff --check` 均退出 0，仅有 LF→CRLF 提示，无空白错误。
- 两轮各自确认数据清理、新数据、测试制品/版本、设备、安装执行人/动作/结果均不适用（N/A）：本次仅文档维护，无运行数据或应用安装需求。证据可追溯本会话上述两位具名 verifier 的最终报告与工具输出，无磁盘日志路径。
- 2026-10-25 技术状态：Root 已审核实际 diff 和两轮独立证据，确认满足 04 门禁，接受 P08-RULES-01 为 Verified。本次仅规则维护通过；用户阶段验收仍为未接受，未切换阶段、未修改需求或开展 P08 业务。
- 2026-10-25 文档提交与计划登记：已验证的规则门禁与订单契约以本地提交 `0b79baa` 固化，提交后工作树曾为空；随后登记 P08-T01～T06 实施计划、依赖顺序与新增阻塞。本记录仅为计划信息，不含验证 PASS、未启动业务实现，也不代表阶段完成。

## P08-CONTRACT-01 契约归档

- 实施结果：新增 `04-order-contract.md`，仅保留 P08 特有业务、接口、改造/接入及验收要求；阶段需求和当前任务同步引用，原订单待确认描述已按用户决定更新。正式文档验证已完成，用户阶段验收仍为未接受。
- 验证：R1 `/root/p08_contract_verify_r1`、R2 `/root/p08_contract_verify_r2` 均 PASS、复测 0、全程无问题，R2 为最终干净轮次。两轮各自完整核查四文件内容/去重、引用、UTF-8/预算、副本一致、历史与修改范围；`python -X utf8 scripts/phase_flow.py check` 与 `git --no-optional-locks diff --check` 均退出 0，仅既有 LF→CRLF 提示。
- 证据：本会话上述两轮独立报告；数据清空/新造数、设备及安装均 N/A（纯文档归档，无运行数据或安装需求）。2026-10-25 Root 审阅实际契约、入口 diff 和两轮证据后技术接受为 Verified，仅指文档归档。

## P08-T01 后端订单域

- 实施结果：`D:\CookBookService` 内完成多明细订单域——`orders`/`order_items`/`order_events` 版本化迁移与旧单菜品回填；明细六态与整单 `CLOSED` 汇总；`allowedActions`、计数与总份数；多明细创建与幂等指纹 v2（兼容旧 v1）；明细动作、整单取消与 `expectedVersion`；同事务事件及六类通知（`category=order`）；旧入口 410；`ASSUMPTIONS.md`/`README.md` 同步。新增纯规则模块 `src/services/order-status.js`。
- 正式验证：每轮由 Root 顺序新派不同独立 verifier 从头执行，数据每轮重建，安装 N/A（纯后端）。实现方与最终轮运行 `npm test` 109/109 PASS、`npm run check` 退出 0。

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

## P08-T02 Android 订单网络与数据层

- P08-T02 实施结果：`lib_network` 新增订单 DTO/API（多明细、六态、allowedActions/计数/总份数、expectedVersion、分页）、错误映射、共享 `orderDataSource` 配置（默认 remote）与订单通知存储；`lib_order` 新增 OrderDataSource 抽象、Remote/Mock 双实现、共享 Mock store、Factory、OrderRepository；加密 pending 采用 AES-GCM+Keystore，按 owner+kind+baseUrl 隔离，超时保留、明确 4xx 清理、原幂等键重放。Mock 覆盖六态/汇总/幂等/版本/取消/六类通知/双账号共享。
- P08-T02 正式验证：Root 顺序新派不同独立 verifier，每轮从头强制重跑 Gradle（R2 使用 `--rerun-tasks`）；纯 JVM 用例每例独立内存 store/临时 cipher，无持久化 App 数据，安装 N/A。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | `:lib_network:testDebugUnitTest` 70 例、`:lib_order:testDebugUnitTest` 38 例，0 failures/errors/skipped；`compileDebugKotlin` PASS；源码/索引无 FAIL |
| R2 | 本轮新派独立 verifier | 0 | 完整重跑 BUILD SUCCESSFUL，70+38 全绿；契约 §2/§3/§6 与 DTO/API/错误映射/配置/Remote/Mock/Pending/Repository/通知存储/索引逐项 PASS；最终干净轮次 |

- P08-T02 证据：两轮均 0 failures/errors/skipped；`python -X utf8 scripts/code_index_check.py check`、`python -X utf8 scripts/phase_flow.py check` 均 PASS。代码索引 DATA-052～068、DEP-014、TEST-030～035。非阻塞观察：Remote 无专项单测留待 T06 真实联调；通知存储边界仅源码核查留待 T05；Mock 幂等在演示上下文丢失时可能先报 RECIPE_NOT_FOUND（不产生第二单），留待 T03/T06 验证；版本校验先于权限的报错优先级细节。
- 2026-10-25 Root 审阅两轮独立证据后技术接受 P08-T02 为 Verified；阶段 Gate 仍未接受。

## P08-T03 预订确认弹窗与预订入口

- P08-T03 实施结果：预订确认弹窗支持多菜选择、数量增减、删除、整单备注、提交中状态、pending 重试/放弃、Mock 上下文标识、IME 避让与账号切换刷新；菜谱详情“加入购物车”按好友/可预订条件分支进入弹窗；3.9 菜品预订页 `@Route(PAGE_ORDER_INFO)` 展示可预订菜品，评分/送达按“暂无”占位，仅展示 `canReserve` 菜品；好友主页增加“预订菜品”入口并由 `UserProfileActionResolver` 控制可见性。未新增独立购物车、支付或配送。
- P08-T03 正式验证：Root 顺序新派不同独立 verifier，每轮从头强制 `--rerun-tasks` 构建；纯 JVM 用例每例独立内存对象，无 App 持久化数据，安装 N/A。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | `--rerun-tasks` 构建成功；`:lib_order:testDebugUnitTest` 49 例、`:lib_relation:testDebugUnitTest` 20 例，0 failures/errors/skipped；`:app:compileDebugKotlin` PASS；源码/布局/索引静态检查 PASS；UI 真机 NOT RUN 留 T06 |
| R2 | 本轮新派独立 verifier | 0 | 全新 verifier 强制重跑 BUILD SUCCESSFUL 248 tasks，49+20 全绿；DTO/弹窗/入口/3.9/好友主页/布局/依赖/索引逐项 PASS，无 FAIL；最终干净轮次（限定本轮代码/构建/静态范围） |

- P08-T03 证据：索引 ARCH-019/008、VIEW-017/018、RES-013、BIZ-004/014、DEP-012/014、TEST-036；`python -X utf8 scripts/code_index_check.py check` 与 `python -X utf8 scripts/phase_flow.py check` 均 PASS。数据重置/新数据为每例独立内存对象，安装 N/A；UI 真机运行与极端字体/IME/小屏确认留 T06。
- P08-T03 非阻断观察（不阻断 Verified，指向 T06）：O1 `ReservationDraft` 构造器非法初始值过滤无专项用例（实现正确）；O2 `order_strings.xml` 两个字符串暂未引用；O3 极端大字体+IME/小屏下弹窗与 3.9 列表内容高度可能被压缩；O4 Mock `buildDemoContext` 固定 `isFriend=true`，正常入口已先校验，绕过入口属演示态；O5 3.9 页 profile 与菜谱列表同一 try，菜谱 404 可能复用“用户不存在”文案，罕见，留 T04/T06 关注。
- 2026-10-25 Root 审阅两轮独立证据后技术接受 P08-T03 为 Verified（不含 UI 真机）；阶段 Gate 仍未接受。

## P08-T04 双向订单列表与详情

- P08-T04 实施结果：订单 Tab 双标签（我的订单/我的销售）、8 类状态筛选、分页、登录态/账号切换、迟到响应丢弃与返回刷新；订单详情按服务端 `allowedActions` 逐项显示接受/拒绝/备货/待取货/完成/取消，携带 `expectedVersion`，操作前确认，409 版本冲突后回查并提示；整单取消；Mock 标识；“我的-订单”切换订单 Tab。
- P08-T04-F01：动作失败（非 401 的 AuthException 与普通 Exception）后保持按钮禁用并立即回查订单，回查完成后解锁；取消确认不发请求；不自动以新版本重试。仅修改 `OrderDetailFragment.kt`。
- P08-T04 正式验证：Root 顺序新派不同独立 verifier，每轮从头强制 `--rerun-tasks` 构建；纯 JVM 用例独立内存，无 App 持久化数据，安装 N/A。

| 轮次 | 独立 verifier | 复测次数 | 实际结果 |
| --- | --- | --- | --- |
| R1 | 本轮新派独立 verifier | 0 | `--rerun-tasks` 构建成功；`:lib_order:testDebugUnitTest` 65 例 0 failures/errors/skipped；`:lib_mine`/`:app` 编译 PASS；源码/布局/索引静态 PASS；R3 超时未回查、R4 409 后按钮提前解锁两处风险转 F01；UI 真机 NOT RUN |
| F01 | 本轮新派独立 verifier | 1 | 仅改 `OrderDetailFragment.kt`；强制重跑 BUILD SUCCESSFUL，65 例全绿；R3/R4 时序问题修复并同轮复测通过（不计新轮） |
| R2 | 本轮新派独立 verifier | 0 | 全新 verifier 强制 `--rerun-tasks`，221 tasks executed，65 例 0 failures/errors/skipped；列表/详情/F01 时序/我的入口/布局/索引逐项 PASS；最终干净轮次（限定代码/构建/静态范围）；UI/F01 运行时 NOT RUN 留 T06 |

- P08-T04 证据：`:lib_order` 65 例（含 `OrderUiSupportTest` 16 例）、`lib_mine`/`app` 编译 PASS；索引 ARCH-020/008/010、VIEW-019/020、BIZ-015、DEP-015、TEST-037、RES-013、DATA-063；`python -X utf8 scripts/code_index_check.py check` 与 `python -X utf8 scripts/phase_flow.py check` 均 PASS。数据重置/新数据为每例独立内存对象，安装 N/A；UI 真机与 F01 运行时留 T06。
- P08-T04 非阻断观察（不阻断 Verified，指向 T06）：O1 `OrderUiSupport.FILTERS`/`OrderFilter` 生产未接线（页面 chip 硬编码），索引已标注；O2 状态/动作中文文案在 `OrderUiSupport` 硬编码，与资源字符串重复；O3 动作失败且回查失败可能连续两条 toast；O4 回查 404/401 时启用已 GONE 的取消按钮，入口二次校验无功能影响；O5 非常规小数秒时间格式化未覆盖；O6 大字体/小屏按钮与摘要截断风险；O7 详情返回刷新依赖 Navigation replace 重建 HomeFragment，无 onResume 兜底；O8 `Constants.kt` 末尾缺换行（格式性）；O9 `00-INDEX.md` 14,180 bytes，低于硬限制、高于 12 KiB 建议线。
- 2026-10-25 Root 技术接受 P08-T04 为 Verified（不含 UI 与 F01 运行时）；阶段 Gate 仍未接受。
