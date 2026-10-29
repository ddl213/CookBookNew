# P10 · 历史归档 002

- 归档日期：2026-10-29
- 归档原因：`02-progress.md` 达到 12 KiB 建议线，将 P10-T02-R1 首轮验证、P10-T02-F01 修复范围与 R1 结果（含两次同轮复测逐项细节、设备失联与恢复过程）全文迁入本文件；失败/修复事实与结论原文保留，活动进度仅留紧凑摘要与链接。
- 对应阶段：P10 · 全链路集成、非功能验证与交付
- 活动进度：`02-progress.md`

## P10-T02-R1 第一轮验证事实（2026-10-29）

- 轮次：R1（独立 verifier），状态 `Blocked`，**非干净轮次**：存在未执行项且发现问题，不满足最终干净轮次条件。
- 设备：vivo V2361A（`10CECU0CFF001RY`）与 `24117RK2CC`（zorn，无线 `192.168.137.27:35945`）。验证中途 D1/D2 两台设备失联，部分检查项未执行。
- 后端：`npm test` 131/131 通过。
- Android 单测：`app` 模块单测通过；聚合 `./gradlew testDebugUnitTest` 失败，`lib_campaign_common` 模板单测缺 `junit` 依赖（即 D-4）。
- 附录 C：C1–C4 多数项 PASS；部分项 NOT RUN / BLOCKED（受设备失联影响），需修复后由后续轮次从头完整执行。
- 证据目录：`C:\Users\Admin\AppData\Local\Temp\p10r1-20261029-113614\`。
- 数据与设备状态：测试数据保留未清库；D2 设备残留全局代理 `127.0.0.1:9`，待设备恢复后清除，清除前不得视为干净环境。
- 发现缺陷（Root 决定先修复再复测）：
  - D-1：菜谱详情页“加入购物车（预订）”按钮位于滚动内容底部，首屏仅露出约 2px 橙色边缘，主预订入口不可见/难发现。
  - D-2：作者分享菜谱后后端生成 `recipe_share` 通知，但 App 通知页只请求 `category=order`，接收方在应用内无任何分享通知入口。
  - D-3：720x1600 + 字体 1.3 时订单卡“对方昵称/创建时间”被省略号截断（轻微）。
  - D-4：`./gradlew testDebugUnitTest` 聚合失败，`lib_campaign_common` 模板单测缺 `junit` 依赖；`app` 模块单测通过。

## P10-T02-F01 修复范围（锁定）

- 职责：`standard-executor`；允许补充对应单测。
- D-1：`subModules/lib_cookbook/src/main/res/layout/cookbook_fragment_detail.xml`，把预订按钮移出滚动内容、固定到评论输入栏上方，保持原文案/确认弹窗/草稿隐藏行为；不改业务逻辑。
- D-2：`subModules/lib_chat/**`、`subModules/lib_network/**`（通知 API/DTO/映射/页面），通知页同时展示订单通知与 `recipe_share`，分享通知可跳转菜谱详情；不改后端、不改聊天发送/加密、不新增未读/已读。
- D-3：`subModules/lib_order/src/main/res/layout/order_item_order.xml`，订单卡昵称/创建时间允许换行，避免 720 宽 + 1.3 字体截断。
- D-4：`subModules/lib_campaign_common/build.gradle.kts`，补 `testImplementation(libs.junit)`。
- 禁止：改后端、认证、订单状态机、分享权限规则和无关页面。

## P10-T02-R1 结果（2026-10-29）

- 轮次门禁：R1 首轮为独立 verifier，`Blocked`、**非干净轮次**（设备失联致部分项 NOT RUN，并发现 D-1～D-4）。R1 内完成 2 次同轮复测，全部已知问题关闭、无新增缺陷；因首轮曾发现缺陷，不满足“从头完整无问题”条件，不能作为最终放行轮次。
- 设备：D1 = vivo V2361A（`10CECU0CFF001RY`，USB）；D2 = `24117RK2CC`（zorn，无线，IP 后续变化）。授权记录见下方“设备授权”。
- 首轮发现：D-1 详情预订按钮首屏裁切；D-2 分享通知无应用内入口；D-3 720 宽 + 1.3 字体订单卡截断；D-4 聚合单测缺 junit。证据：`C:\Users\Admin\AppData\Local\Temp\p10r1-20261029-113614\`。
- P10-T02-F01 修复（standard-executor）：新增 `listAllNotifications` / `NotificationScope` / `recipe_share` 映射 / 通知页双跳转；详情预订按钮固定到评论输入栏上方；订单卡昵称/时间改 2 行；补 junit。新 APK SHA-256 `330f6d39b926a0243f08ed6c9f71bfc7d0ae53dd4cc2c56ae676ba586654d075`（10,611,595 bytes）；`./gradlew testDebugUnitTest` 全量通过；`code-auditor` 增量更新索引 204 条，`code_index_check` PASS。
- 同轮复测 1（证据 `C:\Users\AppData\Local\Temp\p10r1-retest-20261029-125347\`）：D-1/D-2/D-3/D-4 均 PASS；C1-3 解除好友、C2-7 错误重试、C4-2 未登录、X4 单机并发、X7 冷启动 PASS；C4-4 仅覆盖首页 `PARTIAL`；X3 因设备网络注入失败 `BLOCKED`。
- 同轮复测 2（证据 `C:\Users\Admin\AppData\Local\Temp\p10r1-retest2-20261029-131130\`）：D1 改 USB 稳定连接，用 `svc wifi disable` + `svc data disable` 真实断网。聊天发送失败显示失败/可重试、不假成功；恢复网络后重试成功且 `clientMessageId` 无重复。预订断网失败不假成功；在线提交仅 1 笔；固定 `Idempotency-Key` 重放返回同一 id。首页/搜索/菜谱列表/订单/消息的错误态与重试均 PASS；X3 `PASS`，C4-4 `PASS`（覆盖全部四个 Tab）。
- 数据重置：每次复测前由当轮 verifier 清空专用测试数据并重新造数（复测 1 与复测 2 各自独立准备）。
- 遗留风险：D2 为无线连接、IP 会变化，须按型号解析；R1 复测不能抵扣 R2 必需的重置数据与从头完整重测。

## 设备失联与恢复过程

- 失联：R1 首轮验证中途 D1/D2 两台设备同时失联，导致部分附录 C 检查项 NOT RUN/BLOCKED；该轮按 `Blocked`、非干净轮次处理。
- D2 残留：设备上留有全局代理 `127.0.0.1:9`，恢复前不得视为干净环境。
- 恢复后状态（已核实）：D1/D2 代理 `null`/`:0`，airplane 0，wifi 1，font 1.0，分辨率 1080x2400；D1 `/health` 返回 HTTP 200。
- 复测连接调整：复测 2 起 D1 改 USB 稳定连接；弱网用 `svc wifi disable` + `svc data disable` 真实断网（该 vivo ROM 忽略飞行模式网络开关）。
- 残留测试数据（R1 结束时）：users 34/35、recipe 14、orders 4/5、message `offline_x3`、friends/requests；已由 R2 verifier 在 R2 前清空 Turso 12 张业务表并重新造数（见活动进度“P10-T02-R2 结果”）。

## 设备授权（历史，2026-10-29）

- 用户提供并要求使用两台手机完成验收；Root 授权本任务改用 vivo V2361A（serial `10CECU0CFF001RY`，Android 16/API 36）与 `24117RK2CC`（zorn，无线，IP 后续变化，Android 16/API 36）。
- 授权原因：默认机 Xiaomi 12S（`2206122SC`）不可用且用户明确要求双机验收；授权人：Root。

## 结论

- R1 为 `Blocked`、非干净轮次：存在 NOT RUN 项且发现 D-1～D-4，不能作为最终放行轮次。
- 两次同轮复测关闭了全部已知问题、无新增缺陷，但复测不抵扣后续必需的新轮完整重测。
- 最终放行由 R2（独立 verifier、从头干净轮次）满足，详见活动进度“P10-T02-R2 结果”与 `04-acceptance-matrix.md`。
