@echo off
setlocal

rem === Compila la app y deja el APK debug en el Escritorio ===
rem Uso: doble click en este archivo, o correrlo desde una terminal parado en esta carpeta.

set "JAVA_HOME=C:\Users\leolo\jdks\jdk-17.0.20.1+1"
set "PROYECTO=%~dp0"
set "APK_ORIGEN=%PROYECTO%app\build\outputs\apk\debug\app-debug.apk"
set "APK_DESTINO=%USERPROFILE%\OneDrive\Desktop\app-debug.apk"

echo Usando JAVA_HOME=%JAVA_HOME%
echo.

cd /d "%PROYECTO%"
call "%PROYECTO%gradlew.bat" :app:assembleDebug --console=plain
if errorlevel 1 (
    echo.
    echo ============================================
    echo   FALLO LA COMPILACION. Revisa el log arriba.
    echo ============================================
    pause
    exit /b 1
)

if not exist "%APK_ORIGEN%" (
    echo No se encontro el APK generado en:
    echo   %APK_ORIGEN%
    pause
    exit /b 1
)

copy /y "%APK_ORIGEN%" "%APK_DESTINO%" >nul
echo.
echo ============================================
echo   OK: APK copiado a %APK_DESTINO%
echo ============================================
pause
