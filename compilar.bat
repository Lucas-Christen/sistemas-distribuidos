@echo off
rem Compila o projeto e gera dist\servidor.jar e dist\cliente.jar (requer JDK 17 ou mais novo).
cd /d "%~dp0"

if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
mkdir out
mkdir dist

javac --release 17 -encoding UTF-8 -d out --source-path src ^
    src\br\edu\utfpr\sdcontas\servidor\ServidorApp.java ^
    src\br\edu\utfpr\sdcontas\cliente\ClienteApp.java
if errorlevel 1 goto erro

jar --create --file dist\servidor.jar --main-class br.edu.utfpr.sdcontas.servidor.ServidorApp -C out .
jar --create --file dist\cliente.jar --main-class br.edu.utfpr.sdcontas.cliente.ClienteApp -C out .

echo Pronto:
echo   java -jar dist\servidor.jar             (janela)
echo   java -jar dist\servidor.jar --console   (terminal)
echo   java -jar dist\cliente.jar
goto :eof

:erro
echo Falha na compilacao. Confira se o JDK 17+ esta no PATH (javac -version).
exit /b 1
