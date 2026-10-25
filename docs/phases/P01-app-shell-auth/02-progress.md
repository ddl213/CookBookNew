# P01 · 应用骨架与登录入口 · 执行记录

- 本阶段需求源：`docs/phases/P01-app-shell-auth/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：用户于 2026-10-22 明确回复“接受P01”。
- 下一动作：已切换到 P02；P02 实现前等待用户确认好友关系规则等决策。

## 用户决策（2026-10-22）

1. 认证方式：用户名 + 密码，与 CookBookService 现有注册/登录/JWT 接口一致。
2. 未登录范围：公开内容可浏览（首页公开菜谱、搜索、公开菜谱详情）；好友动态、评论、收藏、聊天、订单、预订、我的需登录；受限操作引导登录并在成功后回到原操作。
3. 接入 CookBookService：新增最小 Android 网络层（Retrofit/OkHttp）与 token 存储，打通注册、登录、当前用户。
4. 登录入口：独立登录/注册页；从受限操作进入时，登录成功后回到触发处继续原操作。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P01-T01 | 登录入口端到端基线（网络层、认证会话、登录/注册页、未登录态与回跳） | deep-executor | Verified（技术） | 构建/单测/接口/模拟器烟测通过；当时 UI 运行态待用户手测；2026-10-25 勘误：P01 已由用户接受，UI 运行态等待项已关闭；原记录保留。 |
| P01-T02 | 按实际改动增量更新代码能力索引 | code-auditor | Verified | 新增 22 条、全库 73 条；`code_index_check.py check` PASS；模块计数修正为 13 个 `lib_*` |
| P01-T03 | P01 独立构建、接口与安全回归验证 | verifier | Verified（UI 未重跑） | 18 项单测 0 失败；APK 9,663,995 bytes；`_navauth` 解密、debug/release 清单、后端契约、无密码/token 日志均通过；验证时无在线设备，UI 未重跑 |
| P01-T05 | 后端地址支持构建时配置（BuildConfig + -PapiBaseUrl） | routine-executor | Verified | 真实机 USB 调试地址修复；单测与带属性/默认值构建均通过；默认仍为 10.0.2.2 |

- 2026-10-25 状态勘误：P01-T01 证据中「UI 运行态待用户手测」为当时快照；P01 已由用户接受，等待项关闭；原记录保留。

### P01-T01 实施范围

- `lib_network`：Retrofit/OkHttp、AuthApi、DTO、错误解析、TokenInterceptor、SessionStore/SessionManager、AuthRepository。
- 新增 `lib_auth` 模块：登录页、注册页、表单校验、加载/错误状态、路由。
- `lib_campaign_common`：新增 `MainTabController` 接口与登录/注册 RoutePath。
- `lib_mine`：未登录态（登录/注册、浏览应用）、已登录态（资料卡、退出登录）、后续入口占位。
- `app`：MMKV 初始化、启动恢复会话、debug 明文 HTTP 配置、五导航宿主实现 `MainTabController`。
- 新增依赖（用户已确认接入后端）：Retrofit 2.11.0、OkHttp 4.12.0、converter-gson。

### 实施假设与边界

- Android 模拟器默认后端地址 `http://10.0.2.2:3000/api/v1/`；真机需改为局域网 IP，并让后端 `HOST=0.0.0.0`。
- debug 构建允许明文 HTTP；生产必须 HTTPS。
- token 存 MMKV（应用私有目录），本阶段不做额外加密；安全加固留待后续，不得记为已完成。
- 不实现 P02/P07/P08/P09 的业务页面；我的页仅保留入口占位。
- 不新增产品未定义规则；不改五导航名称/顺序。

## 当前阻塞与待决事项

- P01 的四项前置决策已解除。
- 后续阶段待确认项仍保留在 P00 记录中，不在 P01 重复展开。

## 实施结果、验证与 Gate

- 模块/接线：`lib_network` 新增 Retrofit/OkHttp、AuthApi、DTO、AuthRepository、SessionStore/SessionManager/AuthState、错误映射、`resolveMediaUrl`；新增 `lib_auth` 登录/注册页与 `AuthValidator`；`lib_campaign_common` 新增 `MainTabController` 与 `PAGE_LOGIN/PAGE_REGISTER`；`lib_mine` 未登录/已登录态；`app` MMKV 初始化、启动恢复会话、debug 明文 HTTP、`HomeFragment` 实现切 tab。
- 构建/单测：`:app:assembleDebug --max-workers=2` BUILD SUCCESSFUL；`:lib_auth:testDebugUnitTest` 12/12；`:lib_network:testDebugUnitTest` 6/6；独立验证合并 18/0。
- 后端契约：register 201、重复 409 USERNAME_TAKEN、错密 401、短用户名 400、login 200、me 200、无 token 401；字段与 `UserDto` 一致。
- 路由/清单：`_navauth` 解密为 `action://8001 LoginFragment`、`action://8002 RegisterFragment`；debug 含 `usesCleartextTraffic="true"`，release 不含；INTERNET 保留；五导航名称/顺序未变。
- UI 烟测：实现阶段在 API35 模拟器完成未登录/已登录、校验、401/断网、登录回跳、杀进程恢复、退出、浏览应用、注册自动登录；**头像 URL 修复后未重跑 UI**。
- 安全/回归：新增代码无密码/token 日志；token 仅存 MMKV；新增布局无 FrameLayout；长昵称/用户名 ellipsize；无无关页面修改。
- P01-T05：`NetworkConfig.baseUrl` 读取 `BuildConfig.API_BASE_URL`；Gradle 属性 `apiBaseUrl` 默认 `http://10.0.2.2:3000/api/v1/`；真机 USB 用 `adb reverse tcp:3000 tcp:3000` + `-PapiBaseUrl=http://127.0.0.1:3000/api/v1/`；局域网用开发机 IP 且后端 `HOST=0.0.0.0`。已核实根因：真机与电脑不同网段，原 10.0.2.2 不可达；服务日志无请求。

## 手动验收步骤（供用户）

1. 启动后端：`cd D:/CookBookService && npm start`（默认 127.0.0.1:3000；模拟器经 10.0.2.2 访问）。
2. 构建安装：`cd D:/projects/CookBook && ./gradlew :app:assembleDebug --max-workers=2`，安装 `app/build/outputs/apk/debug/app-debug.apk`。
3. 打开“我的”：应显示“登录/注册”和“浏览应用”。
4. 进入登录页：空表单提交应提示；错误密码应提示“用户名或密码错误”；正确登录后回到“我的”并显示昵称、@username、简介/头像占位。
5. 后端运行时杀进程重开：应恢复登录态。
6. 退出登录：应回到未登录态；“浏览应用”应切到首页。
7. 注册新用户：应自动登录并回到登录前的页面，资料显示正确。
8. 关闭后端再登录：应提示“无法连接服务器，请确认后端已启动”。
9. 小屏/大字体：登录/注册可滚动，昵称/用户名不溢出、不遮挡。

## 已知风险

- token 明文存 MMKV，未额外加密；真机需改 `NetworkConfig.baseUrl` 并让后端 `HOST=0.0.0.0`；release 禁止明文 HTTP；400 字段详情可能为英文；头像修复后 UI 未重跑。

## 问题 / 修复历史及归档引用

- （暂无）

## 补充验证（2026-10-23）
- 用户要求用运行时新建数据复测 P01/P02。
- P01 真机：新用户 qa1023c153344（昵称 QA-C）注册后自动登录；空表单提示、错误密码 401、force-stop 恢复、退出、浏览应用均 PASS。
- 证据：FINAL-QA-01；截图 `C:\Users\Admin\AppData\Local\Temp\final-qa-1023\`（29 张）。
