plugins {
    alias(libs.plugins.android.application)
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

extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java).apply {
    namespace = "com.ginger.android"
    compileSdk = 36

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
        buildConfig = true
    }

    // Kotlin compiler options are configured below via reflection to support
    // AGP's built-in Kotlin (when `android.builtInKotlin=true`). This avoids
    // referencing Kotlin Gradle API types directly in the script.
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    implementation(libs.material)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation("androidx.room:room-paging:2.8.4")

    // Kotlin + Coroutines + Lifecycle (для MVVM)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-auth:21.1.0")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    releaseImplementation("com.google.firebase:firebase-crashlytics-ktx:18.3.5")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-compiler:2.59.2")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.3")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.10.0")
    implementation("com.squareup.retrofit2:converter-gson:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.paging:paging-runtime:3.5.0")
    implementation(libs.photoview)
}

// Apply google-services plugin only when explicitly enabled to avoid
// unexpected failures from plugins that rely on the legacy Variant API.
val runningConnectedAndroidTest = gradle.startParameter.taskNames.any { it.contains("connectedAndroidTest") }
// Read the diagnostic flag robustly (handles boolean or string values passed via -P)
val isAgpObsoleteDiagnostic = project.findProperty("android.debug.obsoleteApi")?.toString()?.toBoolean() ?: false
// Only apply the google-services plugin when the project property `enableGoogleServices` is set to true
// and we are not running connectedAndroidTest nor the AGP obsolete-API diagnostic.
val enableGoogleServices = project.findProperty("enableGoogleServices")?.toString()?.toBoolean() ?: false
if (enableGoogleServices && !runningConnectedAndroidTest && !isAgpObsoleteDiagnostic) {
    try {
        apply(plugin = "com.google.gms.google-services")
    } catch (e: Throwable) {
        println("Warning: Failed to apply com.google.gms.google-services plugin: " + e.message)
        // Continue without failing the build; plugin may be incompatible with current AGP/Kotlin.
    }
} else if (!enableGoogleServices) {
    println("Google services plugin disabled (set -PenableGoogleServices=true to enable)")
} else if (runningConnectedAndroidTest) {
    println("Skipping google-services plugin for connectedAndroidTest run")
} else {
    println("Skipping google-services plugin for AGP obsolete API diagnostic (android.debug.obsoleteApi=true)")
}

// Configure Kotlin `jvmTarget` reflectively so the script does not depend on
// the Kotlin Gradle plugin classes at configuration time. This works when
// AGP provides Kotlin tooling via `android.builtInKotlin=true`.
tasks.configureEach {
    try {
        val className = this.javaClass.name
        if (className == "org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile" || className.contains("Kotlin")) {
            val kotlinOptionsMethod = this.javaClass.getMethod("getKotlinOptions")
            val kotlinOptions = kotlinOptionsMethod.invoke(this)
            kotlinOptions.javaClass.getMethod("setJvmTarget", String::class.java).invoke(kotlinOptions, "11")
        }
    } catch (_: Throwable) {
        // ignore - task may not expose kotlinOptions or reflection may fail
    }
}
