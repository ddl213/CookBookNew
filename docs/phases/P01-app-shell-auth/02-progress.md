# P01 · 应用骨架与登录入口 · 执行记录

- 本阶段需求源：`docs/phases/P01-app-shell-auth/01-requirements.md`
- 用户阶段验收：未接受
- 验收依据：未提供
- 下一动作：按 P01-T01 范围实施“登录入口端到端基线”；实施完成后再安排 P01-T02 独立验证。

## 用户决策（2026-10-22）

1. 认证方式：用户名 + 密码，与 CookBookService 现有注册/登录/JWT 接口一致。
2. 未登录范围：公开内容可浏览（首页公开菜谱、搜索、公开菜谱详情）；好友动态、评论、收藏、聊天、订单、预订、我的需登录；受限操作引导登录并在成功后回到原操作。
3. 接入 CookBookService：新增最小 Android 网络层（Retrofit/OkHttp）与 token 存储，打通注册、登录、当前用户。
4. 登录入口：独立登录/注册页；从受限操作进入时，登录成功后回到触发处继续原操作。

## 任务表

| 任务 ID | 目标 / 范围 | 执行职责 | 状态 | 证据/下一动作 |
| --- | --- | --- | --- | --- |
| P01-T01 | 登录入口端到端基线（网络层、认证会话、登录/注册页、未登录态与回跳） | standard-executor（待分配） | Ready | 范围见下；确认已解除，可实施 |
| P01-T02 | P01 独立验证与代码索引增量更新 | verifier / code-auditor（待分配） | Pending | 独立构建 + 手动 UI 验收 + 索引增量更新 |

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

- 已确认方案与范围，尚未开始实现。

## 问题 / 修复历史及归档引用

- （暂无）
