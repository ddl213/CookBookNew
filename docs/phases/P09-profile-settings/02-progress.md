# P09 · 个人中心、收藏与设置整合 · 执行记录

- 本阶段需求源：`docs/phases/P09-profile-settings/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：2026-10-27 用户明确接受 P09；依据 R3 Xiaomi 12S 真机 21/21、T01～T05 均 Verified、无阻断缺陷；剩余风险：UI 无自动化测试、登录后落点观察、验收环境 reverse/后端需保持。
- 下一动作：用户已接受本阶段，执行阶段切换至 P10；P10 尚未启动，等待用户授权。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P09-T01 | 数据契约与网络层：新增 `PAGE_MY_COLLECTIONS=action://3008`、`PAGE_SETTING_PRIVACY=action://7005`、`PAGE_SETTING_NOTIFICATION=action://7006`、`PAGE_SETTING_HELP=action://7007`（`RoutePath.kt`）；`SocialApi.listCollections(page,pageSize): PageResponse<RecipeDto>` 与 `RecipeRepository.listCollections(...)` 页码/页大小归一化；`UserApi.updateProfile(@Body ProfileUpdateBody): UserDto`、新增 `ProfileUpdateBody` 与 `UserRepository.updateProfile(...)`，客户端只做 trim 与长度防御校验、后端最终判定；`SessionManager.updateUser(user: UserDto)` 更新 SessionStore 并发出新的 `AuthState.LoggedIn`；纯 JVM 单测覆盖新归一化/校验。不改 UI、不改后端 | standard-executor | Verified | R1 PASS（发现 O2，已修复并同轮复测 1 次通过）+ R2 新独立 verifier 干净轮次；单测 78/78、三模块构建成功、真实后端契约通过、索引已同步。证据：`subModules/lib_network/build/test-results/testDebugUnitTest/`（78 tests/0 fail，含 `TEST-com.example.network.repository.UserProfileUpdateValidationTest.xml`）；后端契约 `D:\CookBookService\src\routes\social.js:51`（GET /me/collections）、`D:\CookBookService\src\routes\users.js:58`（PATCH /users/me）；`python scripts/code_index_check.py check`（202 条）与 `python scripts/phase_flow.py check` 均 PASS。下一动作：P09-T03 |
| P09-T02 | “我的”页整合：资料卡增加用户 ID 展示（`id`）并可点击进入 `PAGE_MINE_INFO`；收藏入口跳 `PAGE_MY_COLLECTIONS`、设置入口跳 `PAGE_SETTING`，保留好友/订单入口行为；导航失败有提示，移除无用占位提示；未登录态、登录态、加载态保持；资料更新后随 `SessionManager.state` 自动刷新，不伪造或替代资料 | standard-executor | Verified | R1/R2 见下节：21 项 UI 全 PASS、构建成功、`lib_network` 单测 78/0/0、模拟器由当轮 verifier 安装、数据/设备清理完整；R3 Xiaomi 12S 真机干净轮次 21/21；修复复测 0 次、无产品问题。证据：`app/build/p09-r1-evidence/`、`app/build/p09-r2-evidence/`。UI 手动验收留阶段 Gate |
| P09-T03 | “我的收藏”页：`lib_cookbook` 新增 `CollectedRecipesFragment`（`@Route(PAGE_MY_COLLECTIONS)`）；调用 `GET /me/collections` 分页，下拉刷新、上拉加载、登录守卫、空/加载/错误/重试；卡片点击进菜谱详情；复用现有菜谱卡片视觉，不新增购物车等业务；`onResume` 刷新第一页保证从详情返回后收藏状态一致；登录态/账号切换丢弃旧响应 | standard-executor | Verified | 同 R1/R2（21 项全 PASS、78/0/0、模拟器安装、清理完整）；R3 Xiaomi 12S 真机干净轮次 21/21；修复复测 0 次、无产品问题。证据：`app/build/p09-r1-evidence/`、`app/build/p09-r2-evidence/`。UI 手动验收留阶段 Gate |
| P09-T04 | 个人信息编辑页：`lib_mine` 新增 `MineInfoFragment`（`@Route(PAGE_MINE_INFO)`）；展示并编辑昵称、简介、头像；头像复用 `PickVisualMedia` 与 `RecipeRepository.uploadImage`；保存调用 `PATCH /users/me`，成功后 `SessionManager.updateUser`；客户端校验昵称 1..32、简介 ≤500；上传/保存失败给出真实错误、不伪装成功；保存中防重复提交；返回路径正确；不使用占位资料或他人资料 | standard-executor | Verified | 同 R1/R2（21 项全 PASS、78/0/0、模拟器安装、清理完整）；两处审阅问题验证前修复，R3 Xiaomi 12S 真机干净轮次 21/21；修复复测 0 次、无产品问题。证据：`app/build/p09-r1-evidence/`、`app/build/p09-r2-evidence/`。UI 手动验收留阶段 Gate |
| P09-T05 | 设置与说明页：`SettingsFragment`（`@Route(PAGE_SETTING)`）提供隐私设置、通知设置、应用帮助三个入口；三个说明页——隐私页只陈述当前已确认规则（好友可见/所有人可见、资料展示边界）且不新增开关，通知页说明现有消息/订单通知位置并明确暂无可配置项，应用帮助页展示应用版本与基本使用说明；统一 TitleBar 返回；文案使用 strings 资源；依赖 T01 路由常量 | standard-executor | Verified | 同 R1/R2（21 项全 PASS、78/0/0、模拟器安装、清理完整）；R3 Xiaomi 12S 真机干净轮次 21/21；修复复测 0 次、无产品问题。证据：`app/build/p09-r1-evidence/`、`app/build/p09-r2-evidence/`。UI 手动验收留阶段 Gate |

- 依赖顺序：P09-T01 →（P09-T03、P09-T04、P09-T05 路由与页面就绪）→ P09-T02；文件作用域重叠时顺序派发。

## 当前阻塞与待决事项

- 无（用户已确认资料编辑与设置页范围）。
- UI 验收需本地 `D:\CookBookService` 与模拟器/真机；正式验证需清空应用数据并重新造数（种子用户/菜谱/收藏），由当轮 verifier 亲自安装测试制品。
- 手工验收环境已就绪（2026-10-27）：应用已在 Xiaomi 12S 启动并保持前台，后端终端标签 `c1` 与 `adb reverse tcp:3000` 正常；任一项失效需先恢复再验收。详见下节 R3 后记录。
- （暂无其他记录；不能据此判断风险不存在）

## 实施结果、验证与 Gate

- 2026-10-26：P08 经用户明确接受后执行 `python -X utf8 scripts/phase_flow.py advance --user-accepted` 切换至本阶段；`CURRENT_TASK.md` 已逐字节复制本阶段完整需求，索引中 P09 为唯一“当前”。本次未派发、未实现任何 P09 任务。
- 2026-10-26 用户决定与影响：
  - 用户明确授权开始 P09；按本文件任务表 T01～T05 顺序实施。
  - 确认实现完整“编辑资料”页（昵称/头像/简介），复用后端已有 `PATCH /users/me`；头像复用已有上传接口。
  - 确认设置页采用“三入口 + 说明页”：隐私设置/通知设置不新增任何开关、不改后端；应用帮助展示版本与使用说明。
  - 收藏与资料接口以现有后端契约为准（`GET /me/collections` 分页、条目为 serializeRecipe + collectedAt；`PATCH /users/me` nickname 1..32、avatar、bio ≤500），不新增后端规则。
  - 已核实：后端无隐私/通知设置接口或字段；Android `SocialApi` 只有收藏增删、`UserApi` 只有查询、`SessionManager` 是唯一共享登录态、`MineFragment` 收藏/设置仍为占位 Toast、`PAGE_MINE_INFO(7004)`/`PAGE_SETTING(7001)` 常量存在但无实现；收藏页放 `lib_cookbook` 以复用菜谱卡片，避免新增 lib_mine→lib_cookbook 依赖。
- 2026-10-26 P09-T01 完成与技术验收（Root 技术接受 → `Verified`）：
  - 实现范围：`RoutePath` 4 个路由常量；`SocialApi.listCollections`/`RecipeRepository.listCollections`（页码/页大小归一化）；`UserApi.updateProfile` + `ProfileUpdateBody` + `UserRepository.updateProfile`/`normalizedProfile`；`SessionManager.updateUser`（含账号一致性保护）；`UserProfileUpdateValidationTest` 8 例。未改 UI、未改后端。
  - 轮次与复测：R1 独立 verifier 执行完整清单 PASS（单测 78/78、三模块构建成功、真实后端契约通过），发现非阻断 O2（`updateUser` 账号切换竞态）；实施 Agent 最小修复后由 R1 同轮复测 1 次通过（单测 78/78、构建成功）。R2 为新派独立 verifier，使用全新测试账号从零重跑完整清单，单测 78/78（`cleanTest` 强制执行）、三模块构建成功、`GET /me/collections` 与 `PATCH /users/me` 契约/权限/边界全部 PASS，全程无问题，构成最终干净轮次。
  - 数据与安装：R1、R2 及 R1 复测前均清空涉及的测试数据，由当轮 verifier 重新造数；R2 使用全新测试账号，完成后测试数据清理完整。T01 无 UI 变更，安装“不适用（无 UI）”。
  - R2 非阻断观察：后端 `collectedAt` 当前未进客户端 DTO；客户端无法显式清空 avatar；代码索引待增量——已在本轮由 `code-auditor` 完成（202 条，检查 PASS）。
  - 结论：Root 技术接受 P09-T01 → `Verified`；阶段 Gate 未接受，未推进其他任务。
- 2026-10-26 P09-T02～T05 完成与技术验收（Root 技术接受 → `Verified`）：
  - 实现范围：T02 `MineFragment` 展示用户 ID、资料卡→个人信息、收藏→`PAGE_MY_COLLECTIONS`、设置→`PAGE_SETTING`，好友/订单保持，`openPage` 统一失败提示，资料保存后随 `SessionManager.state` 刷新。T03 `lib_cookbook` `CollectedRecipesFragment`（`PAGE_MY_COLLECTIONS`）：`GET /me/collections` 分页、下拉刷新/加载更多、登录/空/错误态、详情跳转、`onResume` 刷新、账号隔离。T04 `lib_mine` `MineInfoFragment`（`PAGE_MINE_INFO`）：昵称/简介/头像编辑，共享 `PickedImageReader`、复用 `RecipeRepository.uploadImage`，`PATCH /users/me` + `SessionManager.updateUser`，客户端校验、上传/保存禁用、真实错误、账号切换保护。T05 `SettingsFragment`（`PAGE_SETTING`）+ 隐私(7005)/通知(7006)/帮助(7007) 三页共享 `SettingInfoFragment`，无开关、无后端调用，帮助页读取真实 versionName。
  - 轮次与复测：一次真机 `10CECU0CFF001RY` R1 尝试在安装阶段因设备物理断连 BLOCKED，未执行 UI，**不计轮次**（数据已清理）。有效 R1（新独立 verifier，模拟器 `Medium_Phone_API_35`/API35/1080x2400）21 项 UI 清单全 PASS；构建成功、`lib_network` 单测 78/0/0；发现均为环境问题，无产品缺陷；修复复测 0 次。R2 为新派独立 verifier，从零清空并重建 A/B 账号及 21 份收藏，21 项全 PASS（含页面级 401 守卫、断网错误+重试、font_scale 1.3），构建/单测 78/0/0，全程无产品问题，构成最终干净轮次。
  - 数据与安装：两轮均由当轮 verifier 清空专用测试数据并重新造数（R2 使用全新账号）；完成后后端 users 仅 1–7、本轮菜谱/收藏为 0；应用由当轮 verifier 在模拟器亲自安装，模拟器已关闭。
  - 结论：Root 技术接受 P09-T02～T05 → `Verified`；索引已增量同步（204 条，check PASS）。阶段 Gate 仍未接受，等待用户按 Gate 清单手工验收 P09 后再切换 P10。
- 2026-10-27 用户决定（环境策略，不影响既有验收结论）：后续正式验证轮次默认使用真机 Xiaomi 12S（`2206122SC`，Android 15/API 35），设备句柄按型号解析、不写死 IP:port；vivo V2361A（Android 16/API 36）仅用于 Root 明确安排的兼容性烟测。规则已写入 `.claude/rules/04-verification-gates.md` 的“默认测试设备”，解析脚本为 `scripts/pick_test_device.sh`。
- 2026-10-27 R3 真机验证与手工验收环境（新独立 verifier；满足更新后的“默认测试设备”规则）：
  - 设备/构建/安装：Xiaomi 12S（`2206122SC`，serial `192.168.137.7:42089`，Android 15/API35，1440×3200，density 560）；当轮 verifier 亲自安装 APK（10,467,503 bytes，`com.rhys.main`，versionName 1.0.1）；`adb reverse tcp:3000` + `-PapiBaseUrl=http://127.0.0.1:3000/api/v1/`。
  - 数据与清理：A id=19、B id=20、C id=21（C 用于 401 守卫后删除）；A 21 份菜谱 ids 140–160 并全部收藏，page1=20/page2=1/total=21。测试后清理：users 仅 1–7、recipes/collections 0、设备图片/辅助包/reverse/font_scale 恢复、应用卸载。
  - 结果：21/21 UI 清单 PASS，无产品缺陷；构建成功、`lib_network` 单测 78/0/0；预期 RED（后端不可达保存失败、删除用户后 401）均按设计复现并恢复；同轮修复复测 0 次。全程从头执行且无产品问题，构成最终干净轮次。
  - 结论：Root 技术接受维持 `Verified`；阶段 Gate 仍未接受。
- 2026-10-27 手工验收环境（R3 后按用户要求由 `routine-executor` 重装）：后端在终端标签 `c1` 运行（健康 200，`127.0.0.1:3000`）；`adb reverse tcp:3000` 已设置；APK 安装 `Success`（13s，MIUI 确认弹窗 0 次），已启动 `com.rhys.main/.MainActivity` 并保持前台；未造业务数据、未清数据。证据 `app/build/p09-manual-install/`。终端 `c1` 或 reverse 失效时需重启后端并重设 reverse。

## 问题 / 修复历史及归档引用

- O1（T01，非阻断，已随 T01 变更处理）：`app/src/main/assets/nav/_navcookbook`、`_navmine`、`_navorder` 为构建生成资产，随 T01 工作树变更纳入提交范围，不作独立业务源码评审。
- O2（T01，非阻断，已修复）：`SessionManager.updateUser` 存在账号切换竞态。修复点：仅当当前状态为 `AuthState.LoggedIn` 且 `user.id` 与当前用户一致时才写入、发出 `AuthState.LoggedIn`，避免旧账号响应覆盖新账号状态；R1 同轮复测 1 次通过，R2 新轮完整重测无问题。
- O3（T01，非阻断，设计说明）：后端 `GET /me/collections` 返回 `collectedAt`，客户端 DTO 当前未消费；属后端字段预留，不影响收藏列表功能，按现有契约保留。
- O4（T01，非阻断，设计说明）：`PATCH /users/me` 不支持显式置空 avatar，客户端无法清空头像；按现有后端契约保留，未新增规则。
- O5（T02～T05，非阻断，环境）：真机 `10CECU0CFF001RY` R1 尝试在安装阶段因设备物理断连 BLOCKED，未执行 UI、未计为轮次，数据已清理；改用模拟器完成有效 R1/R2。
- O6（T04，非阻断，验证前已修复）：审阅发现头像占位不可点击选图；上传失败后未清除失效预览，可能导致保存无法继续。两处均在 R1 前修复，未产生轮次内修复复测。
- O7（T02～T05，非阻断，环境）：宿主机内存、模拟器冷启动无网络、adb 中文输入限制、限速上传失败文案；R2 网络恢复后第一次重试仍失败、第二次成功，判断为模拟器路由时序，非产品缺陷。
- O8（T02～T05 R3，非阻断，环境/工具，非产品缺陷）：B/C 登录后界面落在“菜谱”Tab，需再点“我的”（与 R2 一致的既有行为）；MIUI 阻止侧载辅助测试 APK，verifier 改用临时 legacy UiAutomator；为观察上传态使用本机限速代理。均为测试工具/环境限制，不影响产品结论。
