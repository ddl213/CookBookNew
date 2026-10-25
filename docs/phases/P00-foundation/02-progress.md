# P00 · 需求基线与实施边界 · 执行记录

- 本阶段需求源：`docs/phases/P00-foundation/01-requirements.md`
- 用户阶段验收：已接受
- 验收依据：用户于 2026-10-22 指示：P00 遗漏检查无问题后提交并开启下一阶段；遗漏项已补齐，P00 基线提交 67163e4。
- 下一动作：已切换到 P01；P01 实现前等待用户确认认证方式、未登录浏览范围与登录回跳。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P00-T01 | 首次全仓只读源码盘点 + 代码能力索引基线 | code-auditor | Verified | `docs/code-index/00-INDEX.md` 及 01–08 分类；`code_index_check.py check` PASS（已盘点、真实条目 51）；`--require-baseline` PASS；`phase_flow.py check` PASS（11 阶段、0 错误、当前 P00） |
| P00-T02 | 将已核实的基线核对与盘点结果登记到本进度文件 | progress-maintainer | Verified | 本文件记录；仅文档变更，不推进阶段。Root 已审核文件内容与两项结构校验结果（2026-10-22） |
| P00-T03 | 按 01-requirements 逐项复核并补齐待决事项阶段映射、登录认证前置与验证边界 | progress-maintainer | Verified | 本文件更新；Root 已审核；`phase_flow.py check` 与 `code_index_check.py check` 均 PASS（2026-10-22） |
| P00-T04 | 提交 P00 基线与能力盘点文档 | progress-maintainer | Verified | 仅受控本地提交；Root 已确认提交范围与校验结果（2026-10-22）；提交 `67163e4`（2026-10-22）；P00 已于 2026-10-22 被用户接受。 |

- 2026-10-25 状态勘误：P00-T04 原记 Implemented，实际受控提交 `67163e4` 已完成且 P00 已被用户接受，状态更正为 Verified；原记录保留。

## 当前阻塞与待决事项

缺失依赖：
- 产品原型图片（`image_8f*.png` 等）未随仓库提供。
- 产品文档中的 Figma 原型 `figma.com/make/pYhPJN1omnDD3PnOiGmme3`：当前 Figma 账号无编辑访问权限，MCP 返回 “don't have edit access”；所有 UI 阶段的视觉基准需用户另行提供可访问设计稿或截图。
- 后端接口与数据协议不在仓库，未核对。

高优先级（P00 内不决策，进入对应阶段前需用户确认）：
1. 订单完整状态机、接受/拒绝后续流转与完成标准（影响 P08，并影响 P07 订单通知）。
2. 预订确认弹窗字段、是否支持数量/多菜品、“加入购物车”的具体含义（影响 P04 详情预订入口与 P08 订单数据）。
3. 未登录浏览范围、登录后回跳与操作权限（直接阻塞 P01，并影响 P02、P04、P07、P08）。
4. 好友关系如何建立、变更、解除，解除后的内容与预订权限（直接阻塞 P02，并影响 P03/P04 可见性、P07 聊天、P08 预订权限）。
5. 登录/注册的认证方式（用户名密码、手机号等）产品未定义，项目内也不存在“既有登录组件”；P01 实现登录入口前需用户确认认证方式与界面形式，不能自行假定。

中优先级（对应页面实施前确认）：菜谱创建/编辑字段与分类校验 → P03；评论/收藏/分享具体布局与规则 → P04、P09；消息与订单通知形式 → P07、P08；推荐/热门/附近口径与数据来源 → P05、P06；评分与预计送达时间来源 → P08。
低优先级：个人信息隐私设置项 → P09。
未来（不属本期默认范围）：预订收费及语音/视频/文字条件 → P08 之后；群聊、直播、社区 → 不属当前阶段。

## 实施结果、验证与 Gate

### 已确认工程事实

| 项目 | 事实 |
| --- | --- |
| 仓库/分支 | `D:\projects\CookBook`，`master`；盘点开始时工作树干净；最近提交为刷新头相关优化 |
| 模块 | `:app` + 12 个 `lib_*`：android_common、campaign_common、chat、cookbook、index、mine、network、order、photo_view、route_annotation、route_api、route_compiler |
| 构建 | Gradle 8.4、AGP 8.1.1、Kotlin 1.9.20、KSP 1.9.20-1.0.14、JDK 17 |
| SDK/版本 | compileSdk 34、minSdk 24、targetSdk 34、versionName 1.0.1 |
| 约定 | ViewBinding；未启用 Compose；Navigation + 自研 `NRoute` KSP 路由；无 DI；Room 仅依赖与空骨架；已引入 MMKV/ImmersionBar/Glide/MagicIndicator/AndroidPicker |
| 五导航 | 已接线：首页、菜谱、消息、订单、我的；`HomeFragment` + ViewPager2 + MagicIndicator，名称顺序符合产品 |
| 首页 | `IndexFragment` 存在；4 个 RecyclerView 均填同一组“推荐/关注”，条目布局为空；`IndexChildFragment` 未接入导航 |
| 菜谱 | 已发布/草稿两个标签复用同一 `CookBookChildFragment`，未读取标签参数，展示硬编码 6 条 `Recipe`，刷新为 2 秒模拟；添加菜谱页仅标题栏 |
| 消息/订单/我的 | 仅 `TitleBar` 占位；无列表、无订单状态、无个人资料 |
| 登录 | 不存在登录/注册页面或鉴权逻辑 |
| 网络/数据库 | 无 Retrofit/OkHttp/API/Repository；Room 无 `@Database`、实体、DAO、建库调用；`MMKVUtils` 未初始化且无业务调用 |
| 订单 | 无订单模型、状态机、接受/拒绝/备货逻辑 |

### 产品约束映射
- 统一用户体系：不得创建“普通用户/创作者”两套注册角色。
- 五底部入口固定：首页、菜谱、消息、订单、我的；不新增、替换或重命名。
- 菜谱可见性“好友可见/所有人可见”：必须在数据访问侧生效；当前 `Recipe` 模型尚无可见性字段。
- 预订：仅好友之间、当前免费；“加入购物车”只跳“预订确认弹窗”，不据此新增独立购物车/支付。
- 订单：我的订单=发起，我的销售=收到，同一笔订单双视角；原文未定义完整状态机。
- 未来规划（收费、语音/视频/文字交换、群聊、直播、社区）不属本期默认范围。

### 验证与证据
- `python scripts/code_index_check.py check`：PASS（文档结构校验；状态“已盘点”；真实条目 51；退出码 0）。
- `python scripts/phase_flow.py check`：PASS（11 个阶段、0 错误、当前 P00；退出码 0）。
- 索引登记数：ARCH 8、UTIL 8、VIEW 16、RES 6、DATA 2、BIZ 0、DEP 7、TEST 4，共 51；分类 06 已核实无跨页面业务组件；03 已拆为 3 个子表，最大文件 6,621 bytes。
- 验证性质：仅文档/索引结构层面，不代表运行时功能验证；不替代用户 Gate。
- 本阶段未执行 Android 构建或运行时验证；工程事实来自源码、Gradle、Manifest 与资源文件的静态核对，构建可用性尚未验证。
- 本阶段未新增产品代码、页面占位或依赖；“最小实施/验收约定”沿用 `.claude/rules/04-verification-gates.md` 与 `.claude/rules/08-code-standards-and-reuse.md`。

### 已知工程风险
- `BasePhotoDialog` 固定加载 `R.drawable.img_home_top`，未展示被点击图片（`subModules/lib_photo_view/src/main/java/com/photo/view/dialog/BasePhotoDialog.kt:106`）。
- `NRoute` 生成的 `app/src/main/assets/nav/_nav*` 纳入版本库，构建未执行时路由可能过期。
- `settings.gradle.kts` 中 `uri("'https://repo1.maven.org/maven2/")` 带前导单引号，仓库地址可能无效。
- `gradle.properties` 硬编码本机 JDK 路径。
- `lib_android_common` 与 `lib_campaign_common` 存在同名颜色资源。
- `MMKVUtils` 未初始化；`ProcessView`、`SimpleDialog`、`ExpandableFlowLayout`、`IndexTitleView`、`DialogLargeImg` 已定义但无调用。
- `RoutePath` 中详情、设置、地址、聊天详情、订单详情等常量无实现类，不能据此认定页面存在。
- `Recipe` 模型缺少可见性、食材清单、制作步骤等产品要求字段。

### 建议下一步
- 用户审核 P00 基线；若接受 P00，再准备 P01。
- P01 实现前先确认“未登录浏览范围”，并同步确认登录入口/回跳与操作权限。

## 问题 / 修复历史及归档引用

- （暂无）
