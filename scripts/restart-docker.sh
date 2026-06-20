#!/bin/bash
# Docker Desktop + WSL2 完全重启脚本 (bash / PowerShell 版本)
# 用法：在 Git Bash 中执行  bash scripts/restart-docker.sh

set -e

echo "============================================"
echo "  Docker Desktop + WSL2 完全重启"
echo "============================================"
echo ""

# 1. 关闭 Docker Desktop
echo "[1/5] 关闭 Docker Desktop..."
taskkill //F //IM "Docker Desktop.exe" 2>/dev/null || true
taskkill //F //IM "docker.exe" 2>/dev/null || true
taskkill //F //IM "com.docker.backend.exe" 2>/dev/null || true
taskkill //F //IM "com.docker.build.exe" 2>/dev/null || true
taskkill //F //IM "docker-sandbox.exe" 2>/dev/null || true
taskkill //F //IM "vpnkit.exe" 2>/dev/null || true
echo "        Docker Desktop 已关闭"
echo ""

# 2. 终止所有 WSL 发行版
echo "[2/5] 终止 WSL 发行版..."
wsl --list --quiet 2>/dev/null | while read -r distro; do
    if [ -n "$distro" ]; then
        echo "        终止: $distro"
        wsl -t "$distro" 2>/dev/null || true
    fi
done
wsl --shutdown 2>/dev/null || true
echo "        WSL 已关闭"
echo ""

# 3. 等待 VM 完全释放
echo "[3/5] 等待 VM 释放..."
for i in $(seq 1 15); do
    if ! wsl --list --running 2>/dev/null | grep -q "docker-desktop"; then
        echo "        WSL 已完全释放 (${i}s)"
        break
    fi
    sleep 2
done
echo ""

# 4. 启动 Docker Desktop
echo "[4/5] 启动 Docker Desktop..."
start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe" 2>/dev/null || \
    cmd.exe /c start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"
echo "        已发送启动命令"
echo ""

# 5. 等待引擎就绪
echo "[5/5] 等待 Docker 引擎..."
for i in $(seq 1 30); do
    if docker ps >/dev/null 2>&1; then
        echo ""
        echo "============================================"
        echo "  Docker Desktop 已就绪！"
        echo "============================================"
        echo ""
        docker ps 2>/dev/null || true
        echo ""
        echo "启动 CGC-PMS:"
        echo "  scripts/start-dev.bat"
        exit 0
    fi
    sleep 2
done

echo ""
echo "============================================"
echo "  启动超时（60秒）"
echo "  请手动检查 Docker Desktop 状态"
echo "============================================"
exit 1
