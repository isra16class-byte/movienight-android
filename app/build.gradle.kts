import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// URL del servidor, fuera del código y del repo: se lee de `local.properties` (ignorado por git).
//   movienight.baseUrl       -> URL para debug y release (ej. https://sala.tu-dominio.uk)
//   movienight.baseUrl.debug -> opcional, pisa la anterior solo en debug (ej. http://10.0.2.2:3000)
// Ver `local.properties.example`. Sin configurar, queda un dominio `.invalid` a propósito: la app
// abre igual y muestra "no se pudo conectar" con la URL a la vista, en vez de apuntar a cualquier lado.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun localProp(name: String): String? =
    localProperties.getProperty(name)?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

val releaseBaseUrl = localProp("movienight.baseUrl") ?: "https://movienight.invalid"
val debugBaseUrl = localProp("movienight.baseUrl.debug") ?: releaseBaseUrl

android {
    namespace = "com.isra16.movienight"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.isra16.movienight"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Fase 1 (spike): HTTP + Socket.IO. socket.io-client 2.x es la serie compatible con servidores
    // Socket.IO 3.x/4.x (el backend usa 4.8.3). org.json ya viene con Android, se excluye el de la
    // librería para no duplicar clases. OkHttp se declara explícito (4.x) para usar su API Kotlin.
    implementation(libs.okhttp)
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}