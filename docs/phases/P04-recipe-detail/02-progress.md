# P04 · 菜谱详情与内容互动 · 执行记录

- 本阶段需求源：`docs/phases/P04-recipe-detail/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：用户 2026-10-24 条件授权“两次独立真机测试均通过即提交并开启 P05”；P04-T03C 14/14 PASS、P04-T05 13/13 PASS，0 FAIL。
- 下一动作：已切换到 P05（见 docs/CURRENT_TASK.md）。

## 用户决策（2026-10-23）

1. 评论区布局：菜谱详情页内嵌评论（食材/步骤下方展示评论列表，分页加载，登录后可发表）。
2. 分享载体：App 内好友选择弹窗，选择好友后调用 `POST /recipes/:id/share`；不接系统分享面板。
3. P04 预订入口：保留原文案“加入购物车（预订）”按钮；点击明确提示“预订功能将在后续阶段开放”，不创建订单、不伪造成功。
4. 无权查看/已删除：后端保持统一 404，页面合并提示“菜谱不存在或无权查看，可能已被删除”，并与网络加载失败区分，避免泄露私密菜谱是否存在。

补充约定：

- 分享仅对菜谱作者展示/可用（产品原文“向好友分享自己的菜谱”，后端 `SHARE_FORBIDDEN` 强制）。
- “我的收藏”独立页面留在 P09，P04 只保证详情收藏状态与接口一致。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 依赖 / 边界与证据 |
| --- | --- | --- | --- | --- |
| P04-T01 | Android 菜谱详情页与收藏/评论/分享全链路：`lib_network` 社交 API/Repository/DTO/错误映射与单测；`lib_cookbook` 详情页（图片、名称、作者头像→用户主页、分类/可见性、简介、食材、步骤、收藏、分享好友选择弹窗、内嵌评论分页与发表、加载/无权/失败/重试、预订入口提示）；替换 `CookBookChildFragment` 与 `UserProfileFragment` 的详情占位为真实跳转 | standard-executor（如遇权限/一致性风险再升级） | Verified | 依赖：后端社交接口已存在。允许文件：`subModules/lib_network/**`、`subModules/lib_cookbook/**`、`subModules/lib_relation/**`（仅详情跳转接线）、`subModules/lib_campaign_common/**`（仅在必须补常量时）。禁止改后端、P05 首页、P07 消息页、P08 订单。证据：详情/收藏/评论/好友分享与入口接线已实现；F01 草稿详情隐藏社交/预订入口；F02 修复 D1/D2/D3 并隐藏草稿操作区留白；Android 单测 58/58、构建成功、HTTP 52/52、后端 87/87、UI 修复后全部 PASS。 |
| P04-T02 | 按 P04-T01 实际改动增量更新代码能力索引；同时核对并修正两处疑似过期条目（VIEW-001 写 `CookBookChildFragment` 使用 `setVertical(30)`+红色分割线，实际为 `setVertical(6)` 无分割线；VIEW-015 写其调用 `BasePhotoDialog`，实际全仓无调用） | code-auditor | Verified | 依赖 T01 完成并冻结文件。只维护索引，不修改业务代码。证据：索引 113 条；新增 DATA-027/028、BIZ-009、TEST-014/015；修正 VIEW-001/VIEW-015 等过期条目；`code_index_check` 与 `phase_flow check` 均 PASS。 |
| P04-T03 | P04 独立验证：受影响模块构建与单测、后端 `npm test`/`npm run check`、真实 HTTP 权限/收藏/评论/分享检查、模拟器或真机 UI 主路径与异常路径 | verifier | Verified | 依赖 T01；禁止修改实现。证据：T03A/T03B/T03C 三轮；T03B 初轮 13 项 11 PASS/1 FAIL(D1)/1 BLOCKED(D4)，另发现 D2/D3；修复后 T03C 定向复测 14 项全部 PASS，0 FAIL/BLOCKED；设备字体恢复 1.0、飞行模式关闭、reverse 保留、已退出登录。 |
| P04-T04 | 手动验收前清理 P04 验证残留数据并重建评论分页样本 | routine-executor | Verified | 依赖 T03 结束。仅操作后端 SQLite 数据，未改源码/配置/上传文件。证据：删除 users 45–47、recipes 57–60、comments 83–107、friend_request 26、friends 45↔46；保留 demo 用户 17–19 与菜谱 15–26；新建菜谱 61（author 17，public/published，共享图片）及评论 108–132（25 条，18/19 交替，时间递增）；清理后 users 4、recipes 13、comments 25、其余社交表 0；`GET /api/v1/recipes/61` 200、`/recipes/57` 404、匿名 comments 401；备份 `C:\Users\Admin\AppData\Local\Temp\cookbook-p04-gate-20261024\backup\cookbook-before.db`；`integrity_check` ok、`foreign_key_check` 无违规；demo 15–26 与备份双向 EXCEPT 为 0。 |
| P04-T05 | P04 第二轮独立真机复测：清空全部后端与 App 数据后重建夹具，覆盖详情/收藏/评论分页/键盘/分享/预订/草稿/404/断网/字体/导航/匿名接口 | verifier | Verified | 依赖 T01/T03；禁止改实现。证据：13/13 PASS，0 FAIL；D4 匿名 UI 入口缺失为 P05 范围缺口；APK 1.0.1 lastUpdate 2026-10-24 14:38:41；报告见 C:\Users\Admin\AppData\Local\Temp\cookbook-p04-v2-20261024\verification-report.md。 |

## 外部依赖与范围边界

- 后端 `D:\CookBookService` 已实现并测试通过：菜谱详情（`GET /recipes/:id`，匿名仅 public/published，其余 404）、评论列表/新增/删除（`GET/POST /recipes/:id/comments`、`DELETE /comments/:id`，均需登录且校验菜谱可见性）、收藏（`PUT/DELETE /recipes/:id/collection`、`GET /me/collections`）、分享（`POST /recipes/:id/share`，需好友关系、接收方可查看且仅作者可分享，生成 `recipe_share` 通知）。对应测试 `test/social.test.js` 已覆盖。P04 预期不需要修改后端；若实施中发现契约缺口，停下报 Root，不得自行改后端规则。
- Android 现状：`RoutePath.PAGE_COOK_BOOK_DETAIL` 常量存在但无实现类；`RecipeApi.get` 与 `RecipeRepository.get` 已可用；`RecipeDto` 已含 `collected`、`canReserve`、`author`、`visibility`；`CookBookChildFragment` 与 `UserProfileFragment` 的菜谱条目点击仍是占位提示。
- 设计稿：Figma 原型（pYhPJN1omnDD3PnOiGmme3）当前账号无访问权限，无法读取详情页设计；视觉沿用项目现有白色+暖橘、圆角卡片与既有 `TitleBar`/`GEditButton` 风格，不臆造未见设计。
- 本阶段只实现详情页与既有社交互动；不新增购物车、订单状态机、付费预订。

## 当前阻塞与待决事项

- D4：匿名用户当前无公开菜谱/搜索入口，无法从 UI 打开公开详情；属 P05 与“未登录浏览范围”待确认项，P04 不修，已归入 P05。接口已证明匿名详情权限正确（公开详情 200，非公开 404）。
- 通知/好友申请无删除接口；P04 验证测试数据已清理（见 P04-T04），未保留测试账号；demo 用户 17–19 与菜谱 15–26 保留；分页验收样本为菜谱 61（25 条评论，id 108–132）。
- T05 复测数据已清理；账号 A/B/C 与 1 条通知、1 条已接受申请因无删除接口保留，P05 下一轮验证前将再次清空全部数据。
- 飞行模式不能切断 adb reverse 回环；真断网验证需临时移除 `tcp:3000` reverse（T03C 已使用并恢复）。
- 评论顺序依赖后端按 id/时间递增；若后端改变排序需同步 UI 逻辑。
- 单机验证（V2361A / Android 16）、字体 1.0 与 1.5；未做小屏/多设备矩阵。
- 部分单测为 Gradle UP-TO-DATE 复用；F02 后 `lib_relation`/`lib_cookbook` 已用 `--rerun-tasks` 重跑。
- Figma 原型不可访问，视觉以现有项目风格为准，不视为有完整设计稿；其余产品待确认项引用 P00。

## 实施结果与验证证据

- Android：`:lib_network/:lib_cookbook/:lib_relation:testDebugUnitTest` 共 58/58（28/11/19），`app:assembleDebug` BUILD SUCCESSFUL；F02 后关键模块已 `--rerun-tasks` 重跑。
- 后端（`D:\CookBookService`）：`npm test` 87/87、`npm run check` 0；HTTP 接口矩阵 52/52 PASS；demo 用户 17–19 与菜谱 15–26 前后快照一致。
- UI：T03B 13 项 11 PASS/1 FAIL(D1)/1 BLOCKED(D4)，另发现 D2/D3；T03C 修复后 14 项全 PASS，0 FAIL/BLOCKED（含 D1 三次迭代、D2 键盘、D3 分页顺序与关键回归）。
- 两次独立真机结果并列：P04-T03C 14/14 PASS（2026-10-23）；P04-T05 13/13 PASS、0 FAIL（2026-10-24，清空全部后端与 App 数据后重建夹具）。T05 唯一 BLOCKED 为 D4 匿名 UI 入口缺失，属 P05 范围缺口，不计入 P04 失败。
- APK：`com.rhys.main` 1.0.1，T03C lastUpdate 2026-10-23 21:32:37；T05 lastUpdate 2026-10-24 14:38:41。设备 V2361A（Android 16）。
- 证据目录：`C:\Users\Admin\AppData\Local\Temp\cookbook-p04-api-20261023\`、`...\cookbook-p04-ui-20261023\`、`...\cookbook-p04-retest-20261023\`；`C:\Users\Admin\AppData\Local\Temp\cookbook-p04-v2-20261024\`（`verification-report.md`、`ui-cases.json`、`http-checks.json`、`cleanup.json`、`env-after.json`）。
- T05 环境：后端 `/health` ok；字体 1.0；`adb reverse` 已恢复；已退出登录；账号 A/B/C 与 1 条通知、1 条已接受申请因无删除接口保留。

## 用户手动验收步骤（UI Gate）

1. 启动后端；安装 lastUpdate 21:32:37 的 APK；执行 `adb reverse tcp:3000 tcp:3000`。
2. 登录账号 A → 菜谱 → 已发布 → 点卡片：详情图片/名称/作者/分类/简介/食材/步骤完整。
3. 详情作者行 → 用户主页 → 返回：主页昵称与菜谱不空白（D1）。
4. 点评论输入框：键盘弹出时输入栏与发送按钮完整可见；发表后输入框清空（D2）。
5. 打开 >20 条评论的菜谱（新样本：菜谱 61 `[P04验收] 分页评论菜`，25 条评论）：第 1 页 → 发表 → 触发第 2 页，顺序正确（D3）。
6. 收藏切换并在“我的收藏”接口侧一致；分享（作者可见、非作者不可见、无好友空态）；预订仅提示不建单；草稿详情无收藏/分享/评论/预订且无异常留白；非好友/已删除统一 404 且无重试；断网显示错误并可重试。
7. 字体调至 1.5 复查详情/评论/分享弹窗无遮挡；测后恢复 1.0。

## 问题 / 修复历史及归档引用

- P04-T01-F01：草稿详情隐藏收藏/分享/评论区/输入框/预订按钮；还原 `MineFragment` 残留（现与 HEAD 一致，不提交）。
- P04-T01-F02：真机缺陷 D1（用户主页 View 重建后空白）、D2（键盘遮挡评论输入栏）、D3（评论分页顺序）修复，并隐藏草稿操作区留白；T03C 复测全部 PASS。
- 证据与复测细节见上述证据目录，不复制长日志。
