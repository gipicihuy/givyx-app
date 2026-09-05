plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.givy.downloader"
    compileSdk = 34

    // Fixed debug keystore committed at app/debug.keystore. Without this,
    // Android Gradle Plugin falls back to an auto-generated debug key —
    // and on GitHub Actions that key is regenerated fresh on every runner,
    // so every "latest" build ends up signed differently. Installing a new
    // build over an old one then fails with "package conflicts with an
    // existing package" because the signatures don't match. Pinning one
    // keystore here keeps every build (CI and local) signed identically,
    // so in-app updates install cleanly over the previous version.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.givy.downloader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Passed by CI as -PgitSha=<commit sha> so the running app knows which
        // commit it was built from; the in-app update checker compares this
        // against the commit noted in the latest GitHub Release to decide if
        // an update is available. Defaults to "local" for local builds.
        buildConfigField(
            "String",
            "GIT_COMMIT",
            "\"${(project.findProperty("gitSha") as String?) ?: "local"}\""
        )
        buildConfigField("String", "GITHUB_REPO", "\"gipicihuy/givyx-app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Provides the XML Theme.Material3.* styles used by themes.xml (the window
    // background before Compose takes over). Compose Material3 alone does not
    // ship these XML resources.
    implementation("com.google.android.material:material:1.12.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Networking (used by the downloader to pull the file from the URL the scraper returns)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // HTML parsing for the TikTok scraper (Jsoup ~= cheerio)
    implementation("org.jsoup:jsoup:1.17.2")

    // Async image loading for the video thumbnail preview
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
