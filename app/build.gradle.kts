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
    // Retrofit + OkHttp logging
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // FusedLocation cho GPS
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // WorkManager cho cảnh báo thời tiết chạy ngầm
    implementation("androidx.work:work-runtime:2.9.0")

    // Lottie cho icon động + MPAndroidChart cho biểu đồ nhiệt độ
    implementation("com.airbnb.android:lottie:6.1.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ViewModel + LiveData cho MVVM
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
}
