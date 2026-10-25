plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
}

// 数据源开关：-PchatDataSource=mock|remote；未指定时 debug 用 mock、release 用 remote。
val chatDataSourceOverride = providers.gradleProperty("chatDataSource")
    .orNull
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.rhys.chat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val defaultChatDataSource = chatDataSourceOverride ?: "mock"
        val escapedChatDataSource = defaultChatDataSource
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        buildConfigField("String", "CHAT_DATA_SOURCE", "\"$escapedChatDataSource\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 未显式指定 chatDataSource 时，release 默认连接真实服务端。
            if (chatDataSourceOverride == null) {
                buildConfigField("String", "CHAT_DATA_SOURCE", "\"remote\"")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // 与项目依赖使用同一镜像，避免 Robolectric 运行时绕过仓库配置。
            it.systemProperty("robolectric.dependency.repo.url", "https://maven.aliyun.com/repository/public")
        }
    }

    ksp {
        arg("MODULE_NAME", "chat")
        arg("ROOT_DIR", rootDir.toPath().toString())
    }
    resourcePrefix = "chat_"
}

dependencies {
    //所有项目公共模块
    implementation(project(":lib_android_common"))
    //当前项目公共模块
    implementation(project(":lib_campaign_common"))
    //网络与会话（复用统一 ApiClient、AuthException 与 DTO）
    implementation(project(":lib_network"))
    //路由模块
    implementation(project(":lib_route_api"))
    implementation(project(":lib_route_annotation"))
    ksp(project(":lib_route_compiler"))

    // 本地加密聊天缓存（Room 运行时 + KSP 编译器）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}