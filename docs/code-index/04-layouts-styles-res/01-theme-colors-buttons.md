# 04-01 · 主题、颜色与按钮/头像

> 返回分类首页：[04-layouts-styles-res.md](../04-layouts-styles-res.md)。selector、图标与页面资源组见 [02-selectors-icons-groups.md](02-selectors-icons-groups.md)。

**收录范围：**应用主题、基础颜色、主/次按钮 selector、头像占位。

**索引原则：**只登记跨页面确可复用的主题、配色与控件背景；页面一次性资源见 02 子表或对应模块。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| RES-001 | `Theme.CookBook`、`Base.Theme.CookBook` | 应用主题入口，继承 `Theme.AppCompat.Light.NoActionBar`。 | 在 `AndroidManifest.xml` 的 `application.android:theme` 使用；当前无 `values-night` 与深色主题，新增夜间模式需另建资源。页面标题栏由布局或 `BaseBindFragment.initTitleBar` 提供，不依赖 ActionBar。 | `app/src/main/res/values/themes.xml` | `app/src/main/AndroidManifest.xml` 引用 `@style/Theme.CookBook`；`styles.xml` 不存在，主题仅两个 style。 |
| RES-002 | 通用颜色 `textTitle`、`textNormal`、`color_f0f0f0`、`color_e8edf2`、`color_0a80ed`、`color_primary`、`color_primary_pressed` 等 | 页面标题、正文、背景、搜索框、分割线、主按钮与选中文字等统一配色；主色为暖橘 `#FF8A00`，按下色 `#E67A00`。 | `textTitle`、`textNormal`、`color_*` 等基础色同时定义在 `lib_android_common` 与 `lib_campaign_common`，当前值一致；`color_primary`/`color_primary_pressed` 目前只在 `lib_android_common`。新增修改需同步重复副本或统一迁移，避免资源合并顺序造成取值差异。`color_0a80ed` 偏蓝，不是主色。 | `subModules/lib_android_common/src/main/res/values/colors.xml` | `IndexFragment`、`CookBookChildFragment`、`CookBookTitleView`、`IndexTitleView` 使用基础色；`LoginFragment`/`RegisterFragment`、`MineFragment` 的按钮、错误文字、进度条与选中文字使用 `color_primary`（见 RES-007～RES-009）。 |
| RES-007 | 主色按钮 selector `auth_bg_primary_button` | 登录/注册主按钮的圆角、主色与按下色；需要“主操作按钮”样式时可复用其状态结构。 | 模块内资源名带 `auth_` 前缀；引用 `@color/color_primary`、`@color/color_primary_pressed`，圆角固定 12dp；`state_enabled=false` 仍使用主色，没有明显禁用视觉，禁用只靠 `isEnabled` 阻止点击。与 `mine_bg_primary_button` 结构相近但各自维护，跨模块使用需通过完整资源名引用，后续宜统一而非再复制第三份。 | `subModules/lib_auth/src/main/res/drawable/auth_bg_primary_button.xml` | `auth_fragment_login.xml` 的 `btn_login`、`auth_fragment_register.xml` 的 `btn_register` 使用。 |
| RES-008 | 次按钮 selector `mine_bg_secondary_button` | “我的”页次要操作按钮（浏览应用、退出登录）的白底描边、按下浅灰样式。 | 模块内资源名带 `mine_` 前缀；白底 + `color_e8edf2` 1dp 描边 + 12dp 圆角，按下背景 `color_f0f0f0`；没有禁用态。仅适合浅色页面，深色背景需另建资源。 | `subModules/lib_mine/src/main/res/drawable/mine_bg_secondary_button.xml` | `mine_fragment_mine.xml` 的 `btn_browse`、`btn_logout` 使用；同目录 `mine_bg_primary_button.xml` 为已登录/未登录主按钮。 |
| RES-009 | 头像占位 `mine_bg_avatar_placeholder` | 用户无头像或头像 URL 为空时，用主色圆形背景 + 昵称首字作为占位。 | 固定主色 `color_primary` 的 oval；只负责背景，文字与首字截断由页面处理（`MineFragment.firstCharacter` 避免截断 emoji 代理对）。头像加载入口见 UTIL-005、URL 解析见 DATA-003。 | `subModules/lib_mine/src/main/res/drawable/mine_bg_avatar_placeholder.xml` | `mine_fragment_mine.xml` 的 `tv_avatar_fallback` 使用；`MineFragment.bindUser()` 在头像为空时显示。`lib_cookbook` 另有一份同结构 `cookbook_bg_avatar_placeholder.xml`，供详情页/分享弹窗使用，后续可考虑统一而非继续复制。 |

> `cookbook_bg_primary_button`/`cookbook_bg_secondary_button` 与 RES-007/RES-008 结构相近，按模块内聚未重复登记；`chat_bg_*`、`index_bg_*`、`order_bg_*` 等页面级按钮/占位背景见各页面资源组（RES-012、RES-013）。
