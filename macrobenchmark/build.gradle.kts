plugins { alias(libs.plugins.android.test) }
android {
    namespace = "com.pedroaba.spix.macrobenchmark"
    compileSdk { version = release(37) }
    defaultConfig {
        minSdk = 29
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("performance") {
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "performance" } }
dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation(libs.androidx.junit)
    implementation(libs.test.runner)
}
