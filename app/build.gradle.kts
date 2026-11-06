plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hallowed.randomthing"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hallowed.randomthing"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
