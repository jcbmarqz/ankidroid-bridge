plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.marqjaco.ankidroidbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marqjaco.ankidroidbridge"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
