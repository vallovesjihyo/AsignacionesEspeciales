@echo off
echo ==================================================
echo   Compilando y Ejecutando IntentAIModel Standalone
echo ==================================================
echo.
javac -d . app/src/main/java/com/example/myapp/ai/IntentAIModel.java
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] La compilacion fallo. Asegurate de tener JDK instalado y en tu PATH.
    pause
    exit /b %errorlevel%
)
java -cp . com.example.myapp.ai.IntentAIModel
pause
