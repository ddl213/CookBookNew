# 04 · 布局、样式与资源

> 本类盘点状态：已盘点。已核对 `app` 与各模块 `res` 目录；只登记确实可复用的主题、颜色、selector、样式与图标。`TitleBar`、刷新头等布局由对应 View 自行 inflate，能力见分类 03，不在此重复。`P08-T03` 增量登记 `lib_order` 预订弹层/3.9 页面资源组（RES-013）；未新增跨模块复用资源。因内容接近 12 KiB，本类按场景拆为两个子表。

**收录范围：**重复使用的 XML 布局、主题、颜色、selector、style、attrs、通用图标与页面级资源组。

**索引原则：**不为每张一次性图片逐行建条目；优先登记页面通用规范和确实可复用资源。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 主题、颜色与按钮/头像](04-layouts-styles-res/01-theme-colors-buttons.md) | `Theme.CookBook`、基础颜色、主/次按钮 selector、头像占位 | RES-001、RES-002、RES-007～RES-009 |
| [02 selector、图标与页面资源组](04-layouts-styles-res/02-selectors-icons-groups.md) | chip/badge、聊天/订单页面资源组、Dialog 样式、底部导航与标题 selector、矢量图标 | RES-003～RES-006、RES-010～RES-013 |

## 覆盖与缺口

- 已覆盖 `app` 的主题与底部导航资源、`lib_android_common`/`lib_campaign_common` 的通用颜色、`lib_auth`/`lib_mine`/`lib_cookbook` 的按钮、chip、badge、头像占位，以及 `lib_chat`/`lib_order` 的页面级资源组。
- `FullScreenDialog`/`@style/dialog`、`index_selector_index_tags.xml` 属于已定义未使用资源；`BasePhotoDialog` 内部布局见 VIEW-015。
- 页面一次性 item 布局、无跨页面复用证据的 `index_*`/`chat_*`/`order_*` drawable 不逐条登记；复用前先核对模块前缀与依赖，不要复制成无前缀同名资源。
- 资源索引只说明文件存在与引用位置，不证明页面在真机上的视觉或适配已验收。
