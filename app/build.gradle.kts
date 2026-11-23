plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val configuredVersionName = providers.gradleProperty("appVersionName").orElse("1.0.0").get()
val configuredVersionCode = providers.gradleProperty("appVersionCode").orElse("1").get().toInt()

android {
    namespace = "app.dayframe.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.dayframe.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = configuredVersionCode
        versionName = configuredVersionName
    }

    buildTypes {
        debug {
            // Debug and release variants share this package identity for a smooth upgrade path.
        }
        release {
            // CI restores this repository's private distribution signing identity.
            // Never publish a release if that identity is missing or has been replaced.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
