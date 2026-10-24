import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val ownerApiUrl = providers.gradleProperty("inofarmApiUrl").orElse("").get()
require(ownerApiUrl.isBlank() || URI(ownerApiUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null }) {
    "inofarmApiUrl harus berupa URL HTTPS tanpa credential/query/fragment."
}
fun quotedConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
android {
    namespace = "com.inofarm.jogja"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.inofarm.jogja"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_API_BASE_URL", quotedConfig(ownerApiUrl))
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
            buildConfigField("String", "DEFAULT_API_BASE_URL", quotedConfig(providers.gradleProperty("inofarmDebugApiUrl").orElse("http://10.0.2.2:8000/api/v1/").get()))
            if(project.hasProperty("performanceTest")) applicationIdSuffix = ".performance"
        }
        create("profile") {
            initWith(getByName("release"))
            signingConfig = getByName("debug").signingConfig
            isDebuggable = false
            matchingFallbacks += listOf("release")
            proguardFiles("proguard-profile-rules.pro", "proguard-profile-test-abi.pro")
            if(project.hasProperty("performanceTest")) applicationIdSuffix = ".performance"
        }
    }
    testBuildType = if (project.hasProperty("profileUi")) "profile" else "debug"
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("dev.chrisbanes.haze:haze:1.6.10")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
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
