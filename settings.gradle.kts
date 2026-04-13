pluginManagement {
    repositories {
        maven { url = uri("https://www.jitpack.io") }
        maven { url = uri("'https://repo1.maven.org/maven2/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://www.jitpack.io") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/releases") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://artifact.bytedance.com/repository/Volcengine/") }
        google()
        mavenCentral()

    }
}

rootProject.name = "CookBook"
include(":app")

rootProject.projectDir.listFiles()?.toList()?.forEach { childFile ->
    if (childFile is File){
        if (childFile.isDirectory() && childFile.name == "subModules"){
            childFile.listFiles()?.toList()?.forEach { lib ->
                if (lib.name.startsWith("lib_")){
                    include(":${lib.name}")
                    project(":${lib.name}").projectDir = File(rootDir, "subModules/${lib.name}")
                }
            }
        }
    }
}
