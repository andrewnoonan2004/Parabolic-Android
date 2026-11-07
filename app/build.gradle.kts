plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.play.publisher)
}
android {
    namespace = "org.parabolic.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.parabolic.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.4"
        setProperty("archivesBaseName", "Parabolic")
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        // Python/FFmpeg/aria2 are executables: they must exist in nativeLibraryDir.
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    signingConfigs {
        create("upload") {
            storeFile = file(System.getenv("ANDROID_UPLOAD_KEYSTORE_PATH") ?: "upload-key.jks")
            storePassword = System.getenv("ANDROID_UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            val uploadConfig = signingConfigs.getByName("upload")
            if (uploadConfig.storeFile?.exists() == true && !uploadConfig.storePassword.isNullOrBlank()) {
                signingConfig = uploadConfig
            }
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint { abortOnError = true }
}
play {
    serviceAccountCredentials.set(file(System.getenv("ANDROID_PLAY_CREDENTIALS_PATH") ?: "play-store-keys.json"))
    track.set("internal")
}
kotlin { jvmToolchain(17) }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.viewmodel.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation.suite)
    implementation(libs.adaptive)
    implementation(libs.adaptive.layout)
    implementation(libs.adaptive.navigation)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.documentfile)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.ytdlp)
    implementation(libs.ffmpeg)
    implementation(libs.aria2c)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
