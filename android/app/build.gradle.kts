plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations

abstract class GitValueSource : ValueSource<String, GitValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val args: ListProperty<String>
    }
    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        val output = ByteArrayOutputStream()
        return try {
            val result = execOperations.exec {
                commandLine(parameters.args.get())
                standardOutput = output
                isIgnoreExitValue = true
            }
            if (result.exitValue == 0) {
                output.toString().trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

android {
    namespace = "com.vibe.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vibe.app"
        minSdk = 26
        targetSdk = 34

        val overrideCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
            ?: System.getenv("VERSION_CODE")?.toIntOrNull()
        val gitCommitCount = providers.of(GitValueSource::class) {
            parameters.args.set(listOf("git", "rev-list", "--count", "HEAD"))
        }.orNull?.toIntOrNull() ?: 1
        versionCode = overrideCode ?: maxOf(1, gitCommitCount)

        val overrideName = project.findProperty("versionName")?.toString()?.trim()
            ?: System.getenv("VERSION_NAME")?.trim()
        val gitTag = providers.of(GitValueSource::class) {
            parameters.args.set(listOf("git", "describe", "--tags", "--exact-match"))
        }.orNull ?: providers.of(GitValueSource::class) {
            parameters.args.set(listOf("git", "describe", "--tags", "--abbrev=0"))
        }.orNull
        versionName = if (!overrideName.isNullOrEmpty()) overrideName else (gitTag ?: "0.8.0")

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val spotifyClientId = project.findProperty("SPOTIFY_CLIENT_ID")?.toString()
            ?: System.getenv("SPOTIFY_CLIENT_ID")
            ?: localProperties.getProperty("SPOTIFY_CLIENT_ID", "")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystoreFile = project.findProperty("KEYSTORE_FILE")?.toString() ?: System.getenv("KEYSTORE_FILE")
            if (keystoreFile != null && file(keystoreFile).exists()) {
                storeFile = file(keystoreFile)
                storePassword = project.findProperty("KEYSTORE_PASSWORD")?.toString() ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = project.findProperty("KEY_ALIAS")?.toString() ?: System.getenv("KEY_ALIAS")
                keyPassword = project.findProperty("KEY_PASSWORD")?.toString() ?: System.getenv("KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
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
    implementation(project(":core:core-model"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-network"))
    implementation(project(":core:core-playback"))
    implementation(project(":core:core-connect"))
    implementation(project(":core:core-database"))
    implementation(project(":core:core-ui"))

    implementation(project(":feature:feature-home"))
    implementation(project(":feature:feature-search"))
    implementation(project(":feature:feature-library"))
    implementation(project(":feature:feature-playlist"))
    implementation(project(":feature:feature-artist"))
    implementation(project(":feature:feature-album"))
    implementation(project(":feature:feature-player"))
    implementation(project(":feature:feature-queue"))
    implementation(project(":feature:feature-lyrics"))
    implementation(project(":feature:feature-devices"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
