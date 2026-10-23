# P03 · 个人菜谱发布与管理 · 执行记录

- 本阶段需求源：`docs/phases/P03-recipe-management/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：2026-10-23 用户确认“P03 验收通过，按 CLAUDE.md 继续推进 P04”
- 下一动作：用户已于 2026-10-23 接受 P03 并授权按流程切换 P04；本阶段记录冻结，P04 仅完成阶段准备，未授权前不开始 P04 实现。

## 用户决策（2026-10-23）

1. 必填字段：名称、分类、食材、步骤；简介、图片可选。
2. 分类：固定“家常菜/早餐/甜品”+ 自定义输入；后端仍存字符串。
3. **实现草稿**（用户明确决定，覆盖原文默认排除）。
4. 图片：AndroidX 系统照片选择器（`PickVisualMedia`），不新增第三方媒体库；选中后上传 CookBookService。

## 草稿规则（本阶段实现约定，供后端与 App 共用）

- 菜谱状态 `draft | published`；新建默认由表单选择，后端默认 `published`。
- 草稿仅作者可见；不得出现在公开列表、好友动态、其他用户主页、分类列表、计数中；非作者访问详情返回 404。
- `GET /recipes/mine` 增加 `status=published|draft|all`，供“已发布/草稿”两个标签使用。
- 作者可通过 `PATCH /recipes/:id` 将草稿发布为已发布，或将已发布转为草稿；删除草稿/已发布均可。
- 草稿与其他菜谱使用相同必填字段（名称/分类/食材/步骤），不是“未完成表单”。
- 草稿不可被他人预订、评论、收藏、分享；作者自己的草稿不在评论/收藏等社交入口展示。
- 已有订单快照保留；删除/转草稿后的订单展示留到 P08 决定，P03 不新增规则。
- 默认可见性：好友可见（隐私安全默认，表单可改）。
- 列表条目点击详情：P04 实现，P03 先提示占位；编辑/删除通过卡片“更多”菜单。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P03-T01 | 个人菜谱列表、创建/编辑/删除、图片上传与草稿/可见性 | deep-executor | Verified | `lib_network` RecipeApi/UploadApi/RecipeRepository/RecipeMediaRules、`lib_cookbook` 真实列表/表单/系统图片选择与上传/草稿 Tab/编辑删除；`lib_network` 17 例 + `lib_cookbook` 11 例 = 28 例 0 失败；`:app:assembleDebug -PapiBaseUrl=http://127.0.0.1:3000/api/v1/` 成功 |
| P03-T02 | 按实际改动增量更新代码能力索引 | code-auditor | Verified | 新增 14 条、全库 107 条；01/05 已拆分；`code_index_check` PASS |
| P03-T03 | P03 独立构建、后端、真机与安全回归验证 | verifier | Verified | 后端 67/67；草稿 API 与可见性；真机 12 步 PASS；图片运行时上传 `/uploads/395d6bae-1315-48b8-a991-bbc9197f0f21.png`；截图 35 张（`C:\Users\Admin\AppData\Local\Temp\p03-qa\`） |
| P03-T04 | 2026-10-23 重新回归 P01/P02/P03 已实现功能 | verifier | Completed（回归执行完成；FAIL，未 Verified） | Android 107/107、debug 构建退出 0；后端 67/67；HTTP 149 PASS/2 FAIL；UI 22 PASS/2 FAIL/0 BLOCKED；当轮 3 项未修复，后续见 T05；[完整历史](03-history-001.md) |
| P03-T05 | 修复申请页/搜索页返回状态丢失、匿名公开菜谱 list/detail 401，并独立复验 | standard-executor / deep-executor | Verified | 2026-10-23 Root 技术接受；Android 119/119、后端 87/87、HTTP 165/165、UI 16/16；审查无阻断，索引 108 条；阶段仍待用户接受 |
| P03-T06 | 安装既有新版 APK，在 V2361A 进行 P03 真机代验收 | verifier | Verified | 2026-10-23 Root 技术接受；真机 UI 14/14、定向 HTTP 7/7，0 FAIL/BLOCKED；已恢复 demo_home，保留新版与连接映射；阶段仍未接受 |

### P03-T01 实施范围

- CookBookService：菜谱 `status` 字段与无损迁移、草稿过滤、`/recipes/mine?status=`、状态流转、测试/文档。
- Android `lib_network`/`lib_cookbook`：菜谱 API/上传 API/Repository/DTO `status`；真实“我的菜谱”列表（替换硬编码 6 条）；创建/编辑表单；图片选择与上传；可见性；草稿/已发布标签；删除；加载/空/错误。
- 不实现 P04 详情、P05 首页、P08 订单。

### P03-T05 修复与验证（2026-10-23）

- 依据与职责：用户授权修复 T04 三项问题；Android standard-executor、后端 deep-executor 实施，独立 verifier 验证、code-auditor 审查及增量索引，Root 技术接受。
- 批准范围：`lib_relation` 两个目标 Fragment、既有 Robolectric 配置/回归测试；`D:\CookBookService` app 认证 hook、recipes routes、必要 service/测试及 README 匿名契约。实际后端未改 service/app；不改产品规则、不推进 P04、不提交/推送。
- 数据边界：使用新鲜自有测试数据，保留 `demo_home/demo_breakfast/demo_dessert`（id 17–19）及菜谱 15–26。

- [x] 复用 T04 证据与源码锁定原因。
- [x] 两页面 view 重建及匿名权限测试确认 RED。
- [x] 最小修复使新增及既有相关测试 GREEN。
- [x] code-auditor 核查变更并维护索引（108 条）。
- [x] 独立验证、Root 审核，登记证据与手工步骤。

- 实现：两页向重建的新 Adapter 恢复列表/空态、搜索词、Tab/分页；账号变化清旧数据，取消与请求代次防护避免旧响应/加载锁污染。后端仅 public list/detail 条件鉴权：缺 header 的 public/published 200、friends/draft 404，坏凭证 401；其余私有读取/写操作及有效认证保留。
- 自动验证：五模块 `lib_auth/lib_network/lib_relation/lib_cookbook/lib_android_common` 新鲜测试 119/119（116 业务公共 + 3 模板，18 份 XML，0 fail/error/skip）；后端 `npm test` 87/87、`npm run check` 退出 0；真实 HTTP 121 请求、165/165 断言，匿名写入 401、有效认证无退化。
- UI：API 35 只读隔离模拟器 16 PASS/0 FAIL/0 BLOCKED，覆盖两条返回链路、收到/发出、接受拒绝、空态/清空、离线 retry、会话隔离、大字 1.5、草稿创建→发布→改可见性→删除。分页、离开期间换账号与取消由 12 个新增真实生命周期单测覆盖，不计作 UI 实测。
- APK：`app/build/outputs/apk/debug/app-debug.apk`，`2026-10-23 19:00:49+08:00`，10,934,025 bytes，包内 API 为 `http://127.0.0.1:3000/api/v1/`；T05 当轮仅安装模拟器、未操作物理手机，后续 T06 已覆盖安装真机。审查无阻断；8 nav/15 route 明文一致，`_navmain/_navrelation` 随机 IV 差异已按 guard 精确还原。
- 证据根：`C:\Users\Admin\AppData\Local\Temp\cookbook-fix-20261023\`；`android-verification/verification-report.md`、`backend-verification/verification-report.json`、`review/review-report.md`；`device/` 内 `final-result.json`、`steps-index.txt`、`formal-cases.json`、`demo-preservation-after-ui.json`、`api-cleanup.json`、`formal-environment-cleanup.json`、`formal-final-state.json`；路由还原见 `progress-maintenance/`。
- 清理与保留：UI 测试菜谱 27/28/32 已删、20↔21 好友关系清理、临时凭据删除；demo 17–19/菜谱 15–26 全列摘要不变，QA 账号/申请历史及后端合成上传图保留。AVD 已关闭、原数据未改，仅手机在线且保持原状态；服务 PID 36852 继续运行，health 200。

### P03-T06 真机代验收（2026-10-23）

- 授权与结论：用户授权“你来测试”；独立 verifier 在 V2361A（`10CECU0CFF001RY`）覆盖安装既有 `19:00:49` APK，包 lastUpdate 为 `19:32:47`。14 项 UI、7 条定向 HTTP 全部 PASS，0 FAIL/0 BLOCKED，Root 已核对证据并技术接受；不代表用户接受 P03。
- 真机覆盖：收到/发出申请、搜索→主页→标题返回，接受/拒绝、空态/清空词、字号 1.5、断网同页 retry、换账号隔离；完整草稿创建与字段编辑、真实系统 picker 选图上传、发布 public→friends、移图重开仍空、删除后两 Tab/API 一致。
- 图片：仅使用本轮专用相册，未读取/截图私人图片。合成 PNG 3,431 bytes，服务器文件与原图完全一致；上传路径 `/uploads/0f8bbefc-5375-4eb8-a7c0-7aea49896c2d.png`。
- 7 条 HTTP：匿名 draft 详情 404/列表 total 0，坏 token 401；published/public 详情与列表 200（含菜谱 33）；friends 详情 404/列表 total 0。既有 Android 119/119、后端 87/87、HTTP 165/165 正式结果仍有效，本轮未改代码、未重建或重跑全套。
- 清理与保护：QA 账号 27–30、请求 16–18；菜谱 33 已删且 GET 404，27↔28 好友清除、pending 18 拒绝。QA 用户/申请历史及服务端合成图保留；手机测试图、MediaStore 条目、专用目录、dump 及两份临时凭据均已删除。demo 17–19/菜谱 15–26 全列摘要未变。
- 当前手机状态：已恢复 `demo_home`（id 17），已发布 3 条（15/16/17）、草稿 1 条（18）；font 从原 1.0 恢复为 1.0。原 reverse 为空，按 Root 收尾决定（已告知用户）有意保留 `tcp:3000→tcp:3000` 及新版 APK，方便继续使用；不能记为全部恢复原 reverse。服务 PID 36852，health 200。
- 边界：单台真机、字号 1.0/1.5；断网反馈实测搜索 retry，未重复菜谱保存失败；分页及页面离开期间换账号/取消沿用 T05 生命周期单测，不计本轮 UI。未扩测设备矩阵或修改产品规则。
- 证据目录：`C:\Users\Admin\AppData\Local\Temp\cookbook-phone-20261023-P03-T06\`；`final-result.json`、`steps-index.txt`、`cases.json`、`api-anonymous-checks.json`、`api-image-upload-integrity.json`、`demo-after.json`、`demo-home-restored.json`、`environment-cleanup.json`、`api-cleanup.json`。P03 阶段仍未接受，不推进 P04、不提交。

### 外部依赖

CookBookService 草稿接口；实现后需重启 3000 端口服务供真机联调。

## 当前阻塞与待决事项

- P03 前置决策已解除；实施与独立验证完成，等待用户验收。
- 后续产品待确认项引用 P00 记录，不在 P03 重复展开。
- T04 的三项问题已由 T05 修复并技术验收，原 FAIL 历史保留；T06 真机代验收已完成并技术接受。当前等待用户明确接受 P03，无需重复本轮测试。
- 2026-10-23 用户已明确接受 P03，上述等待项关闭；阶段已切换至 P04。

## 实施结果、验证与 Gate

- 后端 67/67；草稿 API 与可见性、状态流转、迁移测试通过。
- Android：`lib_network` 17 例 + `lib_cookbook` 11 例 = 28 例 0 失败；debug 构建成功。
- 真机 12 步 PASS；图片运行时上传；截图 35 张见 `C:\Users\Admin\AppData\Local\Temp\p03-qa\`。
- 测试数据（运行时新建）：A `qaP03A162128`、B `qaP03B162128`；菜谱 id5 draft、id6 public、id7 friends、id8 已删除。

### 手动验收步骤（留档）

以下步骤供复核；T06 代验收已完成，不要求用户重复本轮已完成测试。

1. 后端运行、`adb reverse tcp:3000 tcp:3000`、安装最新 debug APK。
2. 登录 → 菜谱页：已发布/草稿 Tab、登录态、空态。
3. “+”空保存：名称/分类/食材/步骤逐字段提示，不发请求。
4. 新建草稿（固定+自定义分类、食材/步骤多行）：只出现在草稿 Tab，显示“草稿·好友可见”。
5. 编辑草稿：系统照片选择器选图→上传→保存；卡片显示真实图片。
6. 改为已发布+所有人可见：草稿 Tab 消失、已发布 Tab 出现。
7. 编辑改名/简介/好友可见；移除已保存图片并保存→卡片无图，重新编辑 imageUrl 为空。
8. 新建无图已发布菜谱，确认图片可选。
9. 删除→确认→列表消失，GET 详情 404。
10. B 查看 A 主页：draft 始终不出现；published 按 visibility；解除好友后 friends 菜谱消失。
11. 退出登录：菜谱页显示登录提示，无私有数据。
12. 卡片正文点击仅提示详情占位（P04）。

#### P03-T05 补充手工验收

1. 服务运行后，在目标设备安装上述 19:00:49 新 APK，执行 `adb -s <目标设备> reverse tcp:3000 tcp:3000`；T05 当轮物理手机未装新版，T06 已完成覆盖安装。
2. 有收到/发出申请的账号进入申请页，两 Tab 分别点用户主页→标题返回：原 Tab、列表/按钮保留，空列表仍有空态；可继续接受/拒绝。
3. 搜索得到多结果→用户主页→标题返回：关键词和结果保留，无需重搜；字体 1.5 下仍可操作。坏 token 401、匿名 public 200 与 friends/draft 404 以本次独立接口证据核对。

### 已知风险

- P03-T03 历史范围为单设备 1080x2400，未测小屏/大字体；T04/T05 已补字体 1.5，小屏矩阵仍未覆盖。T05 采用模拟器定向复验，T06 已补真机流程及真实选图上传，未扩展为完整设备矩阵；作者仍可对自己的草稿评论/收藏（UI 不展示，P08/后续可收紧）；菜谱详情 P04；开发库保留 QA 数据。

## 问题 / 修复历史及归档引用

- [03-history-001.md](03-history-001.md)：T04 详细回归记录完整迁移，保留当轮 FAIL 与全部失败证据；迁移字节一致性证据见 T06 临时目录的 `progress-maintenance/`。
