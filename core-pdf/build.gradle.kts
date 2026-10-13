plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aies.corepdf"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Elegir iText7 o PdfBox-Android acá cuando se porte la primera plantilla
    // (certificado de tapa de boca de hombre). Se deja sin resolver a propósito.
}
