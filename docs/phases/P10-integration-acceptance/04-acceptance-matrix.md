# P10 · 验收矩阵与用户手工验收路径

- 对应阶段：P10 · 全链路集成、非功能验证与交付
- 证据根目录：`EV` = `C:\Users\Admin\AppData\Local\Temp\p10r2-20261029-132345\`
- 说明：C2/C3/C4/X 编号与 R2 verifier 实际执行清单一致，检查项按产品附录 C 与 P10 需求原文归类；下表所有项均由 R2 当轮 verifier 从头执行并 PASS。

## 1. 验收结论摘要

- R1（首轮）发现 D-1 详情预订入口裁切、D-2 分享通知无应用内入口、D-3 720 宽 + 1.3 字体订单卡截断、D-4 聚合单测缺 junit；P10-T02-F01 修复后两次同轮复测全部关闭，无新增缺陷。
- R2（第二轮）由与实现及 R1 不同的独立 verifier 执行：先清空远程 Turso 12 张业务表并重新造数，两台设备亲自安装同一 APK，从头执行完整清单，全项 PASS、无产品缺陷，构成最终干净轮次。
- 回归：后端 `npm test` 131/131；`./gradlew testDebugUnitTest --rerun-tasks` 342 tasks BUILD SUCCESSFUL；`/health` 主机 200/0.149s、D1 200/0.503s、D2 200/0.213s。
- 结论：满足“首轮问题修复复测关闭 + 后续干净轮次”的两轮门禁，等待 Root 技术接受与用户阶段 Gate；未获用户明确接受前不得运行 `finish`，不得宣称整个 App 已完成。

## 2. 验收矩阵

> 证据列文件均位于 `EV` 根目录；结果为 R2 本轮实际执行结论。

### C1 用户与菜谱

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| C1-1 | 同一用户既可浏览他人菜谱，也可发布/管理自己的菜谱，无须切换角色 | PASS | `d1-mine.xml`、`d1-b-detail.xml` |
| C1-2 | 个人菜谱页只展示本人菜谱，创建/编辑/删除正确更新 | PASS | `d1-r1-saved.xml`、`d1-r1-edited.xml`、`d1-r3-deleted.xml`、`a-mine.json` |
| C1-3 | 好友可见菜谱不能经公开列表、搜索或分享链接被无权限用户读取 | PASS | `b-get16-pre.json`、`b-get16-unfriend.json`、`c-get15.json` |
| C1-4 | 所有人可见菜谱在公共入口展示 | PASS | `d1-discover-public.xml`、`b-public.json`、`b-user37recipes.json` |
| C1-5 | 详情含图片、名称、作者、简介、食材清单、制作步骤 | PASS | `d2-r1-detail.xml`、`d2-r1-detail-image.png` |
| C1-6 | 评论/收藏/分享使用当前有效菜谱与权限，收藏与个人列表一致 | PASS | `d2-comment-sent.xml`、`d2-collected.xml`、`d2-collections-page.xml`、`b-coll-now.json` |

### C2 首页与内容发现

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| C2-1 | 首页有搜索框、分类标签、菜谱卡片、好友动态入口 | PASS | `d1-home-top.xml`、`d1-15-home.xml` |
| C2-2 | 上滑折叠搜索框；折叠后分类标签右侧搜索按钮可用，二者不重叠；两种入口都能进搜索 | PASS | `d1-home-scroll.xml`、`d1-search-from-icon.xml` |
| C2-3 | 推荐/好友动态/热门/家常菜/早餐/甜品切换内容正确；好友动态只含好友内容 | PASS | `d2-home-jiachang.xml`、`d1-rec-feed.xml` |
| C2-4 | 菜谱卡片进入详情；好友动态头像进入对应用户主页 | PASS | `d1-b-detail.xml`、`d2-r1-detail.xml`、`d1-avatar-profile.xml` |
| C2-5 | 推荐大师/公开菜谱可切换；创作者卡片进主页，公开菜谱进详情；公共列表不含好友专属 R2 | PASS | `d1-discover.xml`、`d1-discover-public.xml` |
| C2-6 | 搜索菜谱/用户：有结果、无结果、权限过滤正确（B 搜不到 R2） | PASS | `d1-search-results.xml`、`d1-search-none.xml`、`b-get16-pre.json` |
| C2-7 | 首页/搜索加载中、空、失败与重试；失败不显示“无权访问” | PASS | `d1-search-fail.xml`、`d1-home-fail.png`、`d1-cookbook-fail.png` |

### C3 社交与预订

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| C3-1 | A/B 一对一聊天；消息页展示会话与记录；双向发送接收正确 | PASS | `d1-chat-open.xml`、`d1-chat-sent.xml` |
| C3-2 | 消息页通知入口展示预订通知并定位对应订单 | PASS | `d1-messages.xml`、`d2-messages2.xml`、`d1-notif-nav.xml` |
| C3-3 | 详情预订入口文案“加入购物车（预订）”，点击出现“确认预订”弹窗；无独立购物车/支付页 | PASS | `d2-reserve-dialog.xml`、`d2-reserve-dialog.png` |
| C3-4 | 非好友/游客对公开 R1 不可预订（UI 提示；HTTP 403） | PASS | `d2-reserve-nonfriend.xml`、`d1-unlogged-reserve.png`、`c-order-403.json` |
| C3-5 | B 提交预订后，B“我的订单”与 A“我的销售”为同一笔订单，状态一致 | PASS | `d1-orders.xml`、`d1-sales.xml`、`b-orders-api.json` |
| C3-6 | A 接受订单后双方状态更新；备货/ready 后展示“待取货” | PASS | `d1-order-accepted.xml`、`d1-order-ready.xml`、`d2-orders-refreshed.xml` |
| C3-7 | A 拒绝另一笔订单，双方状态一致；失败不显示成功 | PASS | `d1-order7-rejected.xml`、`d2-orders-rej.xml` |
| C3-8 | 相同 Idempotency-Key 重放返回同一订单，不产生第二笔；UI 连续点击不重复 | PASS | `order-idem-1.json`、`order-idem-2.json`、`idem-key.txt` |
| C3-9 | 非参与方 C 读取订单 404/无权 | PASS | `c-order-403.json`、`c-order6.json` |

### C4 我的与通用交互

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| C4-1 | “我的”展示头像、昵称、ID、收藏、好友、订单、设置入口 | PASS | `d1-mine.xml`、`d1-mine3.xml` |
| C4-2 | 未登录展示“登录/注册”与“浏览应用”；公开内容可浏览；受保护操作提示登录不崩溃 | PASS | `d1-loggedout2.xml`、`d1-browse-unlogged.xml`、`d1-unlogged-collect.png` |
| C4-3 | 设置页隐私/通知/帮助入口一致，无假开关/假成功 | PASS | `d1-settings.xml`、`d1-notifsettings.xml` |
| C4-4 | 首页/搜索/菜谱列表/消息/订单/详情的加载中、空、失败与重试；失败不假成功 | PASS | `d1-home-fail.png`、`d1-search-fail.xml`、`d1-cookbook-fail.png`、`d1-order-fail.xml` |
| C4-5 | 免费预订不依赖支付/收费提示（未来规划不作为本期缺陷） | PASS | `d2-reserve-dialog.xml` |

### X 跨页面一致性与安全边界

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| X1 | 登录切换/隐私缓存：退出/恢复不串号、不残留他人数据 | PASS | `d1-logout-confirm.xml`、`d1-loggedout2.xml`、`d1-restored-A.xml` |
| X2 | 跨页面数据一致：改昵称/简介后首页/详情/好友页一致 | PASS | `d1-profile-edit.xml`、`d1-prof-saved.xml`、`d1-home-nick.xml`、`d1-detail-nick.xml` |
| X3 | 弱网/断网失败与恢复：聊天/预订失败可见、可重试、不假成功；恢复后成功 | PASS | `d1-chat-offline-send.png`、`d1-chat-retry.png`、`d1-order-offline.png`、`d1-order-retry.png` |
| X4 | 并发/重复：双机同时刷新无崩溃/ANR；相同 clientMessageId/Idempotency-Key 不重复 | PASS | `d1-15-home.xml`、`d2-home.xml`、`msg-idem-1.json`、`msg-idem-2.json`、`order-idem-1.json`、`order-idem-2.json` |
| X5 | 聊天本地加密：chat.db/-wal 与 MMKV 中测试消息非明文 | PASS | `chat.db`、`chat.db-wal`、`mmkv.default` |
| X6 | UI 适配：720x1600、font1.3/1.5 下首页/详情/订单/聊天/我的无遮挡、溢出、关键操作截断 | PASS | `d1-720-home.png`、`d1-720-orders-320.png`、`d1-15-detail.png`、`d1-15-orders3.png` |
| X7 | 冷启动/响应：双机稳定进入主界面，无崩溃/ANR；记录耗时（无 SLA） | PASS | `d1-coldstart.txt`、`d2-coldstart.txt` |
| X8 | 视觉：白色+暖橘、圆角卡片风格一致；Figma 无访问权限（已知限制） | PASS | `d1-15-detail.png`、`d2-r1-detail.png`、`d1-720-home.png` |

### F-D 首轮缺陷回归

| ID | 检查项 | 结果 | 证据 |
| --- | --- | --- | --- |
| F-D1 | 预订按钮 48.0dp 完整可见；评论栏不重叠；草稿/错误隐藏；游客/非好友提示正确 | PASS | `d1-15-detail.png`、`d1-draft-detail.png`、`d1-unlogged-reserve.png`、`d2-reserve-nonfriend.xml` |
| F-D2 | 通知页显示“菜谱分享「p10r2a」分享了「R1PUB」”并可进详情；订单通知仍跳订单详情 | PASS | `d2-notifs2.xml`、`d2-sharenav.xml`、`d1-notif-nav.xml` |
| F-D3 | 720x1600 + font1.3/1.5 订单卡昵称/份数/时间无省略；1080 正常 | PASS | `d1-720-orders.png`、`d1-720-orders.xml`、`d1-720-orders-320.png` |
| F-D4 | 聚合单测全绿 | PASS | `gradle-test.log` |

## 3. 已知限制与未决事项

以下为事实记录，不是新增缺陷；“未来规划”不作为本期验收条件。

- 聊天无实时推送，页面提供下拉刷新提示。
- Figma 原型当前账号无访问权限，视觉对照仅限可用资源与功能/布局检查。
- 推荐/热门为公开菜谱最新排序回退，未定义独立筛选口径。
- 无“附近”定位功能。
- 无独立销售管理页；“我的销售”为同一 Tab 的卖家视角。
- CloudBase 冷启动约 6s+；`/health` 与冷启动数据仅为观测值，不构成性能 SLA。
- D2 为无线连接，IP 会变化，后续须按型号解析设备。
- 该 vivo ROM 忽略飞行模式网络开关；弱网测试须用 `svc wifi disable` + `svc data disable`。
- 支付、群聊、社区、收费/交换条件属未来规划，不在本期范围。
- 误报排除：R2 verifier 的 curl 中文编码曾造成分类乱码，已用 Node UTF-8 修正并复验，非应用缺陷；设置内“紧急联系人”页不在 App 源码中（系统页面），非本应用功能。

## 4. 发布条件

- APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `330f6d39b926a0243f08ed6c9f71bfc7d0ae53dd4cc2c56ae676ba586654d075`（10,611,595 bytes）；包 `com.rhys.main`，versionName `1.0.1`；构建参数 `-PapiBaseUrl=https://cookbook-api-336963-12-1481356774.sh.run.tcloudbase.com/api/v1/ -PchatDataSource=remote`。
- 后端：CloudBase 服务 `cookbook-api`，版本 `005`，域名 `https://cookbook-api-336963-12-1481356774.sh.run.tcloudbase.com`；`npm test` 131/131。
- 数据库：Turso 远程，12 张业务表，R2 前已清空并重新造数。
- 设备：D1 vivo V2361A（`10CECU0CFF001RY`，USB）、D2 `24117RK2CC`（无线 `192.168.137.30:39063`），均 Android 16/API 36；安装时间 2026-10-29 13:28:20 / 13:28:31。
- 阶段 Gate：需用户明确接受后方可运行 `finish --user-accepted`；未接受前保持 P10 当前状态。

## 5. 用户双机手工验收路径

准备：两台 Android 手机、同一 APK、可访问后端的网络；D1 用账号 A，D2 用账号 B。以下步骤可重复执行，每步均给出预期结果。

1. **安装与启动**：两台手机安装上述 APK 并打开。预期：无崩溃/ANR，进入五个底部入口“首页、菜谱、消息、订单、我的”。
2. **注册 A/B**：D1 注册 A，D2 注册 B。预期：注册后自动登录；“我的”显示头像、昵称、ID。
3. **加好友**：A 搜索 B 的 ID/昵称并发送好友请求，B 接受。预期：双方好友列表互见；非好友不能预订。
4. **创建菜谱**：A 创建 `Rpub`（所有人可见）与 `Rfriend`（好友可见），填写图片、名称、简介、食材、步骤。预期：保存成功；A 的个人菜谱页只显示自己的菜谱；编辑/删除后列表即时更新。
5. **可见性与搜索**：B 搜索 `Rpub` 与 `Rfriend`，并尝试通过分享链接打开 `Rfriend`。预期：`Rpub` 可见且出现在公共入口；`Rfriend` 不出现在公开列表/搜索，无权限用户不能读取。
6. **聊天与分享通知**：A 与 B 进入一对一聊天，A 发送消息并分享 `Rpub`。预期：B 的消息页显示聊天记录和“菜谱分享”通知；点击分享通知进入菜谱详情；点击订单通知进入订单详情。
7. **评论与收藏**：B 评论、收藏 `Rpub`。预期：A 能看到评论；B 的收藏列表包含该菜谱；收藏状态与详情一致。
8. **预订**：B 在 `Rpub` 详情点击“加入购物车”。预期：弹出预订确认弹窗，无独立购物车或支付页；提交后 A 的“我的销售”和 B 的“我的订单”出现同一笔订单，状态为“待接受”。
9. **接受/拒绝与备货**：A 接受该订单并标记备货；再对另一笔订单执行拒绝。预期：双方列表与详情状态一致（接受/待取货/拒绝），同一笔订单双视角对应。
10. **首页交互**：在首页上滑，观察搜索框折叠、分类标签右侧出现搜索按钮；切换“推荐/好友动态/热门/家常菜/早餐/甜品”；点击菜谱卡片和好友动态头像；切换“推荐大师/公开菜谱”。预期：各入口可用，分别进入详情或好友主页，无空白/错位。
11. **我的与设置**：查看“我的”头像/昵称/ID、收藏、好友、订单、设置入口；修改昵称后返回首页/详情。预期：入口可用，昵称同步，设置页无崩溃。
12. **未登录浏览**：退出登录。预期：展示“登录/注册”和“浏览应用”；公开菜谱可浏览；收藏/预订提示登录；重新登录后恢复本人数据，不出现其他账号缓存。
13. **断网失败与重试**：在一台手机断开 Wi-Fi 与移动数据（该 vivo ROM 请用系统网络开关或 `svc wifi disable` + `svc data disable`，飞行模式可能不生效）。发送聊天、提交预订。预期：明确显示失败并可重试，不伪装成功；恢复网络后重试成功，消息/订单不重复。
14. **屏宽与字体**：将设备设为 720 宽或 720x1600，字体缩放 1.3/1.5，检查首页、详情、聊天、订单、我的。预期：核心内容与主要操作无截断、遮挡、重叠；检查后恢复默认显示设置。

## 6. 测试数据残留与清理

- R2 残留：users 37/38/39；recipes 15/16/19/20（17/18 已删）；orders 6/7/8；messages 4；comments 1；friends 2；friend_requests 2；notifications 8；media 1；collections 0。
- 清理责任：由后续 verifier 在正式轮次/复测前清空专用测试数据并重新造数；**不要求用户手动删除**。
- 用户手工验收若新增数据，请记录账号/菜谱/订单标识，交由 verifier 或 Root 在确认属于测试数据后清理；严禁清理真实用户或生产数据。
- 证据目录为系统临时目录，可能被系统清理；如需长期留存，请在清理前复制。
