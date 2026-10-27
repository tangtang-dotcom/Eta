pluginManagement {
    repositories {
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // HugeIcons（过程步骤图标）发布在 JitPack 上，与 rikkahub 使用同一套图标。
        // 只放行 com.github.rikkahub 这一个 group，避免其它依赖改走 JitPack 拉取。
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.rikkahub")
            }
        }
    }
}

rootProject.name = "Eta"
include(":app")
