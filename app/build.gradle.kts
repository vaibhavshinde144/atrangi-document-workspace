plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val atrangiKeystorePath = providers.environmentVariable("ATRANGI_KEYSTORE_PATH").orNull
val atrangiKeystorePassword = providers.environmentVariable("ATRANGI_KEYSTORE_PASSWORD").orNull
val atrangiKeyAlias = providers.environmentVariable("ATRANGI_KEY_ALIAS").orNull
val atrangiKeyPassword = providers.environmentVariable("ATRANGI_KEY_PASSWORD").orNull
val atrangiSigningValues = listOf(
    atrangiKeystorePath,
    atrangiKeystorePassword,
    atrangiKeyAlias,
    atrangiKeyPassword
)
val hasAtrangiSigning = atrangiSigningValues.all { !it.isNullOrBlank() }

if (atrangiSigningValues.any { !it.isNullOrBlank() } && !hasAtrangiSigning) {
    throw GradleException("Atrangi release signing configuration is incomplete")
}

android {
    namespace = "com.atrangi.documentworkspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atrangi.documentworkspace"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "7.2.4"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasAtrangiSigning) {
            create("atrangiRelease") {
                storeFile = file(atrangiKeystorePath!!)
                storePassword = atrangiKeystorePassword
                keyAlias = atrangiKeyAlias
                keyPassword = atrangiKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasAtrangiSigning) {
                signingConfig = signingConfigs.getByName("atrangiRelease")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
