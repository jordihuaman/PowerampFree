pluginManagement {
    repositories {
        // Order matters: AGP is hosted on Google's Maven, Kotlin on central/portal.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "PowerampFree"

val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    (System.getenv("LOCALAPPDATA")?.let { java.io.File(it, "Android/Sdk").exists() } == true)

if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("ANDROID_HOME/ANDROID_SDK_ROOT/LOCALAPPDATA not set: ':app' excluded from build.")
}
include(":core:audio")
