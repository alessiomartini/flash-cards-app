pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Repositories are declared per-module (see app/build.gradle.kts and core/build.gradle.kts)
// rather than centralized here, on purpose: the :core module is pure Kotlin/JVM and only
// ever needs Maven Central, while :app additionally needs Google's Maven repo for AndroidX
// and Jetpack Compose. Keeping them separate means :core can be built and tested in
// network environments that only allow Maven Central.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
}

rootProject.name = "Lexi"

include(":app")
include(":core")
