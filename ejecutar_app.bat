@echo off
title Central de Control - Instalador y Ejecutador Android
color 0A

echo =======================================================================
echo          CENTRAL DE CONTROL - COMPILADOR Y EJECUTADOR AUTOMATICO
echo =======================================================================
echo.
echo [1/4] Buscando herramientas del SDK de Android...

:: Definir ruta de ADB local en base al SDK del sistema
SET ADB_PATH="C:\Users\HP Laptop\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if exist %ADB_PATH% (
    SET ADB=%ADB_PATH%
    echo [+] ADB localizado en: %ADB_PATH%
) else (
    SET ADB=adb
    echo [!] Advertencia: No se encontro ADB en la ruta por defecto. Se usara adb global.
)

echo.
echo [2/4] Verificando dispositivos Android conectados...
%ADB% devices
echo.
echo Presiona cualquier tecla para comenzar la compilacion e instalacion en el dispositivo...
pause > nul

echo.
echo [3/4] Compilando e Instalando aplicacion (Gradle Install)...
echo -----------------------------------------------------------------------
call gradlew.bat installDebug

if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo =======================================================================
    echo [ERROR] Hubo un problema al compilar o instalar la aplicacion.
    echo Revisa los mensajes de error de Gradle arriba.
    echo =======================================================================
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo -----------------------------------------------------------------------
echo [+] ¡Instalacion completada con exito!
echo.
echo [4/4] Iniciando la aplicacion en el dispositivo...
%ADB% shell am start -n com.example.myapp/com.example.myapp.MainActivity

if %ERRORLEVEL% NEQ 0 (
    echo [!] No se pudo iniciar automaticamente la actividad principal.
    echo Por favor, abre la aplicacion manualmente en tu dispositivo.
) else (
    echo [+] ¡Aplicacion iniciada exitosamente!
)

echo.
echo =======================================================================
echo            PROCESO FINALIZADO CON EXITO
echo =======================================================================
echo.
pause
