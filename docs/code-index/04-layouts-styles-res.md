# 04 · 布局、样式与资源

> 本类盘点状态：已盘点。已核对 `app` 与各模块 `res` 目录；只登记确实可复用的主题、颜色、selector、样式与图标。`TitleBar`、刷新头等布局由对应 View 自行 inflate，能力见分类 03，不在此重复。

**收录范围：**重复使用的 XML 布局、主题、颜色、selector、style、attrs、通用图标。

**索引原则：**不为每张一次性图片逐行建条目；优先登记页面通用规范和确实可复用资源。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| RES-001 | `Theme.CookBook`、`Base.Theme.CookBook` | 应用主题入口，继承 `Theme.AppCompat.Light.NoActionBar`。 | 在 `AndroidManifest.xml` 的 `application.android:theme` 使用；当前无 `values-night` 与深色主题，新增夜间模式需另建资源。页面标题栏由布局或 `BaseBindFragment.initTitleBar` 提供，不依赖 ActionBar。 | `app/src/main/res/values/themes.xml` | `app/src/main/AndroidManifest.xml` 引用 `@style/Theme.CookBook`；`styles.xml` 不存在，主题仅两个 style。 |
| RES-002 | 通用颜色 `textTitle`、`textNormal`、`color_f0f0f0`、`color_e8edf2`、`color_0a80ed` 等 | 页面标题、正文、背景、搜索框、分割线等统一配色；原始风格为白底、暖橘/黄色强调。 | 同名颜色同时定义在 `lib_android_common` 与 `lib_campaign_common`，当前值一致；新增修改需同步两处或统一迁移，避免资源合并顺序造成取值差异。`color_0a80ed` 目前偏蓝，不是暖橘主色。 | `subModules/lib_android_common/src/main/res/values/colors.xml` | `IndexFragment`、`CookBookChildFragment`、`CookBookTitleView`、`IndexTitleView` 使用；`lib_campaign_common/src/main/res/values/colors.xml` 有同名副本。 |
| RES-003 | `FullScreenDialog`、`dialog` 样式 | 全屏透明 Dialog 与半透明遮罩 Dialog 的窗口样式。 | `FullScreenDialog` 设置透明窗口、全屏、透明状态栏/导航栏；`dialog` 设置浮动、半透明、透明状态栏与 0.2 遮罩。当前 `BasePhotoDialog` 自行设置窗口属性，未发现直接引用这两个 style。 | `subModules/lib_android_common/src/main/res/values/styles.xml` | 全仓检索无 `FullScreenDialog` 或 `@style/dialog` 引用，属于已定义未使用资源。 |
| RES-004 | 底部导航图标 selector `selector_navigator_item_*` | 五个底部入口的选中/未选中图标切换，配合 `BottomBarItem` 的 `isSelected`。 | 每个 selector 按 `android:state_selected` 切换 select/unselect PNG；新增入口必须同时提供两张图并保持命名 `img_tab_*`。当前入口顺序固定，不能只加图标不改导航。 | `app/src/main/res/drawable/selector_navigator_item_home.xml` | `HomeFragment.mTabIcon` 引用 home/recipes/chat/order/mine 五个 selector；对应 PNG 在 `app/src/main/res/drawable-xxhdpi/`。 |
| RES-005 | 标签文字颜色 selector `cookbook_title_view_selector`、`index_title_view_selector` | MagicIndicator 标题选中/未选中文字颜色。 | 选中使用 `textTitle`，未选中使用 `textNormal`；两个模块各有一份，结构相同但模块内聚。`CookBookTitleView` 与 `IndexTitleView` 分别在各自布局引用。 | `subModules/lib_cookbook/src/main/res/color/cookbook_title_view_selector.xml` | `cookbook_layout_title_view.xml` 引用本文件；`subModules/lib_index/src/main/res/color/index_title_view_selector.xml` 被 `index_layout_title_view.xml` 引用。 |
| RES-006 | 通用矢量图标 `vector_arrow_down_24`、`vector_close_24` | 展开/收起箭头与删除关闭图标，供标签流等动态控件复用。 | 以 `R.drawable.vector_arrow_down_24`、`R.drawable.vector_close_24` 引用；当前仅在 `ExpandableFlowLayout` 中使用，后者全仓未被页面调用。新增图标优先使用矢量资源，避免多倍图。 | `subModules/lib_android_common/src/main/res/drawable/vector_arrow_down_24.xml` | `ExpandableFlowLayout.init/delete` 引用两个图标；`vector_close_24.xml` 同目录。 |

> 说明：`common_layout_title_bar.xml`、`common_layout_refresh_header.xml` 分别是 `TitleBar`、`RefreshHeaderView` 的内部布局，复用入口见 VIEW-005、VIEW-002。`dialog_base_photo.xml` 是 `BasePhotoDialog` 的内部布局，见 VIEW-015。`index_selector_index_tags.xml` 目前为空 selector，且无引用，不建议复用。
