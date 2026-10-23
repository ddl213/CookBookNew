# P03 · 个人菜谱发布与管理 · 历史 001

- 归档日期：2026-10-23
- 来源：`02-progress.md` 的 P03-T04 详细回归记录，完整迁移。
- 保留当轮 FAIL / 未 Verified 结论；后续修复及当前阶段状态见 [执行记录](02-progress.md)。

### P03-T04 回归范围（2026-10-23）

- 职责：verifier；覆盖 P01 应用骨架与登录入口、P02 好友关系与用户主页、P03 个人菜谱发布与管理的已实现功能。
- 边界：Android 既有业务单测、公共组件回归与 debug 构建；CookBookService 本地自动测试及使用新鲜测试数据进行 API 权限回归；在可用设备上验证主要 UI 流程。
- 输入与验收：以当前工程、既有基线、本地服务和设备为输入，记录本次命令、预期/实际及可复核证据，由 Root 审阅；未覆盖项不计 PASS，不沿用历史结果。
- 当轮结论：回归执行完成，结果 FAIL、未 Verified；1 项匿名访问需求缺口与 2 项 UI 状态恢复缺陷当时均未修复；后续闭环见 T05，保留本轮结论。
- 授权限制：本次仅回归已实现功能，不授权新增功能；P03 用户阶段验收仍未接受，不推进 P04。

#### P03-T04 自动测试与 API 结果（2026-10-23）

- Root 已核对本次 Android XML/Gradle 与后端 npm/HTTP 证据；下列结果仅代表相应验证范围。
- Android：107/107（104 业务/公共组件 + 3 模板），0 failure/error/skip；debug 构建退出 0。APK 10,934,025 bytes，生成时间 `2026-10-23 17:09:37+08:00`；API base URL 为 `http://127.0.0.1:3000/api/v1/`。
- CookBookService：`npm test` 67/67、退出 0；使用新鲜测试数据的本地真实 HTTP 回归共 114 请求、151 断言，149 PASS、2 FAIL。
- 两项失败属于同一 P01 匿名公开浏览需求缺口：匿名 `GET /api/v1/recipes/public?q=qaR20261023091304c654` 和 `GET /api/v1/recipes/9`（public/published）均预期 200，实际 401 `UNAUTHORIZED`；本次未修复。
- 证据根目录：`C:\Users\Admin\AppData\Local\Temp\cookbook-regression-20261023\`；Android 见 `android/command.txt`、`gradle-test-assemble.log`、`test-suites.csv`、`route-comparison.json`；后端见 `backend/npm-test.log`、`live-api-summary.json`、`live-api-events.jsonl`。
- 生成资产：`app/src/main/assets/nav/_navauth`、`_navrelation` 与 HEAD/APK 的完整路由明文一致，仅随机 IV 造成差异；还原前再次确认当前 diff 与 `android/generated-route-assets.diff` 一致且资产与 APK 相同，按 Root 精确授权仅将这两个文件还原到 HEAD。

#### P03-T04 UI 最终结果（2026-10-23）

- 结果与设备：24 项，22 PASS、2 FAIL、0 BLOCKED；Root 已复核最终记录、关键截图/控件 XML/API 断言与源码。真机 V2361A 与隔离只读 API 35 `emulator-5556` 使用同一 APK；真机两次被通知抢前台后转模拟器，用户后确认可继续操作。字体 1.5 时菜谱保存按钮无截断，已恢复 1.0。
- 主要覆盖：注册回跳、错密、重启会话恢复、退出；菜谱必填、草稿 CRUD、真实选图上传/移图、分类与多行、状态/可见性、断网不假成功；好友申请接受/拒绝、解除后的可见性、外部先删后的错误反馈。详情仍为既有占位，未新增功能。
- FAIL · 好友申请返回空白：B 的申请页已有 2 条 pending，进入 A 主页后通过标题返回；预期仍显示申请，实际仅标题/Tab 和空 RecyclerView，持续空白，而 API 仍返回 `total=2`。临时恢复：退回好友列表再进入申请页后恢复，此后通过 UI 接受 A、拒绝 C 成功。
- 申请页原因推断（与只读源码一致）：`subModules/lib_relation/src/main/java/com/rhys/relation/request/FriendRequestsFragment.kt:59`，59–62 重建 adapter，170 因保留的 `loaded` 跳过 `loadFirst`，229–230 只在请求完成时提交 items；证据见 `device/api-blank-requests-still-pending.json`、`emu-42-return-requests-blank` 与 `emu-42a-reentered-requests` 的 PNG/XML。
- FAIL · 好友搜索返回丢失结果：B 搜索 A 得到结果，进入 A 主页后返回；预期保留搜索词与结果，实际搜索词仍在、结果消失并显示初始提示。实测临时恢复：再次点击“搜索”后结果恢复。
- 搜索页原因推断（与只读源码一致）：`subModules/lib_relation/src/main/java/com/rhys/relation/search/FriendSearchFragment.kt:56`，56 起重建 adapter，102 调用 `showInitial`，147 的 `loaded` 条件没有重绑结果；证据见 `device/emu-49-search-A-result`、`emu-50-A-profile-from-search`、`emu-51-search-return-state` 的 PNG/XML 及 `ui-search-return-observation.json`。
- 共同生命周期依据：`subModules/lib_android_common/src/main/java/com/android/common/base/BaseBindFragment.kt:23` 重建时 inflate/调用 `initView`，81–85 销毁时清空 binding；仅为原因推断，本轮无业务源码修改、无修复、无提交。
- 清理：本次菜谱 12/13/14 均 GET 404，`mine?status=all` total 0。手机已 UI 退出，font 1.0、reverse 恢复原空，精确删除测试图和 dump；只读 AVD 已关闭，原数据未改。测试账号、后端合成上传图及申请历史仍留开发库，本轮菜谱均已清理。
- 最终证据：本轮根目录下 `device/final-result.json`、`steps-index.txt`、`api-ui-delete-final.json`、`phone-cleanup.json`、`final-adb-devices.txt`；未覆盖窄屏矩阵、非空个人简介/自定义头像独立 UI、未登录关系页直接 deep link，不计为 PASS。

