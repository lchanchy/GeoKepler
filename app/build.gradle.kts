plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace  = "com.act.geomapper"
    compileSdk = 35

    defaultConfig {
        applicationId         = "com.act.geomapper"
        minSdk                = 26
        targetSdk             = 35
        versionCode           = 1
        versionName           = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // ── Compose BOM (gestiona versiones de todas las libs Compose) ──────────
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // ── Lifecycle ────────────────────────────────────────────────────────────
    // lifecycle-runtime-compose expone collectAsStateWithLifecycle()
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    // ── Activity / Navigation ────────────────────────────────────────────────
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // ── Core + Splash ────────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.material)

    // ── DataStore ────────────────────────────────────────────────────────────
    implementation(libs.datastore.preferences)

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.coroutines.android)

    // ── Mapa / Geometría ─────────────────────────────────────────────────────
    implementation(libs.osmdroid)
    implementation(libs.slf4j.android)
    implementation(libs.jts.core)

    // ── Imagen (logo splash) ─────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Debug ────────────────────────────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // ── Test ─────────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
}
