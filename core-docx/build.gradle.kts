plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aies.coredocx"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // api, no implementation: DocxDrawUtils expone tipos de POI (XWPFDocument,
    // XWPFParagraph, XWPFTableCell, etc.) en su superficie pública, así que cualquier
    // módulo que dependa de core-docx necesita POI resoluble en su propio classpath de
    // compilación (misma razón que core-data expone Room como api).
    api("org.apache.poi:poi:5.2.5")
    api("org.apache.poi:poi-ooxml:5.2.5")
}
