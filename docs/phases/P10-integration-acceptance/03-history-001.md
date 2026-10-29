# P10 · 历史归档 001

- 归档日期：2026-10-29
- 归档原因：`02-progress.md` 达到 12 KiB 建议线，将已 `Verified`、无活动依赖的 P10-T01 明细全文迁入本文件；证据、限制与安全处置原文保留，活动进度仅留摘要与链接。
- 对应阶段：P10 · 全链路集成、非功能验证与交付

## P10-T01 CloudBase 部署交付（2026-10-29）

后端仓库 `D:\CookBookService` 完成 T1–T4：

- T1：数据层由 `node:sqlite` 迁移至 `@libsql/client`（异步），本地 `file:` / `:memory:` 与 Turso 远程均验证；两轮独立验证通过。
- T2：图片改存 Turso `media` 表 BLOB，`/uploads/:id` 从库读取；两轮验证通过（含 4MB 图片、重启持久化、清理）。
- T3：CloudBase 云托管部署（Turso + 无 CFS），服务 `cookbook-api`，版本 `005`，域名 `https://cookbook-api-336963-12-1481356774.sh.run.tcloudbase.com`；健康检查/注册/登录/菜谱/图片烟测通过。
- T4：新增 `/api/v1/chat` 兼容层（会话 ID=peerId、clientMessageId 幂等），修复应用消息页 404；后端 `npm test` 131/131，两轮独立验证通过。

服务信息：

- 服务 `cookbook-api`（CloudBase 云托管），版本 `005`；实例 min/max 记录于后端部署记录。
- 数据库：Turso（远程），未使用 CFS。

验证证据：

- 后端测试：`npm test` 131/131 通过。
- 轮次：T1–T4 各完成两轮独立 verifier 验证，均通过。
- Android APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256 `041b94990e9923a3869342f81239ee2b1653d2a534c3c217db282ac9140d691e`；构建参数 `-PapiBaseUrl=https://cookbook-api-336963-12-1481356774.sh.run.tcloudbase.com/api/v1/ -PchatDataSource=remote`。
- UI E2E：两个账号加好友、B→A 消息、应用会话列表/历史、A 在应用内回复、B 通过 API 收到回复；五个底部 Tab 无崩溃/ANR。
- 设备：默认 Xiaomi 12S（2206122SC）不可用；经用户明确授权改用 `24117RK2CC`（zorn，Android 16）。该设备无外网，使用 adb reverse + 主机 HTTP CONNECT 代理完成验证（TLS 端到端，无中间人）；该限制已记录。
- 数据清理：Turso 12 张业务表均为 0 行，schema 保留；设备代理已恢复。

已知限制：

- 聊天无实时推送（UI 提示下拉刷新）。
- CloudBase 网关将图片 `Cache-Control` 改为 `no-store`。
- 未验证缩容到 0 后的冷启动。
- Turso token 轮换后必须重新部署。
- 免费体验版额度 3000 点/月；0.25C0.5G 约 100 小时/月。

安全处置：

- Turso token 曾因 CLI `cloudrun detail --json` 输出而暴露，已轮换并撤销旧 token；`JWT_SECRET` 已随部署轮换。后续禁止使用 `tcb cloudrun detail --json`。

待用户手动验收：

- 真机注册/登录、五个底部 Tab、菜谱图片加载、双账号好友聊天（含回复）、订单基本流程。
