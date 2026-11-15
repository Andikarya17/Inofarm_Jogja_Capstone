plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}
// Records and measures the app's startup path. Emulator numbers are indicative only; see docs/context for the device used.
android {
    namespace = "com.inofarm.jogja.baselineprofile"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Lets the benchmark run on the project emulator; its numbers are indicative, never a device claim.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}
baselineProfile { useConnectedDevices = true }
dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
