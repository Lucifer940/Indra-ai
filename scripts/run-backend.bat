@echo off
REM INDRA AI - start the backend on Windows
setlocal
cd /d "%~dp0.."

set JAR=modules\indra-backend\build\libs\indra-backend-all.jar
if not exist "%JAR%" (
  echo Backend jar not found - building it now ^(needs JDK 17+ and Gradle 8.x^)...
  pushd modules\indra-backend
  call gradle fatJar --no-daemon
  popd
)

if "%INDRA_JWT_SECRET%"=="" (
  echo Note: INDRA_JWT_SECRET is not set. Development mode uses a temporary secret.
  echo       Set a long random value before exposing this server.
)

java -jar "%JAR%" serve
endlocal
