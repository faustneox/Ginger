plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    // kover подключается условно ниже, чтобы можно было отключать его в CI/локально
    alias(libs.plugins.hilt)
}

// Подключаем kover только если явно включён флаг проекта `enableKover=true`
if (project.hasProperty("enableKover") && project.property("enableKover") == "true") {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

// Apply Crashlytics plugin only when explicitly enabled for release builds.
// Use -PenableCrashlyticsPlugin=true in CI or release build jobs.
if (project.hasProperty("enableCrashlyticsPlugin") && project.property("enableCrashlyticsPlugin") == "true") {
    apply(plugin = "com.google.firebase.crashlytics")
    tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
        enabled = false
    }
}

ksp { arg("room.schemaLocation", projectDir.resolve("schemas").absolutePath) }

// Force Kotlin stdlib variants to match catalog version to avoid mixed stdlib versions
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:${libs.versions.kotlin.get()}",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${libs.versions.kotlin.get()}"
        )
    }
}

android {
    namespace = "com.ginger.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ginger.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        viewBinding = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    implementation(libs.material)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.room:room-paging:2.8.2")

    // Kotlin + Coroutines + Lifecycle (для MVVM)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-auth:21.1.0")
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")
    releaseImplementation("com.google.firebase:firebase-crashlytics-ktx:18.3.5")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.8")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.room:room-testing:2.8.2")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    implementation("androidx.paging:paging-runtime:3.3.2")
}

// Apply google-services plugin except when running connectedAndroidTest (workaround for instrumentation run)
val runningConnectedAndroidTest = gradle.startParameter.taskNames.any { it.contains("connectedAndroidTest") }
if (!runningConnectedAndroidTest) {
    apply(plugin = "com.google.gms.google-services")
} else {
    println("Skipping google-services plugin for connectedAndroidTest run")
}
