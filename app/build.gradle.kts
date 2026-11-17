import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release builds are signed only when ANDROID_KEYSTORE_PATH points at a
// keystore (the release workflow sets it from repo secrets). Local and CI
// debug builds don't need it.
val releaseKeystore = System.getenv("ANDROID_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

// GIPHY API key for GIF search: GIPHY_API_KEY in the environment (the release
// workflow sets it from a repo secret) or giphyApiKey in local.properties.
// Builds without one still work; the GIF tab says search isn't set up.
val giphyApiKey: String = System.getenv("GIPHY_API_KEY")?.takeIf { it.isNotBlank() }
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("giphyApiKey")
    }
    ?: ""

android {
    namespace = "io.github.mk3core.coresms"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.mk3core.coresms"
        minSdk = 29 // Android 10: RoleManager for the default SMS app role
        targetSdk = 37
        // Set by the release workflow: -PcoresmsVersionCode=N -PcoresmsVersionName=X.Y.Z
        versionCode = (findProperty("coresmsVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("coresmsVersionName") as String?) ?: "0.7.4"
        buildConfigField("String", "GIPHY_API_KEY", "\"${giphyApiKey.filter { it.isLetterOrDigit() }}\"")
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = releaseKeystore
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.coil.network)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
