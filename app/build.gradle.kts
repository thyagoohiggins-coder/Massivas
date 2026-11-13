plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.massivas.fai"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.massivas.fai"
        minSdk = 26
        targetSdk = 35
        // O GitHub Actions define VERSION_CODE com o número da execução,
        // então cada build novo tem versão maior e instala por cima do anterior.
        versionCode = env("VERSION_CODE")?.toInt() ?: 1
        versionName = env("VERSION_NAME") ?: "1.0.0-dev"
        buildConfigField("String", "UPDATE_REPO", "\"thyagoohiggins-coder/Massivas\"")
    }

    // A chave vem dos Secrets do GitHub (KEYSTORE_BASE64 etc.) ou de keystore/massivas-fai.jks local
    // (fora do git). Mesma chave em todos os builds = atualização por cima da versão antiga.
    val keystoreFile = env("KEYSTORE_FILE")?.let { file(it) }
        ?: file("${rootDir}/keystore/massivas-fai.jks").takeIf { it.exists() && env("KEYSTORE_PASSWORD") != null }
    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = env("KEYSTORE_PASSWORD")
                keyAlias = env("KEY_ALIAS") ?: "massivasfai"
                keyPassword = env("KEY_PASSWORD") ?: env("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sem chave configurada, assina com a chave de debug (APK só para teste, sem Release).
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
