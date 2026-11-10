import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.medtech.reviewhub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medtech.reviewhub"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Release signing is configured through environment variables on CI
            // (see android/README.md). Keys are never committed to the repository.
            val keystoreBase64 = System.getenv("SIGNING_KEYSTORE_BASE64")
            if (!keystoreBase64.isNullOrBlank()) {
                val keystoreFile = File(layout.buildDirectory.get().asFile, "release.keystore")
                keystoreFile.parentFile.mkdirs()
                keystoreFile.writeBytes(
                    Base64.getDecoder().decode(keystoreBase64)
                )
                signingConfigs.create("release") {
                    storeFile = keystoreFile
                    storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                }
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Same applicationId as release so the debug APK is
            // com.medtech.reviewhub (per spec).
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// ---------------------------------------------------------------------------
// copyContentAssets: copy shared/content/ JSON into the app assets so the
// website, backend and Android app all consume the same content files.
// If shared/content/ is missing (or has no manifest.json) the small embedded
// sample content already committed under src/main/assets/content/ is used and
// the build still succeeds. Wired into preBuild so every assemble* run is
// up to date.
// ---------------------------------------------------------------------------
val sharedContentDir = rootProject.file("../shared/content")
val fallbackAssetsContentDir = file("src/main/assets/content")

val copyContentAssets = tasks.register<Copy>("copyContentAssets") {
    group = "build"
    description = "Copies shared/content JSON into app assets (falls back to embedded sample)."
    from(sharedContentDir)
    into(fallbackAssetsContentDir)
    includeEmptyDirs = false
    // Only overwrite the embedded sample when real shared content exists.
    onlyIf {
        val subjects = File(sharedContentDir, "subjects")
        subjects.isDirectory && subjects.list()?.isNotEmpty() == true
    }
}

tasks.named("preBuild") {
    dependsOn(copyContentAssets)
}

// ---------------------------------------------------------------------------
// copyMusicAssets: copy shared/music/catalog.json into app assets so the
// Android player consumes the same generative-audio catalog as the web app
// (shared/MUSIC.md). If shared/music/ is missing the tiny embedded fallback
// catalog committed under src/main/assets/music/ is used and the build still
// succeeds. Wired into preBuild like copyContentAssets.
// ---------------------------------------------------------------------------
val sharedMusicDir = rootProject.file("../shared/music")
val fallbackAssetsMusicDir = file("src/main/assets/music")

val copyMusicAssets = tasks.register<Copy>("copyMusicAssets") {
    group = "build"
    description = "Copies shared/music/ into app assets (falls back to embedded catalog)."
    from(sharedMusicDir)
    into(fallbackAssetsMusicDir)
    includeEmptyDirs = false
    onlyIf { File(sharedMusicDir, "catalog.json").isFile }
}

tasks.named("preBuild") {
    dependsOn(copyMusicAssets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    // NOTE (intentional deviation, documented in README): the spec pins
    // activity-compose 1.13.0 and lifecycle 2.11.0, but those AARs declare
    // minCompileSdk 36 + minAgpVersion 8.9.1, which conflicts with the pinned
    // toolchain (AGP 8.7.3, compileSdk 35 — android-36 is not installed).
    // We keep the pinned toolchain and use the newest AndroidX releases whose
    // AAR metadata accepts compileSdk 35 / AGP 8.7.3. APIs used are identical.
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")
    // lifecycle 2.11.0 requires compileSdk 36 + AGP 8.9.1 (see note above);
    // 2.8.7 is the newest line compatible with the pinned toolchain.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.room:room-runtime:2.7.0")
    implementation("androidx.room:room-ktx:2.7.0")
    ksp("androidx.room:room-compiler:2.7.0")

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.1")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Study Music playback (Media3). Version pinned to 1.5.1: the newest stable
    // Media3 line whose AARs accept compileSdk 35 / AGP 8.7.3. Media3 1.6+/1.7+
    // move to compileSdk 36 + newer AGP, which conflicts with the pinned
    // toolchain (same documented trade-off as activity-compose/lifecycle above).
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
