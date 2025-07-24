@echo off
echo Starting CORBA Name Service...
echo This will start the CORBA naming service using centralized configuration
echo Host: %CORBA_HOST% Port: %CORBA_PORT%
echo Keep this window open while running the game server and clients
echo.

REM Load centralized configuration
call config.bat

echo Starting nameservice on %CORBA_HOST%:%CORBA_PORT%
start "Name Service" tnameserv -ORBInitialPort %CORBA_PORT%
