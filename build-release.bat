@echo off
setlocal

cd /d "%~dp0"

for /f "tokens=2 delims==" %%A in ('findstr /b "mod_version=" gradle.properties') do set "MOD_VERSION=%%A"
if "%MOD_VERSION%"=="" (
  echo Could not read mod_version from gradle.properties.
  exit /b 1
)

if exist "C:\Program Files\Java\jdk-26.0.1\bin\java.exe" (
  set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"
)

echo Building Grappling Armor %MOD_VERSION%...
call gradlew.bat clean build
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

if not exist "dist" mkdir "dist"
set "JAR_NAME=grappling-armor-%MOD_VERSION%.jar"

if not exist "build\libs\%JAR_NAME%" (
  echo Expected jar not found: build\libs\%JAR_NAME%
  exit /b 1
)

copy /Y "build\libs\%JAR_NAME%" "dist\%JAR_NAME%" >nul
echo Done: dist\%JAR_NAME%

endlocal
