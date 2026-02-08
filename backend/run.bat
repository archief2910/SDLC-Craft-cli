@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-25
set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo ========================================
echo   SDLCraft Backend (using Ollama)
echo ========================================
echo.
echo Make sure Ollama is running: ollama serve
echo.

mvn spring-boot:run
