pluginManagement {
    repositories {
        // 腾讯云聚合镜像：同时代理 Google Maven、Maven Central 等
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        // 兜底：见第 3 节
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
}
rootProject.name = "MusicPlayer"
include(":app")
