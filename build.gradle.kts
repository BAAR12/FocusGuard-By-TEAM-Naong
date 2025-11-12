// Top-level build file where you can add configuration options common to all sub-projects/modules.

// The 'allprojects' block was removed from here to fix the build error.
// All repository definitions are now in 'settings.gradle.kts'.

plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}

