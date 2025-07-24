@echo off
echo ========================================
echo  ITCS 222L Final Project - Compile All
echo ========================================
echo.

echo [1/2] Compiling Server...
cd "Server_Java"
call compile.bat
if %ERRORLEVEL% neq 0 (
    echo ERROR: Server compilation failed!
    cd ..
    pause
    exit /b 1
)
cd ..
echo Server compilation completed successfully!
echo.

echo [2/2] Compiling Client...
cd "Client_Java"
call compile.bat
if %ERRORLEVEL% neq 0 (
    echo ERROR: Client compilation failed!
    cd ..
    pause
    exit /b 1
)
cd ..
echo Client compilation completed successfully!
echo.

echo ========================================
echo  All components compiled successfully!
echo ========================================
echo.
echo To run the game:
echo 1. Start name service: start_nameservice.bat
echo 2. Start server: run_server.bat
echo 3. Start client: run_client.bat
