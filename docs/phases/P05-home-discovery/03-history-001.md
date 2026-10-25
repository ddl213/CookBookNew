# P05 · 历史归档 001

- 归档日期：2026-10-25
- 来源：`docs/phases/P05-home-discovery/02-progress.md`
- 范围：P05-T03 第一轮（含首次锁屏尝试与 17:02 复测）及 P05-T04 17:27 锁屏阻塞的完整验证摘录。上述轮次均已被 2026-10-25 第二轮 P05-T04 覆盖或取代，原始证据路径保持原样，不做改写。

## 2026-10-24 P05-T03 第一轮首次尝试（因设备锁屏中断）

- 2026-10-24 P05-T03 第一轮首次尝试（verifier，因设备锁屏中断）：干净起点为停止占用 3000 的旧后端 PID 19004，完整备份数据至 `C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v1-20261024-backup-20261024-1526`，清空 db/-wal/-shm 与 uploads 后重启 PID 29632，`/health` ok。夹具经真实 HTTP 创建前缀 `p05v17147` 的 A/B/C 账号、好友关系、64 道菜谱（公开 48；家常菜 30/早餐 10/甜品 8；A 好友动态 26；含唯一搜索菜、图片上传、不可达图片、长文本、分页数据）、1 条评论与 1 次收藏。构建 `:app:assembleDebug -PapiBaseUrl=http://127.0.0.1:3000/api/v1/ --max-workers=1` 退出 0，APK 10,212,707 B，但无法安装。HTTP 对照 34/34 PASS（公开 total=48、分类 30/10/8 精确、A `/feed` total=26 仅 B、page2=6、无草稿/A/C 泄漏、匿名与无效 token 401、匿名详情 public 200/friends/draft 404、P04 评论/收藏存在、A 搜索用户命中 B）。12 项 UI 必测未执行：设备锁屏且 App 无法安装，11 项 BLOCKED、1 项 HTTP 权限对照 PASS_WITH_GAP（UI 数量一致性未跑）、0 FAIL。清理后删除 64 道菜谱与好友关系，A/B/C mine/collections/friends=0；最终 users=3、recipes/comments/collections/friends/orders/notifications=0、friend_requests=1，uploads 保留 2 个文件。现状：设备仍锁屏、App 未安装、后端 PID 29632 运行、reverse 保持。本轮不计通过，也不代表 P05 失败。
- 结论（首次尝试后，已被下方复测取代）：P05-T01 为 Implemented；首次真机验证被设备锁屏阻塞，不能据此宣称 P05 完成，也不代表用户已接受本阶段。

## 2026-10-24 P05-T03 第一轮复测通过

- 2026-10-24 P05-T03 第一轮复测通过（verifier）：正式报告 `C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v1-retest-20261024\verification-report.md`（8,296 B，17:02:16）。干净起点：停止旧后端 PID 29632，完整备份后删除 db/-wal/-shm 并清空 uploads，重启后端 PID 26172，`/health` ok；写入夹具前业务表全 0（`clean-start.json`）。安装使用 `adb install -r -t app-debug.apk`，App com.rhys.main 1.0.1，lastUpdateTime 2026-10-24 15:59:28；未重新编译，使用工作树已构建 APK（10,212,707 B，15:20:11）。设备 10CECU0CFF001RY，vivo V2361A，Android 16（SDK 36），1080x2400。结果：12 项必测全部 PASS，0 FAIL，0 BLOCKED；HTTP 对照 34/34 PASS；推荐/热门 48、家常菜 30、早餐 10、甜品 8、A 好友动态 26，与 HTTP total 完全一致。清理删除 64 道菜谱（0 错误）与好友关系；最终 users=3、recipes/comments/collections/friends/orders/notifications/messages=0、friend_requests=1、uploads 2 个文件。环境恢复：字体 1.0、飞行模式 0、`svc power stayon false`、screen_off_timeout 恢复默认、adb reverse 保持、设备非锁屏。报告第 5 节两条“非 FAIL 实际表现”处置：① 标签行横向滚动（1.0 首屏显示 4 个标签、1.5 时 3 个，早餐/甜品需横滑）——`01-requirements.md` 第 2 条只要求提供推荐、好友动态、热门、家常菜、早餐、甜品等分类标签并切换对应内容，未要求六个标签同屏完整显示；第 3 条要求不同宽度与字号下不遮挡、重叠或点击失效，本轮已验证满足；判定不构成缺陷，无需修改实现。② 登录用户搜索自己返回空（后端 searchUsers 排除当前用户）——既有后端规则，UI 请求正常执行；记录为待用户确认的口径，不阻塞本轮，也不改实现。
- 结论（2026-10-24 第一轮复测后）：P05-T03 为 Verified（12/12 PASS，0 FAIL，0 BLOCKED）；P05 阶段尚未完成、用户尚未接受，须完成 P05-T04 第二轮独立干净复测后再报 Gate。

## 2026-10-24 17:27 P05-T04 设备锁屏阻塞

- 2026-10-24 17:27 P05-T04（verifier）：设备锁屏无法交互，按边界立即停止，未安装/未解锁/未改数据；12 项必测全部未开始（0 PASS / 0 FAIL / 12 BLOCKED）。证据：`C:\Users\Admin\AppData\Local\Temp\cookbook-p05-v2-20261024\blocked.txt`。本轮不计入干净轮次。用户随后再次解锁设备，Root 已重新设置保持亮屏，P05-T04 第二轮重新发起。
