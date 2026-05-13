@echo off
setlocal
call "D:\VS2022\Common7\Tools\VsDevCmd.bat" -arch=x64 -host_arch=x64
if errorlevel 1 (
    echo VsDevCmd.bat failed
    exit /b 1
)
cd /d D:\001\Distributed_MiniSQL\clients\cpp
echo === cmake configure ===
cmake -S . -B build -G Ninja ^
    -DCMAKE_BUILD_TYPE=Release ^
    -DCMAKE_TOOLCHAIN_FILE=D:/VS2022/VC/vcpkg/scripts/buildsystems/vcpkg.cmake ^
    -DVCPKG_TARGET_TRIPLET=x64-windows
set CONFIGURE_RC=%errorlevel%
if %CONFIGURE_RC% neq 0 (
    echo cmake configure failed: %CONFIGURE_RC%
    exit /b %CONFIGURE_RC%
)
echo === cmake build ===
cmake --build build
set BUILD_RC=%errorlevel%
if %BUILD_RC% neq 0 (
    echo cmake build failed: %BUILD_RC%
    exit /b %BUILD_RC%
)
echo === DONE ===
endlocal
