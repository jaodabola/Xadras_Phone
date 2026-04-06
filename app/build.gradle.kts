plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.navigation.safeargs)
}

android {
    namespace = "com.xadras.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xadras.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // URL base do backend — alterar para o IP/domínio real em produção
        buildConfigField("String", "BASE_URL", "\"http://192.168.1.18:8000/api/\"")
        buildConfigField("String", "WS_BASE_URL", "\"ws://192.168.1.18:8000/ws/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Evitar compressão do modelo TFLite pelo AAPT
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Navegação
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // UI Extras
    implementation(libs.androidx.swiperefreshlayout)

    // Lifecycle (ViewModel + Coroutines)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // DataStore (armazenamento local de tokens)
    implementation(libs.androidx.datastore.preferences)

    // Hilt (injeção de dependências)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Retrofit + OkHttp (comunicação REST com o backend)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // CameraX (captura de vídeo do tabuleiro)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // TensorFlow Lite (deteção de tabuleiro)
    implementation(libs.tflite.runtime)
    implementation(libs.tflite.support)
    implementation(libs.tflite.gpu)

    // OpenCV (Estabilização extrema Xadras_Vision)
    implementation(libs.opencv.android)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Chess library (validação de movimentos e FEN)
    implementation(libs.chesslib)

    // Testes
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}