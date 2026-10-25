# 01-02 · 模块、路由与页面参数

> 返回分类首页：[01-project-architecture.md](../01-project-architecture.md)。应用入口与基类见 [01-app-shell.md](01-app-shell.md)。

**本子类盘点状态：已盘点。**收录范围：页面路由常量、功能模块入口、路由参数约定；只登记可复用定位入口，不把路由常量当作已实现页面。因条目增长超过 12 KiB，按场景拆为路由参数与模块入口两个子表。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 路由常量与页面参数](02-routes-pages.md) | RoutePath、用户主页/菜谱/首页/创作者路由参数 | ARCH-008、ARCH-012、ARCH-014～ARCH-016 |
| [02 功能模块入口](03-module-entries.md) | `lib_auth`、`lib_relation`、`lib_cookbook` 模块边界与页面注册 | ARCH-009、ARCH-011、ARCH-013 |
| [03 聊天页面与路由参数](04-chat-pages.md) | `lib_chat` 消息/通知/聊天详情页面组、聊天路由参数与好友发消息入口 | ARCH-017、ARCH-018 |
| [04 预订与订单页面组](05-order-pages.md) | `lib_order` 3.9 预订页、订单列表/详情、订单参数与动作边界 | ARCH-019、ARCH-020 |
