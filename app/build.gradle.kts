plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tingxia.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tingxia.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "0.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Fixed keystore for debug/CI only (package ends with .debug).
        create("ciDebug") {
            val ks = rootProject.file("keystore/tingxia-debug.jks")
            storeFile = ks
            storePassword = "tingxia-debug"
            keyAlias = "tingxia-debug"
            keyPassword = "tingxia-debug"
        }
        // Optional release signing from env / GitHub Secrets (never use debug key).
        val releaseStorePath = System.getenv("TINGXIA_RELEASE_STORE_FILE")
        if (!releaseStorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = System.getenv("TINGXIA_RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("TINGXIA_RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("TINGXIA_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // No debug-key fallback: unsigned if secrets missing.
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("ciDebug")
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

    lint {
        abortOnError = true
        warningsAsErrors = false
        // Dependabot owns dependency freshness; keep Lint focused on app defects.
        disable += setOf("MissingTranslation", "GradleDependency", "AndroidGradlePluginVersion")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("androidTest") {
            // Room MigrationTestHelper loads schemas from assets.
            assets.srcDirs("$projectDir/schemas")
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    implementation(libs.coroutines.android)
    implementation(libs.coil.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
}
