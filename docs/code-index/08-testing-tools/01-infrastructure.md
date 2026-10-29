# 08-01 · 测试基础设施与框架测试

> 返回分类首页：[08-testing-tools.md](../08-testing-tools.md)。业务能力回归测试见 [02-business-tests.md](02-business-tests.md)。

**收录范围：**Android 资源/Robolectric 配置、Adapter/列表/刷新框架测试套件、模块模板测试。

**索引原则：**不因为登记而执行全量测试；模板测试不计为业务覆盖；注明可安全执行的范围及输入前提。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| TEST-001 | Android 资源与 Robolectric 单测配置 | 在 JVM 上运行带 Android 资源的控件与 Fragment 测试；`lib_relation` 复用公共模块的既有配置。 | 模块启用 `buildFeatures.viewBinding`、`testOptions.unitTests.isIncludeAndroidResources = true`，并设置 `robolectric.dependency.repo.url` 为阿里云公共仓库；依赖 JUnit `4.13.2`、Robolectric `4.12.2`。按范围执行 `./gradlew :lib_android_common:testDebugUnitTest` 或 `:lib_relation:testDebugUnitTest`。需要可下载依赖的网络环境；现有 Robolectric 测试使用 SDK 28。 | `subModules/lib_android_common/build.gradle.kts` | 同模块 `src/test/java/com/android/common/` 下测试类；`libs.versions.toml` 的 `robolectric = 4.12.2`；`subModules/lib_relation/build.gradle.kts` 复用该配置，测试见 TEST-013。 |
| TEST-002 | Adapter/列表回归测试套件 | 验证 Builder 配置、Diff、多布局、快照提交、点击下标、ConcatAdapter 查找与错误路径。 | 主线程 `submitList/addItems` 后需 `shadowOf(Looper.getMainLooper()).idle()` 等待 Diff；测试中自建 `ViewBinding` Fixture 和 `RecordingAdapter`。不要把这些 Fixture 当作业务布局。 | `subModules/lib_android_common/src/test/java/com/android/common/base/adapter/AdapterBuilderTest.kt` | 同目录 `BaseAdapterTest.kt`；`src/test/java/com/android/common/ext/RecyclerViewExtTest.kt` 覆盖控件级提交与 ConcatAdapter。 |
| TEST-003 | 刷新与列表装饰测试套件 | 验证下拉手势、刷新头状态、换头、Grid 占满一行、RvDecoration 间距与分割线。 | 需 Robolectric Activity 与真实 `RecyclerView` measure/layout；通过 `MotionEvent` 模拟触摸，使用 `shadowOf(Looper).idleFor(...)` 推进动画。仅覆盖竖向与已标注的 Grid 场景，未覆盖横向下拉。 | `subModules/lib_android_common/src/test/java/com/android/common/base/adapter/refresh/RefreshRecyclerViewTest.kt` | 同目录 `header/RefreshHeaderViewTest.kt`；`src/test/java/com/android/common/utils/RvDecorationRefreshTest.kt` 覆盖刷新头与间距集成。 |
| TEST-004 | 模块模板测试（非业务覆盖） | 仅验证基本断言或包名，作为新模块测试目录是否可运行的冒烟。 | `ExampleUnitTest` 断言 `2 + 2 == 4`；`ExampleInstrumentedTest` 断言 target package 为 `com.rhys.main`。不能据此宣称菜谱、订单、消息、登录等功能已验证。`lib_campaign_common` 原先缺少单测依赖，`P10-T02-F01` 补 `testImplementation(libs.junit)` 后其模板单测可编译运行，仍不构成业务覆盖（DEP-004）。 | `app/src/test/java/com/rhys/cookbook/ExampleUnitTest.kt` | 各模块 `src/test`/`src/androidTest` 下同名模板；`app/src/androidTest/java/com/rhys/cookbook/ExampleInstrumentedTest.kt`；`subModules/lib_campaign_common/src/test/java/com/campaign/common/ExampleUnitTest.kt` 与其 `build.gradle.kts`。 |
