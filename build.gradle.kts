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