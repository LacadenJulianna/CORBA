@echo off
REM Central configuration for CORBA Name Service
REM Edit these values to change the nameservice host and port for all components

set CORBA_HOST=localhost
set CORBA_PORT=1050

REM Display current configuration
echo CORBA Configuration:
echo   Host: %CORBA_HOST%
echo   Port: %CORBA_PORT%
echo.
