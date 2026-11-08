import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

fun loadProps(file: File) = Properties().apply { if (file.exists()) file.inputStream().use(::load) }

val versionProps = loadProps(rootProject.file("version.properties"))
// Signing credentials live outside of version control, see scripts/create-keystore.sh
val keystoreProps = loadProps(rootProject.file("keystore.properties"))

android {
    namespace = "de.hauptmann.sunshinealarm"
    compileSdk = 35

    defaultConfig {
        // NEVER change applicationId: a different id installs as a separate app
        // and the existing data (history, alarms) is not carried over.
        applicationId = "de.hauptmann.sunshinealarm"
        minSdk = 30
        targetSdk = 35
        versionCode = versionProps.getProperty("VERSION_CODE").toInt()
        versionName = versionProps.getProperty("VERSION_NAME")
    }

    signingConfigs {
        // One fixed key for every build. Android only installs an update over an
        // existing app if it is signed with the same key.
        create("stable") {
            storeFile = keystoreProps.getProperty("storeFile")?.let(::file)
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
        debug {
            // Same id and key as release, so debug and release builds can update each other
            signingConfig = signingConfigs.getByName("stable")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["test"].resources.srcDir(rootProject.file("samples"))
}

room {
    // Schemas are versioned in the repo; needed to write migrations that keep user data
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
}
