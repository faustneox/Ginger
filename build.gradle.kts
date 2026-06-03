// Top-level build file where you can add configuration options common to all sub-projects/modules.

buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // Provide Apache HttpClient for Crashlytics Gradle plugin runtime
        classpath("org.apache.httpcomponents:httpclient:4.5.13")
        classpath("org.codehaus.groovy:groovy:3.0.17")
        classpath("org.codehaus.groovy:groovy-xml:3.0.17")
    }
}

plugins {
  // Add the dependency for the Google services Gradle plugin
  id("com.google.gms.google-services") version "4.3.15" apply false
    // Kover code coverage plugin (applied in modules as needed)
    id("org.jetbrains.kotlinx.kover") version "0.9.8" apply false
  // Crashlytics Gradle plugin
  id("com.google.firebase.crashlytics") version "2.9.5" apply false
}

// Declare Firebase BOM for Android modules from root so app/library modules
// can inherit the BOM without adding it individually. We attach the BOM
// only when the Android plugin is applied to avoid applying to non-Android projects.
subprojects {
  plugins.withId("com.android.application") {
    dependencies.add("implementation", dependencies.enforcedPlatform("com.google.firebase:firebase-bom:34.14.0"))
    // Add explicit constraints for KTX artifacts that are not covered by the BOM
    dependencies.constraints.add("implementation", "com.google.firebase:firebase-firestore-ktx:25.1.4")
    dependencies.constraints.add("implementation", "com.google.firebase:firebase-storage-ktx:21.0.2")
  }
  plugins.withId("com.android.library") {
    dependencies.add("implementation", dependencies.enforcedPlatform("com.google.firebase:firebase-bom:34.14.0"))
    // Add explicit constraints for KTX artifacts that are not covered by the BOM
    dependencies.constraints.add("implementation", "com.google.firebase:firebase-firestore-ktx:25.1.4")
    dependencies.constraints.add("implementation", "com.google.firebase:firebase-storage-ktx:21.0.2")
  }
}