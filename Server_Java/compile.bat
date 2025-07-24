@echo off
echo Compiling CORBA GameServer...

REM Set classpath to include MySQL connector and parent directory for GameApp package
set CLASSPATH=.;mysql-connector-j-9.3.0.jar;..

REM Compile the server
javac -cp %CLASSPATH% GameServerApp.java GameServer.java

if %ERRORLEVEL% EQU 0 (
    echo Compilation successful!
    echo.
    echo To run the server:
    echo java -cp %CLASSPATH% GameServerApp
) else (
    echo Compilation failed!
)
