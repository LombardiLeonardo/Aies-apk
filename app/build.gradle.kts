import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// SEC-006 (docs/security/AUDITORIA-2026-09-19.md): keystore.properties vive en keystore/, NO
// versionado (ver .gitignore) -- mismo patrón que local.properties en core-data/build.gradle.kts.
// Si el archivo no existe (ej. un checkout nuevo que todavía no generó su propio keystore), el
// build type "release"/"liviano" simplemente no tiene signingConfig -- Gradle los deja sin firmar
// en vez de romper el build entero, para no bloquear `debug` en un checkout sin ese archivo.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore/keystore.properties")
    if (file.exists()) load(FileInputStream(file))
}
val tieneKeystoreDeRelease = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.aies.superapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aies.superapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (tieneKeystoreDeRelease) {
            create("release") {
                storeFile = rootProject.file("keystore/${keystoreProperties.getProperty("storeFile")}")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    // Variante temporal solo para achicar el APK y poder mandarlo por chat (límite de 30 MB).
    // SEC-006: antes se firmaba con la key de debug (regenerable por cualquiera que tenga el
    // repo, sin garantía de integridad de quién generó el APK); ahora usa el keystore de release
    // real si está disponible, y cae a debug solo si no lo está (checkout nuevo sin
    // keystore/keystore.properties todavía generado). minifyEnabled acá es nuevo en el repo: si
    // algo se rompe en runtime que no se rompía antes (sospechar primero de Apache POI,
    // reflection-heavy), es la primera variable a revisar -- no es la variante que se use para
    // desarrollo día a día.
    buildTypes {
        create("liviano") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            matchingFallbacks += "debug"
            signingConfig = if (tieneKeystoreDeRelease) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        getByName("release") {
            if (tieneKeystoreDeRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // android-mail y android-activation (feature-inspeccion-glp, envío de correo SMTP) empaquetan
    // los mismos archivos de licencia en META-INF; ninguno de los dos se necesita en el APK final.
    packaging {
        resources {
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(project(":core-data"))
    implementation(project(":core-ui"))
    implementation(project(":core-pdf"))
    // Fase 4: :app ya no navega directo a Inspecciones/Certificados/Informes/Controles
    // Visuales/Calibrado/Ejes/GLP — feature-sesion es el único destino, y renderiza esas
    // pantallas puertas adentro de su propio back-stack (ver feature-sesion/build.gradle.kts).
    // Sus Activities/recursos igual quedan en el APK final por dependencia transitiva
    // (el manifest merger opera sobre todo el grafo, sin importar api/implementation).
    implementation(project(":feature-sesion"))
    // Nota de trabajo es la única excepción: se mantiene como dependencia directa de :app
    // (además de que feature-sesion también la usa para el acceso secundario "Historial").
    implementation(project(":feature-nota-trabajo"))

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Throwaway instrumented E2E validation (app/src/androidTest) — not a permanent CI suite,
    // see EndToEndFlowTest.kt for context.
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // feature-inspecciones is only an `implementation` dep of feature-sesion (not exposed to
    // :app's compile classpath transitively), but the E2E test needs its
    // HistorialCompartidoHelper directly as a DAO-level fallback for the one legacy
    // Activity+XML module — see EndToEndFlowTest.kt for why.
    androidTestImplementation(project(":feature-inspecciones"))
    // Same reasoning, added for NuevasFuncionesSmokeTest.kt (throwaway, see that file): it calls
    // feature-certificados/feature-repuestos DAO-level helpers directly, same technique as above.
    androidTestImplementation(project(":feature-certificados"))
    androidTestImplementation(project(":feature-repuestos"))
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Tests de regresión de seguridad (docs/security/AUDITORIA-*.md) — parsean el
    // AndroidManifest.xml fuente directamente, sin necesitar Robolectric ni un dispositivo.
    testImplementation("junit:junit:4.13.2")
}
