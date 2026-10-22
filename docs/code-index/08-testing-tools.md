# 08 · 测试与辅助工具

> 本类盘点状态：已盘点。已核对 `lib_android_common` 的 Robolectric 测试配置与 5 个真实测试类，以及各模块模板测试；模板测试不计为业务覆盖。2026-10-22 `P01-T02` 增量登记 `lib_auth`、`lib_network` 的纯 JVM 测试。

**收录范围：**现有单测工具、Fixture、测试基类、mock/脚本/局部验证入口。

**索引原则：**不因为登记而执行全量测试；注明可安全执行的范围及输入前提。测试通过只证明该测试范围，不代表阶段验收。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| TEST-001 | `lib_android_common` Robolectric 单测配置 | 在 JVM 上运行带 Android 资源的列表/刷新控件测试。 | 模块启用 `buildFeatures.viewBinding`、`testOptions.unitTests.isIncludeAndroidResources = true`，并设置 `robolectric.dependency.repo.url` 为阿里云公共仓库；依赖 JUnit `4.13.2`、Robolectric `4.12.2`。执行 `./gradlew :lib_android_common:testDebugUnitTest`。需要可下载依赖的网络环境；测试使用 SDK 28。 | `subModules/lib_android_common/build.gradle.kts` | 同模块 `src/test/java/com/android/common/` 下测试类；`libs.versions.toml` 的 `robolectric = 4.12.2`。 |
| TEST-002 | Adapter/列表回归测试套件 | 验证 Builder 配置、Diff、多布局、快照提交、点击下标、ConcatAdapter 查找与错误路径。 | 主线程 `submitList/addItems` 后需 `shadowOf(Looper.getMainLooper()).idle()` 等待 Diff；测试中自建 `ViewBinding` Fixture 和 `RecordingAdapter`。不要把这些 Fixture 当作业务布局。 | `subModules/lib_android_common/src/test/java/com/android/common/base/adapter/AdapterBuilderTest.kt` | 同目录 `BaseAdapterTest.kt`；`src/test/java/com/android/common/ext/RecyclerViewExtTest.kt` 覆盖控件级提交与 ConcatAdapter。 |
| TEST-003 | 刷新与列表装饰测试套件 | 验证下拉手势、刷新头状态、换头、Grid 占满一行、RvDecoration 间距与分割线。 | 需 Robolectric Activity 与真实 `RecyclerView` measure/layout；通过 `MotionEvent` 模拟触摸，使用 `shadowOf(Looper).idleFor(...)` 推进动画。仅覆盖竖向与已标注的 Grid 场景，未覆盖横向下拉。 | `subModules/lib_android_common/src/test/java/com/android/common/base/adapter/refresh/RefreshRecyclerViewTest.kt` | 同目录 `header/RefreshHeaderViewTest.kt`；`src/test/java/com/android/common/utils/RvDecorationRefreshTest.kt` 覆盖刷新头与间距集成。 |
| TEST-004 | 模块模板测试（非业务覆盖） | 仅验证基本断言或包名，作为新模块测试目录是否可运行的冒烟。 | `ExampleUnitTest` 断言 `2 + 2 == 4`；`ExampleInstrumentedTest` 断言 target package 为 `com.rhys.main`。不能据此宣称菜谱、订单、消息、登录等功能已验证。 | `app/src/test/java/com/rhys/cookbook/ExampleUnitTest.kt` | 各模块 `src/test`/`src/androidTest` 下同名模板；`app/src/androidTest/java/com/rhys/cookbook/ExampleInstrumentedTest.kt`。 |
| TEST-005 | `AuthValidatorTest`（12 例） | 验证登录/注册表单预校验：用户名 trim 后长度、空用户名、密码最小长度/空密码、昵称可选与最大长度、确认密码必填与一致性。 | 纯 JVM JUnit4，无 Android/Robolectric 依赖；执行 `./gradlew :lib_auth:testDebugUnitTest`。只覆盖 `AuthValidator` 纯函数，不覆盖登录/注册 UI、`SessionManager` 或后端校验；断言中包含中文错误文案，修改文案需同步测试。 | `subModules/lib_auth/src/test/java/com/rhys/auth/AuthValidatorTest.kt` | 被测入口 `subModules/lib_auth/src/main/java/com/rhys/auth/AuthValidator.kt`（BIZ-003）；`LoginFragment`/`RegisterFragment` 实际调用。 |
| TEST-006 | `NetworkConfigTest`（5 例） | 验证媒体 URL 解析：null/空白返回 null、绝对 http(s) 原样返回、根相对路径拼接默认 origin、带端口/路径的自定义 baseUrl 取 origin、其他相对值原样返回。 | 纯 JVM JUnit4；执行 `./gradlew :lib_network:testDebugUnitTest`。`@Before/@After` 会把 `NetworkConfig.baseUrl` 重置为默认值，避免污染其他测试；不发起 HTTP 请求，不覆盖 `ApiClient`/`AuthApi`/鉴权头。 | `subModules/lib_network/src/test/java/com/example/network/NetworkConfigTest.kt` | 被测入口 `subModules/lib_network/src/main/java/com/example/network/NetworkConfig.kt`（DATA-003）；`MineFragment` 使用 `resolveMediaUrl`。 |
