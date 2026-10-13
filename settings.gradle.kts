pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AiesSuperApp"

include(
    ":app",
    ":core-data",
    ":core-ui",
    ":core-pdf",
    ":core-docx",
    ":feature-inspecciones",
    ":feature-certificados",
    ":feature-informes",
    ":feature-nota-trabajo",
    ":feature-inspeccion-ejes",
    ":feature-inspeccion-glp",
    ":feature-controles-visuales",
    ":feature-calibrado",
    ":feature-repuestos",
    ":feature-planilla-tapas",
    ":feature-sesion"
)
