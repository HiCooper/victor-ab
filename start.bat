@echo off
echo ========================================
echo Starting Victor Backend Service...
echo ========================================
echo.

cd /d "%~dp0"

echo Step 1: Building project...
call mvn clean install -DskipTests
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo Step 2: Starting Spring Boot application...
echo.
call mvn spring-boot:run -pl victor-web

pause
