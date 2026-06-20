@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title Docker Desktop + WSL2 完全重启

echo ============================================
echo   Docker Desktop + WSL2 完全重启脚本
echo ============================================
echo.

REM ========================================================
REM 1. 关闭 Docker Desktop（如果正在运行）
REM ========================================================
echo [1/5] 关闭 Docker Desktop...
taskkill /F /IM "Docker Desktop.exe" >nul 2>&1
taskkill /F /IM "docker.exe"          >nul 2>&1
taskkill /F /IM "com.docker.backend.exe" >nul 2>&1
taskkill /F /IM "com.docker.build.exe"   >nul 2>&1
taskkill /F /IM "docker-sandbox.exe"     >nul 2>&1
taskkill /F /IM "vpnkit.exe"             >nul 2>&1
echo         完成
echo.

REM ========================================================
REM 2. 关闭所有 WSL 发行版
REM ========================================================
echo [2/5] 关闭 WSL...

REM 先逐个终止发行版
wsl --list --quiet 2>nul | findstr /R "." > "%TEMP%\wsl_list.txt" 2>nul
if exist "%TEMP%\wsl_list.txt" (
    for /F "tokens=*" %%i in (%TEMP%\wsl_list.txt) do (
        echo         终止 WSL: %%i
        wsl -t "%%i" >nul 2>&1
    )
    del "%TEMP%\wsl_list.txt" 2>nul
)

REM 然后全局 shutdown
wsl --shutdown 2>nul
echo         完成
echo.

REM ========================================================
REM 3. 等待 VM 完全释放
REM ========================================================
echo [3/5] 等待 WSL 虚拟机完全释放...
set /a wait_count=0
:wait_loop
    wsl --list --running 2>nul | findstr "docker-desktop" >nul
    if %errorlevel% neq 0 (
        echo         WSL 已完全释放 (等待 !wait_count!s)
        goto wsl_done
    )
    timeout /T 2 /NOBREAK >nul
    set /a wait_count+=2
    if !wait_count! lss 30 goto wait_loop
    echo         等待超时，继续执行...
:wsl_done
echo.

REM ========================================================
REM 4. 重新启动 Docker Desktop
REM ========================================================
echo [4/5] 启动 Docker Desktop...
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
echo         已发送启动命令
echo.

REM ========================================================
REM 5. 等待 Docker 引擎就绪
REM ========================================================
echo [5/5] 等待 Docker 引擎就绪...
echo         这可能需要 30-60 秒...
set /a retry=0
:wait_docker
    docker ps >nul 2>&1
    if !errorlevel! equ 0 goto docker_ready
    timeout /T 2 /NOBREAK >nul
    set /a retry+=2
    if !retry! lss 60 goto wait_docker

    echo.
    echo ========================================
    echo   启动超时（!retry!秒）
    echo ========================================
    echo.
    echo   请手动执行以下步骤：
    echo   1. 右键任务栏 Docker 图标
    echo   2. 选择 Troubleshoot
    echo   3. 或完全卸载后重装 Docker Desktop
    echo.
    pause
    goto end

:docker_ready
echo.
echo ============================================
echo   Docker Desktop 已就绪！
echo ============================================
echo.
docker ps 2>nul
echo.
echo 现在可以启动 CGC-PMS：
echo   scripts\start-dev.bat
echo.

:end
endlocal
pause
