import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.util.Properties

val appProperties = Properties().apply {
    file("${rootProject.projectDir}/application.properties").inputStream().use { load(it) }
}

val keystorePropertiesFile = file("${rootProject.projectDir}/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val applicationVersionCode = appProperties.getProperty("versionCode").toInt()
var applicationVersionName = appProperties.getProperty("versionName")
if (appProperties.getProperty("isPreRelease").toBoolean()) {
    applicationVersionName += "-${appProperties.getProperty("preReleaseName")}.${appProperties.getProperty("preReleaseVer")}"
}
val gitSha: String? = System.getenv("GITHUB_SHA")
if (!gitSha.isNullOrEmpty()) {
    applicationVersionName += "+${gitSha.substring(0, 7)}"
}

android {
    buildToolsVersion = "36.0.0"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.rednote.lite"
        minSdk = 23
        targetSdk = 36
        versionCode = applicationVersionCode
        versionName = applicationVersionName
        vectorDrawables { useSupportLibrary = true }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        val keystoreFile = keystoreProperties.getProperty("keystore.file", "")
        if (keystoreFile.isNotBlank()) {
            create("config") {
                storeFile = file(File(rootDir, keystoreFile))
                storePassword = keystoreProperties.getProperty("keystore.password")
                keyAlias = keystoreProperties.getProperty("keystore.alias")
                keyPassword = keystoreProperties.getProperty("keystore.key.password")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        all {
            signingConfig =
                if (signingConfigs.any { it.name == "config" })
                    signingConfigs.getByName("config")
                else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
    }
    namespace = "com.rednote.lite"
    applicationVariants.configureEach {
        val variant = this
        outputs.configureEach {
            (this as BaseVariantOutputImpl).outputFileName =
                "rednote-${variant.buildType.name}-${applicationVersionName}(${applicationVersionCode}).apk"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp.core)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
}
