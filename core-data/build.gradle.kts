import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

// Fase C del pase a base de datos compartida: SUPABASE_URL/SUPABASE_ANON_KEY viven en
// local.properties (no versionado, ver .gitignore) y se exponen acá como BuildConfig porque
// SupabaseClientProvider vive en ESTE módulo (core-data), que no depende de :app y por lo tanto
// no puede ver el BuildConfig de :app -- el grafo de dependencias solo corre en un sentido.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(FileInputStream(file))
}
val supabaseUrl: String = localProperties.getProperty("SUPABASE_URL") ?: ""
val supabaseAnonKey: String = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""
// Login automático (usuario de servicio único, no por-técnico) para que toda la app quede
// autenticada contra Supabase Auth sin pantalla de login propia -- ver SupabaseClientProvider.
val supabaseServiceEmail: String = localProperties.getProperty("SUPABASE_SERVICE_EMAIL") ?: ""
val supabaseServicePassword: String = localProperties.getProperty("SUPABASE_SERVICE_PASSWORD") ?: ""

android {
    namespace = "com.aies.coredata"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_SERVICE_EMAIL", "\"$supabaseServiceEmail\"")
        buildConfigField("String", "SUPABASE_SERVICE_PASSWORD", "\"$supabaseServicePassword\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val roomVersion = "2.6.1"
    // api, no implementation: AppDatabase/los DAOs exponen tipos de Room (RoomDatabase,
    // Flow-returning queries) en su superficie pública, así que cualquier módulo que
    // dependa de core-data necesita Room resoluble en su propio classpath de compilación.
    api("androidx.room:room-runtime:$roomVersion")
    api("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Supabase (Fase C: solo conexión/validación, todavía no reemplaza ninguna lectura/escritura
    // real -- eso es Fase D). Mismo pin de versiones que feature-inspeccion-glp (el único otro
    // consumidor de supabase-kt en el repo, ver ese build.gradle.kts para el porqué completo):
    // BOM 2.6.0 porque 3.x+ requiere Kotlin 2.x, incompatible con el Kotlin 1.9.24 fijado a nivel
    // de todo el repo.
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    // gotrue-kt (Auth): login automático con el usuario de servicio, ver SupabaseClientProvider.
    // Mismo nombre de artifact "viejo" que ya documenta feature-inspeccion-glp/build.gradle.kts
    // para esta versión 2.6.0 (el rename a auth-kt fue recién en 3.0.0).
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Tests de regresión de seguridad (docs/security/AUDITORIA-*.md) — escanean el código fuente
    // y local.properties en busca de secretos con rol service_role, sin necesitar Robolectric.
    testImplementation("junit:junit:4.13.2")
}

// Mismo workaround que feature-inspeccion-glp: io.ktor:ktor-serialization-kotlinx-json:2.3.12
// fuerza (constraint "strictly") kotlinx-serialization-bom 1.7.3 más abajo en el árbol, que
// exige Kotlin 2.0+ y rompe la compatibilidad con el Kotlin 1.9.24 de todo el repo.
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
