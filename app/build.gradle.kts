import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

// Release signing reads from keystore.properties at the repo root (gitignored). Absent on
// machines without the key, in which case release builds stay unsigned.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}

android {
    namespace = "com.kaizenll.xpendiq"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.kaizenll.xpendiq"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when the keystore is present (so CI/contributors without it still build).
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Two distribution variants:
    //  - full: reads bank SMS (RECEIVE_SMS/READ_SMS + receiver). For off-Play distribution.
    //  - play: notification-listener only, no SMS permissions. Safe for the Play SMS policy.
    // SMS permissions and the SMS receiver live in src/full/AndroidManifest.xml; shared code
    // branches on BuildConfig.SMS_ENABLED.
    flavorDimensions += "dist"
    productFlavors {
        create("full") {
            dimension = "dist"
            buildConfigField("boolean", "SMS_ENABLED", "true")
            // Off-Play build: no Play Billing (can't take payment when sideloaded), so it's free.
            buildConfigField("boolean", "BILLING_ENABLED", "false")
            // Distinguish the two builds in the About card / issue reports.
            versionNameSuffix = "-full"
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "SMS_ENABLED", "false")
            // Play Store build: real Play Billing behind the entitlement seam.
            buildConfigField("boolean", "BILLING_ENABLED", "true")
            // No suffix: Play users see a clean version (e.g. "1.0").
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.biometric)

    // Play Billing lives only in the play flavor — full stays free and can't take payment.
    "playImplementation"(libs.billing.ktx)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
