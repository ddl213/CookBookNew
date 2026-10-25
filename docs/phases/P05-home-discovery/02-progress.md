# P05 · 首页推荐、分类与搜索 · 执行记录

- 本阶段需求源：`docs/phases/P05-home-discovery/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：第 2 轮完整干净测试（P05-T04，2026-10-25）+ 2026-10-24 用户预授权
- 下一动作：P05 已切换至 P06；下一阶段由新会话执行，本会话不开始 P06 实现。

## 实施口径（2026-10-24，Root 依据已确认需求）

1. 未登录范围沿用 P01 用户决策：公开菜谱、菜谱搜索、公开菜谱详情可浏览；好友动态、评论、收藏、聊天、订单、预订、我的需登录；受限操作引导登录并回到原操作。
2. 推荐/热门权重与口径仍为【待确认】：两者均使用公开菜谱接口的最新排序作为明确回退，不宣称个性化推荐或热门算法已完成；后续用户确认口径再替换。
3. 固定分类标签：推荐、好友动态、热门、家常菜、早餐、甜品；分类内容用公开接口 `category` 精确过滤，好友动态用 `/feed`。
4. 搜索页分“菜谱/用户”：菜谱搜索匿名可用；用户搜索接口需登录，匿名时提示登录。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P05-T01 | 首页与搜索实现：lib_network 增加 public/feed 分页接口与单测；lib_index 实现标签切换、卡片、折叠搜索、搜索页、匿名态、加载/空/错/重试、分页；lib_campaign_common 增加搜索路由；不改后端、P06 创作者页、P07 消息、P08 订单 | standard-executor | Implemented（实现已交付，未验收） | 改动：`RecipeApi.listPublic/listFeed`、`RecipeRepository.listPublic/listFeed/normalizedFilter` + `RecipeRepositoryPublicFeedTest`(4)；lib_index 首页重写、新增 `IndexSearchFragment`、`IndexUiSupport`、item/搜索布局与 selector/strings/drawable；`PAGE_INDEX_SEARCH=action://3006`。单测 `:lib_network` 32 项、`:lib_index` 1 项均 0 失败；`:app:assembleDebug` BUILD SUCCESSFUL，APK 10,212,707 B（2026-10-24 15:20:11）。Root 审阅后修正：`rv_tags` 增 `layout_marginEnd=48dp` 避让折叠搜索按钮；`totalScrollRange<=0` 时隐藏右侧搜索按钮。真机未验证，转 P05-T03。 |
| P05-T02 | 按 P05-T01 实际改动增量更新代码能力索引 | code-auditor | Verified | 新增 ARCH-015、BIZ-010、TEST-016；更新 ARCH-008、DATA-022、DATA-024、DATA-020、UTIL-005 等；索引共 116 条。`code_index_check.py check` 与 `phase_flow.py check` 均 PASS（EXIT 0）。 |
| P05-T03 | P05 第一轮独立真机验证：清空全部后端与 App 数据后重建夹具，覆盖首页标签/折叠搜索/搜索页/匿名/权限/分页/异常/P04 回归 | verifier | Verified（第一轮复测 12/12 PASS，0 FAIL，0 BLOCKED） | 第一轮首次因设备锁屏阻塞；用户解锁后复测 12/12 PASS、0 FAIL、HTTP 34/34 PASS。报告：`C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v1-retest-20261024\verification-report.md`（8,296 B，17:02:16）。干净起点：停旧后端 PID 29632，完整备份后删除 db/-wal/-shm 并清空 uploads，重启 PID 26172，`/health` ok；写夹具前业务表全 0。App com.rhys.main 1.0.1 经 `adb install -r -t` 安装，lastUpdateTime 2026-10-24 15:59:28，未重编，使用工作树 APK 10,212,707 B（15:20:11）；设备 10CECU0CFF001RY vivo V2361A Android 16（SDK 36）1080x2400。12 项必测全 PASS；HTTP 对照 34/34 PASS；推荐/热门 48、家常菜 30、早餐 10、甜品 8、A 好友动态 26，与 HTTP total 完全一致。清理删除 64 道菜谱（0 错误）与好友关系，最终 users=3、recipes/comments/collections/friends/orders/notifications/messages=0、friend_requests=1、uploads 2。两项非 FAIL：①六标签在横向滚动行内（1.0 首屏 4 个、1.5 前三项+第四项部分，均可滚动点击），需求未要求同屏完整，不构成缺陷；②后端 `searchUsers` 排除当前用户，B 搜自己为空属既有规则，登录回跳已用匿名→A 登录搜 B 验证。首轮环境阻塞证据保留于 `C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v1-20261024\`。 |
| P05-T04 | P05 第二轮独立真机验证：由另一个新 verifier 再次清空全部数据后独立复测 | verifier | Verified（第二轮 12/12 PASS，0 FAIL，0 BLOCKED） | 报告：`C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v2-20261025\verification-report.md`（11,674 B，10:53）。干净起点全 10 张业务表 0、uploads 0，后端 PID 22960 `/health` ok；夹具前缀 `p05v273512`，A/B/C=1/2/3，64 道菜谱（公开 48：家常 30/早餐 10/甜品 8；A feed 26）；HTTP 49/49、UI-vs-HTTP 14/14、分页 5/5、UI 12/12；清理 64/64；最终 users=3、friend_requests=1、其余业务表 0、uploads 2。 |
| P05-T05 | 按 T03/T04 失败项修复并由对应 verifier 复测（仅在失败时启用） | standard-executor/verifier | 未开始 | 同一问题正式退回修复最多再试一次。 |

## 当前阻塞与待决事项

- Figma 原型不可访问，视觉沿用现有白色+暖橘/圆角风格。
- 推荐/热门口径待确认（已按回退方案实施：公开菜谱最新排序）。
- 未登录用户搜索用户需登录。
- 构建会重写 `app/src/main/assets/nav/_nav*` 生成资源；`_navindex` 新增 `action://3006` 路由，其余 7 个路由表与 HEAD 一致。按既有提交惯例须随 P05 一并提交，尚未提交。
- 第一轮真机验证已复测通过；第二轮 P05-T04 须再次清空后端与 App 全部数据。
- 设备锁屏曾两次阻塞验证（2026-10-24 P05-T03 首次尝试、17:27 P05-T04 设备探测），均由用户手动解锁；第一轮 P05-T03 解锁后复测 12/12 PASS。当前设备已解锁，Root 已重新设置保持亮屏，供 P05-T04 使用；不记录、不要求 PIN。
- P05-T04 开始时设备常亮原值：`screen_off_timeout=2147483647`、`stay_on_while_plugged_in=2`、`font_scale=1.0`、`airplane_mode_on=0`；本轮按要求保持未恢复。阶段 Gate 通过后按用户要求恢复（`svc power stayon false`、删除 `screen_off_timeout` 覆盖），结果见下方 Gate 记录。

## 实施结果、验证与 Gate

- 2026-10-24 P05-T01（standard-executor）：实现范围限于 lib_network 数据层、lib_index 首页/搜索 UI 与 `RoutePath.kt` 搜索路由，未改后端、app 导航结构及 P06-P08 范围。
- 2026-10-24 Root 审阅修正两条 UI：标签行右侧预留 48dp 避免与折叠搜索按钮重叠；内容不足一屏（`totalScrollRange<=0`）时隐藏右侧搜索按钮，避免无效入口。
- 单测真实结果：`:lib_network:testDebugUnitTest` 32 项 0 失败（含新增 `RecipeRepositoryPublicFeedTest` 4 项）；`:lib_index:testDebugUnitTest` 1 项 0 失败。
- 构建真实结果：`:app:assembleDebug -PapiBaseUrl=http://127.0.0.1:3000/api/v1/ --max-workers=1` BUILD SUCCESSFUL；产物 `app/build/outputs/apk/debug/app-debug.apk` 10,212,707 bytes，2026-10-24 15:20:11。
- 2026-10-24 P05-T02（code-auditor）：索引增量完成，条目总数 116；`python scripts/code_index_check.py check` 与 `python scripts/phase_flow.py check` Root 复跑均 PASS。
- P05-T03 第一轮（含首次锁屏尝试、17:02 复测 12/12 PASS、0 FAIL、0 BLOCKED）与 P05-T04 17:27 锁屏阻塞的完整验证记录已归档至 `docs/phases/P05-home-discovery/03-history-001.md`，原证据路径不变。结论：第一轮复测已通过，但阶段 Gate 以 2026-10-25 第二轮 P05-T04 为准。
- 2026-10-25 P05-T04（verifier，第二轮完整干净测试）：正式报告 `C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v2-20261025\verification-report.md`（11,674 B，10:53）。结论 **12/12 PASS，0 FAIL，0 BLOCKED**。干净起点：停旧后端 PID 22576，备份后删除 db/-wal/-shm 并清空 uploads，重启后端 PID 22960，`/health` ok；写夹具前 10 张业务表全 0、uploads 0（`clean-start.json`）。夹具前缀 `p05v273512`，A/B/C=1/2/3，64 道菜谱：公开 48（家常菜 30/早餐 10/甜品 8），A 好友动态 26，含草稿/好友限定/陌生人/唯一搜索词/坏图/长文本/分页数据；2 张图片真实上传。APK 复用工作树 `app-debug.apk` 10,212,707 B（2026-10-24 15:20:11，SHA-256 `602191b6…b727`），`adb install -r -t` Success 后 `pm clear`；设备 10CECU0CFF001RY 已解锁。结果：HTTP 对照 **49/49 PASS**、UI 卡片数 vs HTTP total **14/14 PASS**（推荐/热门 48、家常 30、早餐 10、甜品 8、A feed 26、B feed 29、C feed 0）、分页 **5/5** 顺序一致无重复缺失、12 项 UI 必测全 PASS（标签权限、折叠搜索、搜索页/匿名/登录回跳、匿名首页、异常与重试、字体 1.5、账号切换隐私、P04 评论/收藏/预订 Toast/分享入口、导航返回）。清理 64/64 菜谱删除成功、好友关系 204；最终 DB users=3、recipes/comments/collections/friends/orders/order_events/notifications/messages=0、friend_requests=1，uploads 2（无删除接口）。环境：App 1.0.1 保持安装并已退出登录，`adb reverse tcp:3000` 保持，font 1.0、飞行模式 0；设备常亮设置按要求未恢复。过程事件：用例 4.3 设备短暂掉 adb，`adb kill-server/start-server` 重建 reverse 后无损续跑。09:48–09:50 曾有一次中止的部分夹具尝试（feed 22、分页/长文本不符合本轮矩阵），已整体移入 `aborted-run-0948\`，最终轮从清空数据重新开始，旧尝试不参与结论。两项非 FAIL 观察：①标签行横向滚动（六标签均可滚动到并点击，需求未要求同屏完整）；②登录后搜索自己被后端 `searchUsers` 排除，属既有规则，是否需“搜索自己”待用户/Root 决定。未覆盖风险：离线错误+重试仅在冷启动/无缓存出现，已缓存标签离线时静默展示；折叠动画中间帧未抓取；分享未实际发送；uploads 为 1x1 PNG；字体 1.5 未覆盖所有二级页面；未跑仪器化 UI、全量回归或压力/弱网测试。
- 结论（2026-10-25 第二轮完成后）：P05-T04 为 Verified（12/12 PASS，0 FAIL，0 BLOCKED），满足用户 2026-10-24 预授权的推进条件（序号 ≥2、从清空数据开始、完整流程、零问题）；用户阶段 Gate 已接受，P05 归档、切换 P06。

## 问题 / 修复历史及归档引用

- 历史验证摘录归档：`docs/phases/P05-home-discovery/03-history-001.md`（P05-T03 第一轮含锁屏尝试与复测、P05-T04 17:27 锁屏阻塞；无删除，仅迁移）。
