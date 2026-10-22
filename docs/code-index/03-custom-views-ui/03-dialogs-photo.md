# 03-03 · 弹窗与图片查看

> 返回分类首页：[03-custom-views-ui.md](../03-custom-views-ui.md)

**收录范围：**ViewBinding Dialog/BottomSheet、Builder Dialog、PhotoView 与全屏图片查看。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| VIEW-013 | `BaseDialog`、`BaseBottomSheetDialog` | 基于 ViewBinding 的 Dialog/BottomSheet 基类，统一透明背景、初始化顺序和生命周期清理。 | 子类传入 `inflate`；实现 `initView`，可选 `initData/initListener/DestroyDialog`；提供 `setWindowSize`、`setDialogCancelable`、`setDialogCanceledOnTouchOutside`、`dismissDialog`、`hideDialog`。`onStop` 清空 binding，销毁后不可再访问。 | `subModules/lib_android_common/src/main/java/com/android/common/base/BaseDialog.kt` | `DialogLargeImg` 继承 `BaseDialog`，但布局为空且全仓无调用；`BaseBottomSheetDialog` 同目录，未发现子类。 |
| VIEW-014 | `SimpleDialog` | 生命周期感知的 Builder 式 Dialog，可指定布局、点击 View、尺寸、位置、主题。 | `SimpleDialog.with(activityOrFragment).setLayout(...).setInitView {...}.setClickViews(...).setInitClick {...}.show()`；布局 ID 必填，否则 `onCreate` 抛异常；Builder 只接受 `ComponentActivity` 或 `Fragment`。当前全仓未见调用。 | `subModules/lib_android_common/src/main/java/com/android/common/view/dialog/SimpleDialog.kt` | 全仓检索仅定义，无页面调用。 |
| VIEW-015 | `PhotoView`、`BasePhotoDialog` | 图片缩放、双击、拖拽、拖拽退出动画及全屏查看 Dialog。 | `BasePhotoDialog.show(activity, view)` 从被点击 View 取全局矩形作为动画起点；布局必须包含 id 为 `photoView` 的 `PhotoView`；支持返回键、点击外部、下拉退出。当前实现固定加载 `R.drawable.img_home_top`，不会使用被点击图片，接入真实图片前需扩展。 | `subModules/lib_photo_view/src/main/java/com/photo/view/dialog/BasePhotoDialog.kt` | `CookBookChildFragment` 在菜谱图片子控件点击时调用 `BasePhotoDialog.show(requireActivity(), view)`；`PhotoView.kt`、`PhotoViewAttacher.kt` 提供缩放/拖拽能力；`dialog_base_photo.xml` 为内部布局。 |
