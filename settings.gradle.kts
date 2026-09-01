pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "pulse"

includeBuild("plugin")
include(":lint-rules")
include(":sdk:shared")
include(":sdk:ui")
include(":sdk:client")
include(":sdk:server")
include(":sdk:emulator")
include(":tool")
include(":examples:pulse")
project(":examples:pulse").projectDir = file("examples/pulse")
