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

    // A chave de assinatura NUNCA fica no repositório (regra do projeto, veja CLAUDE.md).
    // O build da nuvem recebe o arquivo e a senha pelos Secrets do GitHub, via variáveis de ambiente.
    val keystorePath: String? = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "minimaltv"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
