@echo off
echo Compiling CORBA Game Clients...

REM Change to parent directory to compile with correct package structure
cd ..

REM Compile the clients from project root
javac -cp "." Client_Java\GameClient.java

if %ERRORLEVEL% EQU 0 (    echo Compilation successful!
    echo.
    echo To run the Client:
    echo java -cp ".;Client_Java" GameClient
) else (
    echo Compilation failed!
)

REM Return to Client_Java directory
cd Client_Java
