plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.hambalapps.agymobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hambalapps.agymobile"
        minSdk = 26
        targetSdk = 28
        versionCode = 14
        versionName = "1.4.1"

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
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.ui.tooling)
}

val bridgeRoot = rootProject.projectDir.resolve("../bridge")
val syncBridgeAssets = tasks.register("syncBridgeAssets") {
    val bridgeDist = bridgeRoot.resolve("dist")
    val bridgePkg = bridgeRoot.resolve("package.json")
    val assetsBridgeDir = layout.projectDirectory.dir("src/main/assets/bridge").asFile

    onlyIf { bridgeDist.exists() && bridgePkg.exists() }
    doLast {
        assetsBridgeDir.mkdirs()
        bridgePkg.copyTo(assetsBridgeDir.resolve("package.json"), overwrite = true)
        val outDist = assetsBridgeDir.resolve("dist")
        outDist.deleteRecursively()
        bridgeDist.copyRecursively(outDist, overwrite = true)
    }
}

tasks.named("preBuild") {
    dependsOn(syncBridgeAssets)
}
