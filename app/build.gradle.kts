plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.mdm_client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mdm_client"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    lint {
        disable += "QueryAllPackagesPermission"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    // AndroidX Core (لـ NotificationCompat)
// استخدم أحدث إصدار

    // WorkManager (إذا كنت تستخدمه لأعمال دورية)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
// HTTP client (OkHttp)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
// Google Play Services Location (إذا كنت بحاجة للموقع)
    implementation("com.google.android.gms:play-services-location:21.0.1")
// AndroidX Core (لبناء الإشعارات NotificationCompat وغيرها)
    implementation("androidx.core:core-ktx:1.13.1")
// AppCompat و Material Design (لواجهة المستخدم)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
// Kotlin Coroutines (إذا تستخدم الكوروتينز)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
// Firebase Cloud Messaging
    implementation("com.google.firebase:firebase-messaging:23.1.2")

    // Kotlin Coroutines - لإدارة المهام غير المتزامنة بسهولة
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Retrofit - (إذا احتجت) للتواصل مع أي APIs أخرى بسهولة
     implementation("com.squareup.retrofit2:retrofit:2.9.0")
     implementation("com.squareup.retrofit2:converter-gson:2.9.0")





    implementation(libs.firebase.storage)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}