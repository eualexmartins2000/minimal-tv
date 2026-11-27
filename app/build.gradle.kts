plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alex.minimaltv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alex.minimaltv"
        minSdk = 30          // Android 11 (TCL S5400A)
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.1.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Chave fixa: sem ela cada build teria assinatura diferente e a TV recusaria atualizar.
    signingConfigs {
        create("fixed") {
            storeFile = file("minimaltv.keystore")
            storePassword = "minimaltv"
            keyAlias = "minimaltv"
            keyPassword = "minimaltv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
