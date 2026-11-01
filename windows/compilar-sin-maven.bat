@echo off
rem ------------------------------------------------------------------
rem  Compila Java2UML sin Maven (solo necesita un JDK 17+).
rem  Resultado: dist\java2uml.jar
rem ------------------------------------------------------------------
setlocal
cd /d "%~dp0.."
if exist build rmdir /s /q build
mkdir build\classes
dir /s /b src\main\java\*.java > build\sources.txt
javac --release 17 -encoding UTF-8 -d build\classes -cp lib\javaparser-core-3.28.2.jar @build\sources.txt
if errorlevel 1 (
  echo Error de compilacion.
  pause
  exit /b 1
)
xcopy /e /i /y /q src\main\resources build\classes >nul
pushd build\classes
jar xf ..\..\lib\javaparser-core-3.28.2.jar
if exist META-INF rmdir /s /q META-INF
popd
if not exist dist mkdir dist
jar cfe dist\java2uml.jar com.umlgen.app.App -C build\classes .
if errorlevel 1 (
  echo Error al crear el jar.
  pause
  exit /b 1
)
rmdir /s /q build
echo Listo: dist\java2uml.jar
pause
