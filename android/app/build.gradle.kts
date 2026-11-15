plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
fun quotedConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
// Release signing comes from ~/.gradle/gradle.properties or the environment, never from the repository.
fun releaseSecret(name: String) = providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull?.takeIf { it.isNotBlank() }
val releaseStoreFile = releaseSecret("INOFARM_RELEASE_STORE_FILE")
val releaseApiUrl = providers.gradleProperty("inofarmFinanceApiUrl").orElse("")
android {
    sourceSets.getByName("androidTest").assets.srcDir("../../contracts/v2/fixtures")
    namespace = "com.inofarm.jogja"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "com.inofarm.jogja"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FINANCE_API_BASE_URL", quotedConfig(providers.gradleProperty("inofarmFinanceApiUrl").orElse("").get()))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    signingConfigs {
        if (releaseStoreFile != null) create("release") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseSecret("INOFARM_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSecret("INOFARM_RELEASE_KEY_ALIAS")
            keyPassword = releaseSecret("INOFARM_RELEASE_KEY_PASSWORD")
        }
    }
    // Local profiling only: release behavior, locally signed, never distributed as a production release.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            testProguardFiles("proguard-test-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
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
// A release APK talks to the pilot server only over HTTPS; refuse to build one that would ship without a usable URL.
val checkReleaseConfig = tasks.register("checkReleaseConfig") {
    val url = releaseApiUrl
    val signed = releaseStoreFile != null
    doLast {
        val value = url.get()
        require(value.startsWith("https://") && value.endsWith("/api/v2/")) {
            "Release needs -PinofarmFinanceApiUrl=https://<server>/api/v2/ (got '" + value + "')"
        }
        if (!signed) logger.warn("Release APK is unsigned: set INOFARM_RELEASE_STORE_FILE, INOFARM_RELEASE_STORE_PASSWORD, INOFARM_RELEASE_KEY_ALIAS and INOFARM_RELEASE_KEY_PASSWORD outside the repository.")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseConfig) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
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
