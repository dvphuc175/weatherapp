import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

val openWeatherApiKey: String =
    localProperties.getProperty("OPENWEATHER_API_KEY")
        ?: System.getenv("OPENWEATHER_API_KEY")
        ?: ""

android {
    namespace = "com.example.weather_application"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.weather_application"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$openWeatherApiKey\"")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    // 1. Retrofit & Gson: Dùng để gọi API thời tiết và dịch dữ liệu JSON
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // 2. Location: Dùng để lấy tọa độ hiện tại của máy ảo/điện thoại
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // 3. Glide: Dùng để tải mượt mà các icon thời tiết (như mây, mưa, nắng) từ mạng
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 4. WorkManager: Cực kỳ quan trọng để làm tính năng chạy ngầm cảnh báo trước 15 phút
    implementation("androidx.work:work-runtime:2.9.0")
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}
