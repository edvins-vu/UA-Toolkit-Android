plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.ua.toolkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    compileOnly(files("libs/classes.jar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.adjust.sdk:adjust-android:5.0.0")
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
}