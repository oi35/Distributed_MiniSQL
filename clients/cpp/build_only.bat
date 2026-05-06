@echo off
setlocal
call "D:\VS2022\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64
if errorlevel 1 (
    echo VsDevCmd.bat failed
    exit /b 1
)
cd /d D:\001\Distributed_MiniSQL\clients\cpp
cmake --build build
endlocal
