# P07 · 消息中心与好友一对一聊天 · 执行记录

- 本阶段需求源：`docs/phases/P07-messages-chat/01-requirements.md`
- 用户阶段验收：未接受
- 验收依据：未提供
- 下一动作：双轮完整流程已通过；已提交最终验收请求，等待用户明确接受；接受后按用户选择创建本地提交并切换 P08。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P07-T01 | 定义聊天与预订通知客户端契约（REST 会话/历史/发送、WebSocket 实时事件预留、权限/错误码/幂等规则），实现可替换数据源架构：`ChatDataSource` 接口、`RemoteChatDataSource`（按契约的 REST 实现）、`ChatRealtimeSource` 预留接口、数据源配置切换（Mock/真实，服务地址复用现有 `NetworkConfig.baseUrl` 与 Gradle 配置）、`ChatRepository`；契约文档 `docs/phases/P07-messages-chat/04-chat-contract.md` | standard-executor | Verified | 契约 `04-chat-contract.md` v1；`lib_network` ChatApi/NotificationApi/CursorPage/聊天 DTO/ChatErrorMapper/ApiClient 接入；`lib_chat` 可替换数据源、Mock/Remote、WebSocket 预留、配置切换（debug mock / release remote）、ChatRepository。独立 verifier：契约/API/错误映射/配置/Mock 分页幂等 PASS；`lib_network` 43 例、`lib_chat` 相关测试全绿；release 编译通过；无 WebSocket 硬依赖、无未读/已读字段。复核退回：契约 peer 可空性与 Mock 复合游标已修正并补测。 |
| P07-T02 | 实现 Mock 数据源与加密本地聊天存储：Mock 数据源需显式标识演示模式（不得伪装真实同步），支持模拟失败/延迟以验证状态；本地存储用 Room + Android Keystore AES-GCM 字段加密，按登录用户隔离、退出登录清理、clientMessageId 去重 | deep-executor | Verified | Room 加密缓存（消息正文/昵称/头像/最后消息/通知标题正文）、AES-256-GCM + Keystore、owner 隔离、退出/切号清理、SENDING→SENT/FAILED、clientMessageId 重试幂等、取消落 FAILED、会话稀疏合并、owner 落库前复核。独立 verifier 两轮复核 PASS；`lib_chat` 53 例、0 failures（含加密落盘、账号隔离、清理、竞态、取消、合并测试）；`app` 编译通过；nav 生成文件已还原。未覆盖：Android Keystore 真机加解密未执行（单测用 JVM 密钥）；真实后端未联调。 |
| P07-T03 | 实现消息列表页（会话列表 + 独立“预订通知”入口 + 加载/空/失败/断网重试状态 + Mock 模式标识），点击进入聊天详情 | standard-executor | Verified | 消息列表 + 独立预订通知入口 + `ChatNotificationsFragment`（action://5002）+ 会话参数 Key；登录/未知/未登录状态、Mock 横幅、首刷/下拉/滚动分页、空/错误/缓存内联重试、账号切换重建 Repository。独立 verifier 复核 PASS；`lib_chat` 83 例全绿（含 T03/T04 测试）；`:lib_chat:assembleDebug`、`:app:compileDebugKotlin` PASS；nav `_navchat` 含 2003/5002。 |
| P07-T04 | 实现一对一聊天详情页（历史分页、发送、失败重试、重复结果去重、未登录/非好友/无权访问拒绝；实时接收通过预留接口接入，后端未就绪时明确不可用而非伪造） | standard-executor | Verified | `ChatDetailFragment`（action://5001）：conversationId/peerUserId 双入口、`openConversation`、历史“加载更早”锚点、发送 SENDING/SENT/FAILED、失败气泡重试复用 clientMessageId、401/403/404、实时不可用横幅、离线缓存；好友主页 `btnMessage` 已接线。独立 verifier 两轮复核 PASS；Major（Mock 重入非演示会话 404）已修复：Mock 稳定 ID 区间惰性恢复、越界仍 404，并有跨实例测试；Minor（锚点优先级、深链负数归零）已修复。回归：`lib_chat` 83 例、`lib_network` 43 例真实执行，0 failures；`_navchat` 含 2003/5001/5002。IME Insets 修复：`ChatDetailFragment` 监听 `WindowInsetsCompat.Type.ime()`，输入栏底部 padding 跟随键盘，键盘收起归零，View 重建清空监听；真机复测 `14_keyboard_fixed/15_keyboard_sent/16_keyboard_hidden/17_reenter_keyboard` 全部 PASS；`lib_chat` 83 例、`lib_network` 43 例全绿；`:app:compileDebugKotlin` PASS。 |
| P07-T05 | 独立验证 P07-T01～T04、构建与手动 UI 验收步骤，并按实际改动增量更新代码索引 | verifier + code-auditor + progress-maintainer | Verified | 独立 verifier 两轮复核 T03/T04 PASS；Major（Mock 重入 404）与 Minor（锚点、深链负数、键盘遮挡）均修复；代码索引已增量同步（ARCH-017/ARCH-018/BIZ-012/BIZ-013/RES-012、TEST-021/027/028/029、DATA-037/039/049 等）；真机 Mock 烟测 PASS。第一轮（2026-10-25）：`pm clear com.rhys.main` → 冷启动登录引导 → 临时账号登录 → 消息列表（Mock 横幅/独立预订通知/林小厨）→ 下拉刷新无重复 → 预订通知页 → 聊天详情（3 条历史/左右气泡/实时不可用）→ 键盘上移并发送 `round1_ok` → 返回列表预览更新 → 退出登录 → 重新登录验证本地聊天清空（`round1_ok` 不存在/仅种子）→ 再次退出。全部 PASS；logcat 无 FATAL/AndroidRuntime/ANR。证据 `build/p07-ui-smoke/round1_*`。第二轮（2026-10-25，独立 verifier）：再次 `pm clear` → 同一账号登录 → 同一完整流程（标记 `round2_ok`）→ 退出/重登清理验证 → 最终退出并 `pm clear`。全部 PASS；logcat 283 行无 FATAL/ANR。证据 `build/p07-ui-smoke/round2_*`。结论：连续两轮完整流程无问题，满足用户的“连续两轮无问题才继续推进”条件；仍不代表用户阶段 Gate 已接受。 |

## 当前阻塞与待决事项

- 后端聊天接口尚未提供；已定“先定契约、可替换实现”，服务端就绪后接入。
- 实时消息后端未就绪，本期只预留 WebSocket 接口与契约，不实现真实连接。
- UI 验收若需临时后端夹具，须先取得用户明确同意。
- 生产环境 HTTPS/WSS 与聊天记录加密存储为用户已确认要求；开发期模拟器 HTTP 仅限本地联调。
- Android Keystore 真机加解密与真实后端联调尚未执行。
- 设备已退出登录并清空应用数据；后端临时账号 `p07smoke134134` 仍保留（未删除）；`adb reverse tcp:3000` 当前保留，可按用户决定移除；未提交 Git。
- 数据库清理语义以 ChatSessionGuard 的单测与登出行为为准；不把“UI 隐藏”写成“已证明消息行删除”。

## 实施结果、验证与 Gate

- 2026-10-25 用户决策：先定契约；预订通知为独立项；不做未读/已读；聊天记录加密存储。
- 2026-10-25 用户补充：服务端本地开发、后续云部署；REST 发送/历史 + WebSocket 预留；Mock/真实数据源可切换；不依赖未实现接口，Mock 不得伪装真实同步。
- 2026-10-25 验证状态：P07-T01/T02 已由独立 verifier 复核并建议 Verified；T03 尚未独立验证；阶段仍待用户 Gate。
- 2026-10-25 T03/T04 已由独立 verifier 建议 Verified；真机 UI 烟测尚未完成；阶段 Gate 未通过。
- 2026-10-25 真机烟测：设备 V2361A，`adb reverse tcp:3000` + 后端 127.0.0.1:3000，Mock 模式，临时账号 `p07smoke134134`（密码/token 不记录）。消息列表（Mock 横幅、独立预订通知入口、林小厨会话、无未读/红点）、预订通知（标题/返回/演示通知/Mock 横幅/无已读未读）、聊天详情（标题、左右气泡、实时不可用、输入栏）、发送 `hello_p07`（输入清空、单条新气泡、无失败/已读/送达）、返回列表（预览更新为 `hello_p07`、不回退、不重复）、键盘（输入栏在键盘上方、可直接发送、收起复位、重进仍生效）均 PASS；logcat 无 FATAL/ANR。未覆盖：好友主页“发消息”（临时账号无好友）NOT RUN；真实后端聊天/WebSocket、403/404/断网恢复未实测。证据：`build/p07-ui-smoke/`（构建产物，不纳入提交）。
- 2026-10-25 P07 技术验收完成；真机 UI 烟测通过（除注明 NOT RUN）；阶段 Gate 尚未通过。
- 2026-10-25 用户要求：按完整流程测试两遍；每轮测试前清空应用数据；第一轮有问题则修复并复测通过后才开第二轮；第二轮有问题则修复后开第三轮；只有完整流程无问题才继续推进。
- 2026-10-25 用户双轮规则已执行：连续两轮完整流程通过（含清空数据、登录、收发/键盘、退出重登清理），证据 `build/p07-ui-smoke/round1_*`、`round2_*`；仍不代表用户阶段 Gate 已接受。

## 问题 / 修复历史及归档引用

- 2026-10-25 P07-T01 复核退回：修正契约 peer 可空性与 Mock 分页游标/排序键不一致，已补测试并通过。
- 2026-10-25 P07-T02 三轮修复：Mock 发送者身份、Keystore 并发生成、退出在途写入、空文本密文、retryMessage owner 复核；P07-T03 参数语义修正。
- 2026-10-25 P07-T04 修复：Major（Mock 重入非演示会话 404）与 Minor（pendingAnchor 优先级、深链负数归零）。
- 2026-10-25 键盘遮挡输入栏：改为 IME Insets 监听修复，真机复测通过。
