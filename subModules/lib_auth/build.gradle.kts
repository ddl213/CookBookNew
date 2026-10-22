plugins {
    alias(libs.plugins.com.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.com.google.devtools.ksp)
}

android {
    namespace = "com.rhys.auth"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    buildFeatures {
        viewBinding = true
    }

    ksp {
        arg("MODULE_NAME", "auth")
        arg("ROOT_DIR", rootDir.toPath().toString())
    }
    resourcePrefix = "auth_"
}

dependencies {
    //公共模块
    implementation(project(":lib_android_common"))
    implementation(project(":lib_campaign_common"))
    //网络与会话
    implementation(project(":lib_network"))
    //路由模块
    implementation(project(":lib_route_api"))
    implementation(project(":lib_route_annotation"))
    ksp(project(":lib_route_compiler"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
