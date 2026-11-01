@echo off
rem ------------------------------------------------------------------------------
rem  Genera Java2UML.exe AUTOCONTENIDO (incluye su propio Java, ~85 MB).
rem  Requiere un JDK 17+ instalado (trae la herramienta jpackage), p. ej. Temurin JDK.
rem  Resultado: exe\Java2UML\Java2UML.exe  (copiable a cualquier PC con Windows)
rem ------------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
set "JAR=java2uml.jar"
if not exist "%JAR%" set "JAR=..\dist\java2uml.jar"
if not exist "%JAR%" (
  echo No se encontro java2uml.jar. Compila primero el proyecto ^(mvn package o compilar-sin-maven.bat^).
  pause
  exit /b 1
)
set "JPACKAGE=jpackage"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
"%JPACKAGE%" --version >nul 2>&1
if errorlevel 1 (
  echo No se encontro jpackage. Instala un JDK 17 o superior ^(no alcanza con el JRE^) y configura JAVA_HOME.
  pause
  exit /b 1
)
if exist build-exe rmdir /s /q build-exe
if exist exe\Java2UML rmdir /s /q exe\Java2UML
mkdir build-exe
copy /y "%JAR%" build-exe\java2uml.jar >nul

"%JPACKAGE%" --type app-image --name Java2UML --input build-exe --main-jar java2uml.jar ^
  --main-class com.umlgen.app.App --dest exe --app-version 1.0.0 --vendor Java2UML ^
  --description "Generador de diagramas de clases UMLetino desde codigo Java" ^
  --add-modules java.base,java.desktop,java.prefs,java.xml ^
  --jlink-options "--strip-debug --no-header-files --no-man-pages"
if errorlevel 1 (
  echo Error al generar el ejecutable.
  pause
  exit /b 1
)
rmdir /s /q build-exe
echo.
echo Listo: exe\Java2UML\Java2UML.exe
echo Podes copiar la carpeta exe\Java2UML completa a otra PC; no necesita Java instalado.
pause
