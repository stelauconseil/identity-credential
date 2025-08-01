import org.gradle.kotlin.dsl.credentials

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    id("kotlin-android")
    alias(libs.plugins.navigation.safe.args)
    alias(libs.plugins.parcelable)
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

kotlin {
    jvmToolchain(17)

    compilerOptions {
        allWarningsAsErrors = true
    }
}

android {
    namespace = "com.android.mdl.appreader"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.android.mdl.appreader"
        minSdk = 29
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        dataBinding = true
        viewBinding = true
        compose = true
    }

    lint {
        baseline = file("lint-baseline.xml")
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
            excludes += listOf("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":multipaz"))
    implementation(project(":multipaz-android-legacy"))
    implementation(project(":multipaz-doctypes"))
    implementation(project(":jpeg2k"))

    implementation(libs.kotlinx.datetime)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.material)

    debugImplementation(compose.uiTooling)
    implementation(compose.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometrics)
    implementation(compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.code.scanner)
    implementation(libs.androidx.material)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    implementation(libs.kotlinx.io.core)
    implementation(libs.cbor)

    implementation(libs.play.services.identity.credentials)

    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)

    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Stelau
    implementation(files("libs/cev-2.1.7.jar"))
    implementation("io.github.ehn-digital-green-development:base45:0.0.3")
    implementation("org.msgpack:msgpack-core:0.9.9")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor:2.19.1")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
