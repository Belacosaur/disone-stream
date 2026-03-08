import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    kotlin("kapt")
}

android {
    namespace = "com.disone"
    compileSdk = 34

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val dappStoreKeystoreFile = rootProject.file("keystore.dappstore.properties")
    if (keystorePropertiesFile.exists()) {
        val keystoreProperties = Properties()
        keystoreProperties.load(keystorePropertiesFile.inputStream())
        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }
    if (dappStoreKeystoreFile.exists()) {
        val props = Properties()
        props.load(dappStoreKeystoreFile.inputStream())
        signingConfigs {
            create("dappStore") {
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
                storeFile = rootProject.file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
            }
        }
    }

    flavorDimensions += "store"
    productFlavors {
        create("playStore") {
            dimension = "store"
        }
        create("dappStore") {
            dimension = "store"
        }
    }

    defaultConfig {
        applicationId = "com.disone.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 7
        versionName = "4.0"
        // Override for custom domain if Railway's *.up.railway.app has DNS issues on some networks
        buildConfigField("String", "API_BASE_URL", "\"https://disone-api.up.railway.app/\"")
    }

    buildTypes {
        release {
            signingConfig = null // Set per-variant in androidComponents
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants { variant ->
        val ext = extensions.getByType(com.android.build.gradle.AppExtension::class.java)
        val configs = ext.signingConfigs
        val config = when {
            variant.name.contains("dappStore") && configs.findByName("dappStore") != null ->
                configs.getByName("dappStore")
            rootProject.file("keystore.properties").exists() -> configs.getByName("release")
            else -> null
        }
        config?.let { variant.signingConfig?.setConfig(it) }
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.50")
    kapt("com.google.dagger:hilt-compiler:2.50")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.hilt:hilt-work:1.0.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0") // Fixes PendingIntent FLAG_IMMUTABLE on Android 12+

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // jlibtorrent removed - TorrentSession stubbed; use stream-server for hosted modes

    // libVLC
    implementation("org.videolan.android:libvlc-all:3.4.4")

    // ExoPlayer (HLS)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")

    // Solana Mobile Wallet Adapter
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.0.3")
    implementation("com.solanamobile:web3-solana:0.2.5")
    implementation("com.solanamobile:rpc-core:0.2.7")
    implementation("io.github.funkatronics:multimult:0.2.3")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Accompanist
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.34.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

tasks.register("copyDappStoreApkForPublishing") {
    dependsOn("assembleDappStoreRelease")
    doLast {
        val apk = file("build/outputs/apk/dappStore/release/app-dappStore-release.apk")
        val dest = file("${rootProject.projectDir}/dapp-store-publishing/files/app-dappStore-release.apk")
        if (apk.exists()) {
            dest.parentFile.mkdirs()
            apk.copyTo(dest, overwrite = true)
            println("Copied dApp Store APK to ${dest.path}")
        }
    }
}
