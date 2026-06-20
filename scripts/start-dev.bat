@echo off
REM ============================================
REM CGC-PMS Development Environment (Docker)
REM ============================================

echo ==========================================
echo  CGC-PMS Docker 开发环境启动
echo ==========================================
echo.

REM 检查 .env
if not exist "%~dp0..\deploy\.env" (
    echo [WARN] deploy\.env 不存在，从 .env.example 复制...
    copy "%~dp0..\deploy\.env.example" "%~dp0..\deploy\.env"
    echo [WARN] 请编辑 deploy\.env 填写实际密码后重新运行。
    pause
    exit /b 1
)

REM 1. 预构建后端 JAR（如果 target/ 下没有）
cd /d "%~dp0..\backend"
if not exist "target\cgc-pms-backend.jar" (
    echo [1/3] 构建后端 JAR（首次需等待 Maven 下载依赖）...
    call mvnw.cmd clean package -DskipTests -q
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] 后端构建失败
        pause
        exit /b 1
    )
    echo [OK] JAR 构建完成
) else (
    echo [1/3] JAR 已存在，跳过构建
)

REM 2. 启动 Docker Compose
echo.
echo [2/3] 启动 Docker 容器...
cd /d "%~dp0..\deploy"
docker compose -f docker-compose.dev.yml up -d
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker Compose 启动失败，请检查 Docker Desktop 是否在运行。
    pause
    exit /b 1
)
echo [OK] 容器启动完成

REM 3. 等待健康检查
echo.
echo [3/3] 等待服务就绪...
docker compose -f docker-compose.dev.yml ps

echo.
echo ==========================================
echo  开发环境已就绪（全部在 Docker 内运行）
echo ==========================================
echo.
echo  前端:     http://localhost:5173
echo  后端 API: http://localhost:8080/api
echo  Swagger:  http://localhost:8080/api/swagger-ui.html
echo  MinIO:    http://localhost:9001
echo  MySQL:    localhost:3307
echo  Redis:    localhost:6379
echo.
echo  常用命令:
echo    查看日志: cd deploy ^&^& docker compose -f docker-compose.dev.yml logs -f
echo    重启服务: cd deploy ^&^& docker compose -f docker-compose.dev.yml restart
echo    停止服务: cd deploy ^&^& docker compose -f docker-compose.dev.yml down
echo ==========================================
pause
