@echo off
echo Starting Python Player Client...
echo Make sure the CORBA Name Service and Game Server are running!
echo.

REM Load centralized configuration
call "..\config.bat"

python player_client.py -ORBInitialHost %CORBA_HOST% -ORBInitialPort %CORBA_PORT%
pause
