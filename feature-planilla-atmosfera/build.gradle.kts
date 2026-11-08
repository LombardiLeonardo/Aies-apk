plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24"
}

android {
    namespace = "com.aies.feature.planillaatmosfera"
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
    implementation(project(":core-pdf"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Base de datos local propia del módulo (registro de planilla de tapas por compartimento
    //), mismo criterio que feature-repuestos/feature-controles-visuales.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Supabase (solo postgrest-kt): a diferencia de Repuestos/Controles Visuales, este módulo
    // escribe directo 2 tablas remotas nuevas (planillas_atmosfera y su hija) que no tienen
    // wrapper/DAO en core-data. Reusa el cliente singleton ya autenticado
    // com.aies.coredata.remote.SupabaseClientProvider.client (de core-data) -- NO crea un
    // SupabaseClient propio como feature-inspeccion-glp. Mismo pin de versiones que
    // feature-inspeccion-glp/core-data (el resto de la doc completa está en esos build.gradle.kts):
    // BOM 2.6.0 porque 3.x+ requiere Kotlin 2.x, incompatible con el Kotlin 1.9.24 del repo.
    implementation(platform("io.github.jan-tennert.supabase:bom:2.6.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}

// Mismo workaround que feature-inspeccion-glp/core-data: io.ktor:ktor-serialization-kotlinx-
// json:2.3.12 fuerza (constraint "strictly") kotlinx-serialization-bom 1.7.3 más abajo en el
// árbol, que exige Kotlin 2.0+ y rompe la compatibilidad con el Kotlin 1.9.24 de todo el repo.
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
