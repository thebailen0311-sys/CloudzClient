@echo off
setlocal
cd /d "%~dp0"
set GV=9.2.1
set GDIR=%~dp0.gradle-dist\gradle-%GV%

if exist "gradle\wrapper\gradle-wrapper.jar" if exist "gradlew.bat" goto build

echo [Cloud] Gradle wrapper missing - setting it up (one time)...
if not exist "%GDIR%\bin\gradle.bat" (
  if not exist ".gradle-dist" mkdir ".gradle-dist"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GV%-bin.zip' -OutFile '.gradle-dist\g.zip'; Expand-Archive '.gradle-dist\g.zip' '.gradle-dist' -Force; Remove-Item '.gradle-dist\g.zip'"
)
if not exist "%GDIR%\bin\gradle.bat" (
  echo [Cloud] Failed to download Gradle. Check your internet connection.
  pause & exit /b 1
)
call "%GDIR%\bin\gradle.bat" wrapper --gradle-version %GV%
if errorlevel 1 ( echo [Cloud] Wrapper generation failed. & pause & exit /b 1 )

:build
echo [Cloud] Building...
call gradlew.bat clean build
if errorlevel 1 ( echo. & echo [Cloud] BUILD FAILED - copy the errors above. & pause & exit /b 1 )
echo.
echo [Cloud] BUILD SUCCESSFUL - jar is in build\libs
explorer "build\libs"
pause
