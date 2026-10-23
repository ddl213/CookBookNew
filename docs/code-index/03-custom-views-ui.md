# 03 · 自定义 View 与 UI 组件

> 本类盘点状态：已盘点。已核对 `lib_android_common`、`lib_photo_view`、`lib_cookbook`、`lib_index` 的自定义 View、Adapter、刷新、弹窗与图片查看入口；未证实调用或仅定义的组件已在子表注明。因内容接近 12 KiB，按功能拆为下面三个子表。2026-10-22 `P01-T02` 增量：登录/注册与“我的”页复用既有 `TitleBar`、`GEditText`、`GImageView`，本次未新增自定义 View/Adapter。`P02-T01` 增量：好友/搜索/申请/主页复用既有 `TitleBar`、`GEditText`、`GImageView` 与 `buildAdapter`/`RvDecoration`，仍未新增可复用自定义 View；修复 `TitleBar` 右侧文字可见但不可点的问题（VIEW-005）。`P03-T01` 增量：菜谱列表/表单复用既有 `TitleBar`、`GEditText`、`GImageView`、`RefreshRecyclerView`/`buildRefreshAdapter`；本次新增的卡片布局、分类 chip 与表单控件均为页面级实现，未登记为跨页面自定义 View。`P04-T02` 增量：详情页/评论/好友选择复用 `RvDecoration`、`buildAdapter`、`BaseBottomSheetDialog`，并更正 `RvDecoration` 间距与 `BasePhotoDialog` 调用两处过期描述。

**收录范围：**自定义 View、复合 View、通用 Adapter/Delegate、刷新列表、Dialog、标题栏、图片查看、进度组件。

**索引原则：**注明初始化方式、attrs、生命周期、样式依赖；只有源码存在的能力才登记，定义未使用不写成已接线。

## 子表导航

| 子表 | 主要内容 | 条目 |
| --- | --- | --- |
| [01 列表、Adapter 与刷新](03-custom-views-ui/01-lists-refresh.md) | RvDecoration、RefreshRecyclerView、AdapterBuilder/BaseAdapter、CommonFragmentAdapter | VIEW-001～VIEW-004 |
| [02 自定义 View 与样式控件](03-custom-views-ui/02-views-style.md) | TitleBar、G 系列样式控件、CircleLoadingView、ProcessView、ExpandableFlowLayout、标签标题 | VIEW-005～VIEW-012、VIEW-016 |
| [03 弹窗与图片查看](03-custom-views-ui/03-dialogs-photo.md) | BaseDialog/BaseBottomSheetDialog、SimpleDialog、PhotoView/BasePhotoDialog | VIEW-013～VIEW-015 |

## 覆盖与缺口

- 已覆盖 `lib_android_common` 全部自定义 View/Adapter/刷新/弹窗源码，以及 `lib_photo_view` 图片查看链路、`lib_cookbook`/`lib_index` 的标签标题实现。
- `ProcessView`、`SimpleDialog`、`ExpandableFlowLayout`、`IndexTitleView`、`DialogLargeImg` 只在源码中定义，全仓未见页面或布局调用；复用前需补接入与验证。
- `BasePhotoDialog` 当前全仓无页面调用，且固定加载占位图 `R.drawable.img_home_top`，没有使用被点击图片；这是已定义未接线能力，不是已完成功能（VIEW-015）。`BaseBottomSheetDialog` 的可用子类为 `ShareFriendDialog`（VIEW-013）。
