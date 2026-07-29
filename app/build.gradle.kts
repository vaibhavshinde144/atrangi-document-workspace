import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val atrangiLogoBase64 = rootProject.file("docs/atrangi-brand-logo.b64")
val atrangiLogoPng = file("src/main/res/drawable-nodpi/atrangi_riders_launcher.png")
if (atrangiLogoBase64.exists()) {
    val raw = atrangiLogoBase64.readText().trim().substringAfter(',', atrangiLogoBase64.readText().trim())
    val decoded = Base64.getMimeDecoder().decode(raw)
    require(decoded.size > 1024 && decoded.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
        "Atrangi launcher logo must decode to a valid PNG"
    }
    atrangiLogoPng.parentFile.mkdirs()
    atrangiLogoPng.writeBytes(decoded)
}

android {
    namespace = "com.atrangi.documentworkspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.atrangi.documentworkspace"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "7.2.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
}
