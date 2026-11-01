@echo off
rem ------------------------------------------------------------------
rem  Java2UML - inicia la aplicacion (requiere Java 17 o superior)
rem ------------------------------------------------------------------
setlocal
set "APP_DIR=%~dp0"
set "JAR=%APP_DIR%java2uml.jar"
if not exist "%JAR%" set "JAR=%APP_DIR%..\dist\java2uml.jar"
if not exist "%JAR%" (
  echo No se encontro java2uml.jar junto a este archivo.
  pause
  exit /b 1
)

set "JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"

"%JAVA%" -version >nul 2>&1
if errorlevel 1 (
  echo No se encontro Java en esta PC.
  echo Instala Java 17 o superior, por ejemplo desde https://adoptium.net/ ^(Temurin 17 o 21^).
  pause
  exit /b 1
)

set "JV="
for /f "tokens=2 delims==" %%v in ('call "%JAVA%" -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"') do set "JV=%%v"
if not defined JV goto run
set "JV=%JV: =%"
if "%JV:~0,2%"=="1." goto old
if %JV% LSS 17 goto old

:run
set "JAVAW=%JAVA:java.exe=javaw.exe%"
if /i "%JAVAW%"=="java" set "JAVAW=javaw"
start "" "%JAVAW%" -jar "%JAR%"
exit /b 0

:old
echo Tu version de Java es %JV%. Java2UML necesita Java 17 o superior.
echo Descargala desde https://adoptium.net/
pause
exit /b 1
