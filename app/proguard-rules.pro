# Variante "liviano" (ver app/build.gradle.kts): reglas conservadoras para no romper en
# runtime lo que la variante debug normal nunca tuvo que enfrentar (reflection).

# Diagnóstico temporal: sin ofuscar nombres de clase, para que los toasts de error que muestran
# e::class.simpleName sean legibles (una excepción real, no una letra sola). El shrinking (sacar
# código muerto) sigue activo -- es lo que más pesa en el tamaño final, no el renombrado.
-dontobfuscate

# Apache POI (feature-inspecciones/informes/certificados, exportación a Excel) descubre clases
# por reflection en varios puntos internos -- mantenerlo entero es lo seguro dado que no hay
# forma de probar en dispositivo antes de mandar este APK.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**

# Ktor/Supabase-kt: motor HTTP y serialización usan reflection/service-loader internamente.
-keep class io.ktor.** { *; }
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# kotlinx.serialization: mantener los serializers generados y las clases @Serializable de toda
# la app (DTOs remotos en core-data y feature-inspeccion-glp).
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.aies.**$$serializer { *; }
-keepclassmembers class com.aies.** {
    *** Companion;
}
-keepclasseswithmembers class com.aies.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room: ya genera implementaciones concretas via KSP, pero las entidades quedan expuestas por
# las mismas dudas de no poder probar en dispositivo.
-keep class com.aies.coredata.entities.** { *; }

# Rutas opcionales de librerías transitivas (log4j vía POI, tink vía Google/Ktor, etc.) pensadas
# para desktop/OSGi -- nunca se ejecutan en Android, solo hace falta que R8 no aborte por no
# encontrar sus clases.
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn java.awt.**
-dontwarn javax.annotation.**
-dontwarn org.osgi.framework.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
