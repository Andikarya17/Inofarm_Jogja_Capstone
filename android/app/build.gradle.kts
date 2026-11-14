plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}
fun quotedConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    sourceSets.getByName("androidTest").assets.srcDir("../../contracts/v2/fixtures")
    namespace = "com.inofarm.jogja"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.inofarm.jogja"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FINANCE_API_BASE_URL", quotedConfig(providers.gradleProperty("inofarmFinanceApiUrl").orElse("").get()))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    // Local profiling only: release behavior, locally signed, never distributed as a production release.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            testProguardFiles("proguard-test-rules.pro")
        }
        getByName("debug") {
            applicationIdSuffix = ".finance"
            buildConfigField("String", "FINANCE_API_BASE_URL", quotedConfig(providers.gradleProperty("inofarmFinanceDebugApiUrl").orElse("http://10.0.2.2:8000/api/v2/").get()))
            if(project.hasProperty("performanceTest")) applicationIdSuffix = ".finance.performance"
        }
        create("profile") {
            initWith(getByName("release"))
            signingConfig = getByName("debug").signingConfig
            isDebuggable = false
            matchingFallbacks += listOf("release")
            proguardFiles("proguard-profile-rules.pro", "proguard-profile-test-abi.pro")
            applicationIdSuffix = ".finance"
            if(project.hasProperty("performanceTest")) applicationIdSuffix = ".finance.performance"
        }
    }
    testBuildType = if (project.hasProperty("profileUi")) "profile" else "debug"
}
// One profile for every build type, written to src/main/generated/baselineProfiles by :app:generateBaselineProfile.
baselineProfile { mergeIntoMain = true }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Installs the app's Baseline Profile on devices that sideload the APK instead of getting it from Play.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    "baselineProfile"(project(":baselineprofile"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.08.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
