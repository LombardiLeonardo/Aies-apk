plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aies.feature.sesion"
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
    // A propósito: solo core-data (lee/escribe SesionServicio/Unidad/TipoServicio) y core-ui.
    // Sin Room propio ni ksp — este módulo es puramente un consumidor del esquema de core-data
    // (mismo patrón que feature-nota-trabajo).
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    // Fase 3 (módulos de servicio): feature-sesion navega directo a la pantalla real de cada
    // módulo (evita duplicar el NavHost de :app, que todavía no monta feature-sesion como
    // destino). Se van sumando de a uno por fase.
    implementation(project(":feature-calibrado"))
    implementation(project(":feature-repuestos"))
    implementation(project(":feature-planilla-tapas"))
    implementation(project(":feature-planilla-atmosfera"))
    implementation(project(":feature-controles-visuales"))
    implementation(project(":feature-certificados"))
    implementation(project(":feature-informes"))
    implementation(project(":feature-inspecciones"))
    // Fase 4: accesos secundarios desde la pantalla de inicio (historial de notas de trabajo,
    // Ejes y GLP quedan fuera del flujo de visita pero necesitan seguir siendo alcanzables
    // ahora que :app ya no tiene bottom nav).
    implementation(project(":feature-nota-trabajo"))
    implementation(project(":feature-inspeccion-ejes"))
    implementation(project(":feature-inspeccion-glp"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
