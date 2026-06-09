@echo off
echo.
echo ========== ReCall Project Setup ==========
echo.
echo Downloading Gradle 8.5...
echo.

if not exist gradle-8.5-bin.zip (
    curl -L -o gradle-8.5-bin.zip https://services.gradle.org/distributions/gradle-8.5-bin.zip
)

echo Extracting Gradle...
tar -xf gradle-8.5-bin.zip

echo.
echo ========== Setup Complete ==========
echo.
echo To build the project, run:
echo   gradle-8.5\bin\gradle build
echo.
echo Or add gradle-8.5\bin to your PATH and run:
echo   gradle build
echo.
pause
