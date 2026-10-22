# 07 · 已有依赖与构建工具

> 本类盘点状态：已盘点。已核对版本目录、根/模块 Gradle、wrapper、Manifest 与 `scripts/`；版本以仓库文件为源，不按常见版本猜测。

**收录范围：**项目已引入依赖、版本管理入口、Gradle convention、构建/自动化脚本与工具链。

**索引原则：**以实际仓库文件为源；写清库用途与版本来源，不照网上常见版本猜测。

| ID | 类 / API / 资源入口 | 用途与优先复用场景 | 调用方式、前提和限制 | 定义文件（项目相对路径） | 真实调用位置 / 核查证据 |
| --- | --- | --- | --- | --- | --- |
| DEP-001 | Gradle 版本目录 `libs.versions.toml` | 统一管理 SDK、Kotlin、AGP、KSP、AndroidX 与三方库版本。 | 当前 `compileSdk = 34`、`minSdk = 24`、`targetSdk = 34`、`versionCode = 1`、`versionName = 1.0.1`；AGP `8.1.1`、Kotlin `1.9.20`、KSP `1.9.20-1.0.14`、Gradle wrapper `8.4`、Java 17。`activityCompose`、`composeBom`、`activity`、`viewBinding` 等版本项存在但未全部使用；项目未启用 Compose。 | `gradle/libs.versions.toml` | `build.gradle.kts`、`app/build.gradle.kts`、各模块 `build.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties`、`gradle.properties`。 |
| DEP-002 | 公共 UI/基础库组合（AndroidX、Material、Navigation、MMKV、ImmersionBar、Glide、MagicIndicator） | `lib_android_common` 以 `api` 暴露核心库，业务模块可直接使用；新增页面优先复用这些库而非引入同类新库。 | 版本见 DEP-001；`core-ktx`、`fragment-ktx`、Navigation、Lifecycle、ConstraintLayout、RecyclerView、Material、ViewBinding、MMKV `2.0.1`、ImmersionBar `3.2.2`、Glide `4.16.0`、MagicIndicator `1.7.0`。`api` 会传递给依赖模块；升级前检查全项目兼容性，需 Root 授权。 | `subModules/lib_android_common/build.gradle.kts` | 各业务模块依赖 `:lib_android_common`；`HomeFragment` 使用 MagicIndicator，`BaseBindFragment` 使用 ImmersionBar，`ViewExt` 使用 Glide，`MMKVUtils` 使用 MMKV。 |
| DEP-003 | Room + KSP（`lib_cookbook`） | 预留给菜谱本地数据库；当前只有空 `RoomDatabase` 骨架。 | Room `2.6.1` runtime/ktx，KSP 引入 `room-compiler`；`ksp { arg("MODULE_NAME", "cookbook"); arg("ROOT_DIR", rootDir) }`。没有 `@Database/@Entity/@Dao`，也没有建库入口；直接复用前需补齐完整实现。 | `subModules/lib_cookbook/build.gradle.kts` | `CookBookDB.kt` 为空抽象类；全仓无 Room 注解与 `databaseBuilder`。 |
| DEP-004 | AndroidPicker（WheelPicker） | `lib_campaign_common` 暴露滚动选择框 Common/ColorPicker，供通用选择场景复用。 | 版本 `4.1.14`，通过 `api` 暴露；当前全仓未见 `com.github.gzuliyujiang` 或 WheelPicker 调用，属于已引入未使用依赖。不要为单个选择器再引入同类库。 | `subModules/lib_campaign_common/build.gradle.kts` | `libs.versions.toml` 的 `com-github-wheel-picker-*`；全仓检索无业务引用。 |
| DEP-005 | 自定义路由编译依赖（KotlinPoet、fastjson、KSP） | `lib_route_compiler` 在 KSP 阶段扫描 `@Route` 并生成加密 nav 资源。 | `kotlinpoet`/`kotlinpoet-ksp`/`kotlinpoet-metadata` `1.11.0` 为 `compileOnly`；`fastjson 1.2.69` 为 `implementation`；`commons-lang3 3.5`、`commons-collections4 4.1`、KSP API 为 `compileOnly`。模块同时应用 `org.jetbrains.kotlin.jvm` 与 `kotlin` 插件，建议确认是否重复。 | `subModules/lib_route_compiler/build.gradle` | `RouteProcessor` 使用 fastjson、`FileIOUtil`、`EncryptUtils`；`lib_route_api/build.gradle` 用 `compileOnly` 引入 Navigation、core-ktx、Gson。 |
| DEP-006 | 文档与阶段校验脚本 | 校验代码索引结构、源码路径、文件大小与阶段索引/当前任务一致性。 | 根目录执行 `python scripts/code_index_check.py check`；阶段一致性执行 `python scripts/phase_flow.py check`。脚本只做文档结构与路径校验，不证明业务实现或运行时行为；`--require-baseline` 要求索引为 `已盘点`。 | `scripts/code_index_check.py` | `scripts/phase_flow.py` 同目录；本次盘点已运行索引校验。 |
| DEP-007 | Gradle 仓库与 JDK 配置 | 配置插件/依赖仓库、AndroidX、非传递 R 类与本机 JDK 路径。 | `settings.gradle.kts` 的 `pluginManagement` 中存在 `uri("'https://repo1.maven.org/maven2/")`，带前导单引号，实际仓库 URL 可能无效；`gradle.properties` 的 `org.gradle.java.home` 指向本机绝对路径 `D:\software\jdk\jdk_17_0_17`，换机器需调整。`android.nonTransitiveRClass=true`，跨模块引用资源需使用完整包名 R。 | `settings.gradle.kts` | `gradle.properties`、`gradle/wrapper/gradle-wrapper.properties`；各模块 `resourcePrefix` 与 `nonTransitiveRClass` 影响资源命名。 |
