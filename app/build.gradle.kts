plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.streakguard.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.streakguard.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "0.4.4"
        testInstrumentationRunner = "com.streakguard.app.OptimizedSmokeRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Isolated, debug-signed install using the same R8/resource rules as release.
        // Never publish this variant; it exists to exercise optimized code on a device.
        create("optimizedTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            proguardFiles("optimized-test-rules.pro")
        }
    }
    testBuildType = if (providers.gradleProperty("optimizedSmoke").isPresent) "optimizedTest" else "debug"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    testImplementation("junit:junit:4.13.2")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-tooling-preview")
}

// Keep symbols tied to the exact release, alongside the APK/AAB in CI artifacts.
// AGP also embeds the R8 mapping in an App Bundle for Play crash deobfuscation.
val archiveReleaseSymbols by tasks.registering(Zip::class) {
    dependsOn("minifyReleaseWithR8")
    from(layout.buildDirectory.dir("outputs/mapping/release")) {
        include("*.txt", "*.prt")
    }
    archiveFileName.set("streakguard-${android.defaultConfig.versionName}-${android.defaultConfig.versionCode}-symbols.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/release-symbols"))
}
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    finalizedBy(archiveReleaseSymbols)
}
