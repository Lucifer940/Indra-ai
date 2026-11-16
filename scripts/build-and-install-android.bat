@echo off
REM INDRA AI - build the debug APK and install it on a connected phone (Windows)
setlocal enabledelayedexpansion
cd /d "%~dp0.."

where java >nul 2>nul || (echo ERROR: Java not found. Install JDK 17+ and set JAVA_HOME. & exit /b 1)
where gradle >nul 2>nul || (echo ERROR: Gradle not found. Install Gradle 8.x. & exit /b 1)

if "%ANDROID_HOME%"=="" if "%ANDROID_SDK_ROOT%"=="" if not exist local.properties (
  echo ERROR: no Android SDK found.
  echo   Set ANDROID_HOME, for example: setx ANDROID_HOME "C:\Users\%USERNAME%\AppData\Local\Android\Sdk"
  echo   or create local.properties with: sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
  exit /b 1
)

echo Building debug APK...
call gradle :indra-android:assembleDebug --no-daemon -Pandroid
if errorlevel 1 (echo ERROR: build failed - see the compiler output above. & exit /b 1)

set APK=
for /r "modules\indra-android\build\outputs\apk\debug" %%f in (*.apk) do set APK=%%f
if "%APK%"=="" (echo ERROR: build finished but no APK was found. & exit /b 1)
echo APK: %APK%

where adb >nul 2>nul || (
  echo adb not found - install Android platform-tools or install the APK manually.
  exit /b 0
)

adb install -r "%APK%"
if errorlevel 1 (echo Install failed - check the phone is unlocked and USB debugging is allowed. & exit /b 1)

echo Launching INDRA AI...
adb shell am start -n ai.indra.android.debug/ai.indra.android.SplashActivity
echo.
echo Done. On a real phone, set the server to your PC's LAN address in Settings ^> Server.
endlocal
