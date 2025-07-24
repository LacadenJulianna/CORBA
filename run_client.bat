@echo off
echo Starting Game Client...
echo Make sure the Name Service and Game Server are running first!
echo.
echo This client provides both player and admin functionality
echo.

REM Load centralized configuration
call config.bat

java -cp ".;Client_Java" GameClient -ORBInitialPort %CORBA_PORT% -ORBInitialHost %CORBA_HOST%
pause
