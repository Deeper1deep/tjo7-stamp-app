import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The ORIGINAL signing key (signing/tjo7.keystore, alias tjo7). Never regenerated.
val signingProps = Properties().apply {
    val f = rootProject.file("signing/tjo7.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.tjo7.stamp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tjo7.stamp"   // must never change: updates install over the old app
        minSdk = 23
        targetSdk = 34
        // Always above the previous Capacitor builds (build-N used GITHUB_RUN_NUMBER as versionCode)
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "build $versionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("tjo7") {
            storeFile = rootProject.file("signing/" + (signingProps.getProperty("storeFile") ?: "tjo7.keystore"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias") ?: "tjo7"
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("tjo7") }
        release {
            signingConfig = signingConfigs.getByName("tjo7")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        warningsAsErrors = false
        textReport = true
        disable += setOf("MissingTranslation", "ExtraTranslation", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("com.google.guava:guava:33.0.0-android")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
