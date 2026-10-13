plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.aies.feature.inspeccionglp"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":core-docx"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Base de datos local propia del módulo (clientes/vehículos/checklist/informes/fotos),
    // calcada de la SQLite (sqlite-net) de la app MAUI original.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Supabase (Postgrest + Auth/GoTrue + Storage): único módulo con nube de toda la super
    // app, apunta al mismo proyecto Supabase ya en producción de la app MAUI original.
    // BOM 2.6.0 (no la 3.x más nueva): supabase-kt 3.x+ arrastra kotlin-stdlib/kotlinx-coroutines/
    // kotlinx-serialization/kotlinx-io compilados con Kotlin 2.x, incompatibles en binario con
    // el compilador Kotlin 1.9.24 fijado a nivel de todo el repo (error real de build, no
    // hipotético: "Module was compiled with an incompatible version of Kotlin"). 2.6.0 es la
    // última release de la serie 2.x (ago-2024, previa al salto a "Ktor 3"), construida contra
    // Kotlin/coroutines/serialization de esa misma época y confirmada compatible acá. El único
    // costo es el nombre del artifact de Auth: a esta versión el módulo todavía se llama
    // `gotrue-kt` (el paquete interno ya se llama `io.github.jan.supabase.gotrue`, y la clase
    // pública ya es `Auth`, no `GoTrue` — el rename de MÓDULO a `auth-kt` recién pasó en 3.0.0).
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Configuración/credenciales: DataStore para valores no sensibles (URL, host SMTP, etc.),
    // EncryptedSharedPreferences para secretos (anon key, contraseña SMTP) — equivalente al
    // par Preferences/SecureStorage del original.
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha07")

    // Envío de correo SMTP: puerto Android estándar de JavaMail (javax.mail normal no corre
    // en Android porque depende de javax.activation/AWT no disponibles en la plataforma).
    implementation("com.sun.mail:android-mail:1.6.8")
    implementation("com.sun.mail:android-activation:1.6.8")
}

// io.ktor:ktor-serialization-kotlinx-json:2.3.12 fuerza (constraint "strictly") kotlinx-
// serialization-bom 1.7.3 más abajo en el árbol, que exige Kotlin 2.0+ y rompe la
// compatibilidad con el Kotlin 1.9.24 de todo el repo. Se fuerzan de vuelta a 1.6.3 tanto los
// artifacts "comunes" (KMP metadata) como sus variantes -jvm, porque forzar solo uno dejaba
// al otro resolverse igual a 1.7.3 (son artifacts separados en el grafo de dependencias).
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.3",
            "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.6.3",
            "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3",
            "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3"
        )
    }
}
