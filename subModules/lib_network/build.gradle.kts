plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.network"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // 构建时可通过 -PapiBaseUrl=... 覆盖后端地址；默认仅适用于 Android 模拟器。
        val apiBaseUrl = providers.gradleProperty("apiBaseUrl")
            .getOrElse("http://10.0.2.2:3000/api/v1/")
        val escapedApiBaseUrl = apiBaseUrl
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        buildConfigField("String", "API_BASE_URL", "\"$escapedApiBaseUrl\"")

        // 订单数据源：默认 remote（debug/release 相同）；-PorderDataSource=mock 显式启用演示模式。
        // 非法值由 OrderDataSourceConfig 回退 remote，并由 OrderDataSourceFactory 记录 warning。
        val orderDataSource = providers.gradleProperty("orderDataSource")
            .getOrElse("remote")
        val escapedOrderDataSource = orderDataSource
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        buildConfigField("String", "ORDER_DATA_SOURCE", "\"$escapedOrderDataSource\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    resourcePrefix = "network_"
}

dependencies {

    implementation(project(":lib_android_common"))

    // 网络请求
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.com.google.code.gson)

    testImplementation(libs.junit)
}