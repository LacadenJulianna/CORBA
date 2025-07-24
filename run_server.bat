@echo off
echo Starting Game Server
echo Make sure the Name Service is running first!
echo Also ensure MySQL is running with the game_db database set up.
echo.

REM Load centralized configuration
call config.bat

cd Server_Java
java -cp .;mysql-connector-j-9.3.0.jar;.. GameServerApp -ORBInitialPort %CORBA_PORT% -ORBInitialHost %CORBA_HOST%
pause
