@echo off
REM ============================================
REM CGC-PMS Project Startup Script (Windows)
REM ============================================

echo ==========================================
echo  CGC-PMS Local Development Startup
echo ==========================================
echo.

REM 1. Start Docker containers
echo [1/3] Starting Docker containers...
cd /d "%~dp0..\deploy"
docker compose up -d
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Docker compose failed. Make sure Docker is installed and running.
) else (
    echo [OK] MySQL, Redis, MinIO started.
)

REM 2. Start backend
echo.
echo [2/3] Starting backend...
cd /d "%~dp0..\backend"
start "CGC-PMS Backend" cmd /c "mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo [OK] Backend starting on http://localhost:8080/api

REM 3. Start frontend
echo.
echo [3/3] Starting frontend...
cd /d "%~dp0..\frontend-admin"
start "CGC-PMS Frontend" cmd /c "pnpm dev"
echo [OK] Frontend starting on http://localhost:5173

echo.
echo ==========================================
echo  All services starting...
echo  Backend:  http://localhost:8080/api/swagger-ui.html
echo  Frontend: http://localhost:5173
echo  MinIO:    http://localhost:9001
echo ==========================================
pause
