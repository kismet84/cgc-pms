#!/usr/bin/env python3
"""
CGC-PMS 开发环境重建脚本

用法：
  python scripts/rebuild.py              # 重建后端 + 前端
  python scripts/rebuild.py backend       # 仅重建后端
  python scripts/rebuild.py frontend      # 仅重建前端
  python scripts/rebuild.py --test       # 重建后端 + 前端，并运行全部测试

功能：
  后端: 在宿主机用 Maven 重新构建 JAR，然后重启 Docker 后端容器
  前端: 重启 Docker 前端容器（容器自动 pnpm install + vite dev）
"""

import argparse
import subprocess
import sys
import os
import time
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = PROJECT_ROOT / "backend"
FRONTEND_DIR = PROJECT_ROOT / "frontend-admin"
DEPLOY_DIR = PROJECT_ROOT / "deploy"
COMPOSE_FILE = "docker-compose.dev.yml"

# JDK 21 path — required for Maven builds on Windows
JAVA_HOME = os.environ.get("JAVA_HOME", r"D:\projects-test\jdk-21\jdk-21.0.11+10")

# Jasypt 密钥 — required for running tests
JASYPT_PASSWORD = os.environ.get("JASYPT_PASSWORD", "dev-jasypt-key")


def run(cmd: list[str], *, cwd: Path | None = None, check: bool = True,
        capture: bool = False, timeout: int | None = None, env: dict | None = None) -> subprocess.CompletedProcess:
    """Run a command, print output in real time, return result."""
    full_env = os.environ.copy()
    full_env["JAVA_HOME"] = JAVA_HOME
    full_env.setdefault("JASYPT_ENCRYPTOR_PASSWORD", JASYPT_PASSWORD)
    if env:
        full_env.update(env)

    if sys.platform == "win32" and cmd and cmd[0].lower().endswith(".cmd"):
        cmd = ["cmd", "/c", *cmd]

    print(f"  \033[36m→ {' '.join(cmd)}\033[0m")
    cwd = cwd or PROJECT_ROOT
    if capture:
        return subprocess.run(cmd, cwd=str(cwd), env=full_env, check=check,
                              capture_output=True, text=True, timeout=timeout)
    return subprocess.run(cmd, cwd=str(cwd), env=full_env, check=check, timeout=timeout)


def step_header(msg: str) -> None:
    print(f"\n\033[1;33m{'=' * 60}\033[0m")
    print(f"\033[1;33m  {msg}\033[0m")
    print(f"\033[1;33m{'=' * 60}\033[0m")


def resolve_maven_wrapper(cwd: Path) -> list[str]:
    """Resolve a usable Maven Wrapper command for the current platform."""
    windows_wrapper = cwd / "mvnw.cmd"
    unix_wrapper = cwd / "mvnw"

    if sys.platform == "win32":
        if windows_wrapper.exists():
            return ["mvnw.cmd"]
        if unix_wrapper.exists():
            return ["mvnw"]
    else:
        if unix_wrapper.exists():
            return ["./mvnw"]
        if windows_wrapper.exists():
            return ["mvnw.cmd"]

    raise FileNotFoundError(f"无法在 {cwd} 找到可用的 Maven Wrapper")


def run_maven(cmd: list[str], *, cwd: Path, timeout: int) -> None:
    """Run Maven with a clean-first, package fallback strategy."""
    try:
        run(cmd, cwd=cwd, timeout=timeout)
        return
    except subprocess.CalledProcessError as error:
        if "clean" not in cmd:
            raise
        fallback_cmd = [argument for argument in cmd if argument != "clean"]
        print(f"  \033[33m[WARN] clean 失败，改用 {' '.join(fallback_cmd)}\033[0m")
        run(fallback_cmd, cwd=cwd, timeout=timeout)
    except subprocess.TimeoutExpired:
        if "clean" not in cmd:
            raise

        fallback_cmd = [argument for argument in cmd if argument != "clean"]
        print(f"  \033[33m[WARN] clean 超时，改用 {' '.join(fallback_cmd)}\033[0m")
        run(fallback_cmd, cwd=cwd, timeout=timeout)


def check_docker() -> bool:
    """Check if Docker is running."""
    try:
        run(["docker", "ps", "-q"], capture=True, check=False)
        return True
    except Exception:
        return False


def build_backend(test: bool = False) -> bool:
    """Build backend JAR on host using Maven Wrapper."""
    step_header("后端构建")

    mvnw = resolve_maven_wrapper(BACKEND_DIR)

    # Step 1: Maven clean package
    print(f"\n[1/2] Maven 构建 (clean package)...")
    mvn_args = [*mvnw, "clean", "package", "-DskipTests", "-q"]
    try:
        run_maven(mvn_args, cwd=BACKEND_DIR, timeout=300)
        print("  \033[32m[OK] JAR 构建完成\033[0m")
    except subprocess.CalledProcessError as e:
        print(f"  \033[31m[ERROR] Maven 构建失败 (退出码 {e.returncode})\033[0m")
        return False

    # Step 2: Restart backend Docker container
    print(f"\n[2/2] 重启后端容器...")
    try:
        run(["docker", "compose", "-f", COMPOSE_FILE, "restart", "backend"],
            cwd=DEPLOY_DIR, timeout=60)
        print("  \033[32m[OK] 后端容器已重启\033[0m")
    except subprocess.CalledProcessError:
        print("  \033[31m[ERROR] 容器重启失败\033[0m")
        return False

    # Step 3: Run tests if requested
    if test:
        print(f"\n[测试] 运行后端测试...")
        test_args = [*mvnw, "test", f"-Djasypt.encryptor.password={JASYPT_PASSWORD}"]
        try:
            run(test_args, cwd=BACKEND_DIR, timeout=600)
            print("  \033[32m[OK] 后端测试通过\033[0m")
        except subprocess.CalledProcessError:
            print("  \033[31m[ERROR] 后端测试失败\033[0m")
            return False

    return True


def build_frontend(test: bool = False) -> bool:
    """Restart frontend Docker container (auto pnpm install + dev)."""
    step_header("前端构建")

    # Check if frontend container is running
    result = run(["docker", "ps", "-q", "-f", "name=cgc-pms-frontend-dev"],
                 capture=True, check=False)
    is_running = result.stdout.strip() != ""

    if not is_running:
        print("  前端容器未运行，正在启动...")
        try:
            run(["docker", "compose", "-f", COMPOSE_FILE, "up", "-d", "frontend"],
                cwd=DEPLOY_DIR, timeout=120)
            print("  \033[32m[OK] 前端容器已启动\033[0m")
        except subprocess.CalledProcessError:
            print("  \033[31m[ERROR] 前端容器启动失败\033[0m")
            return False
    else:
        print("[1/1] 重启前端容器 (触发 pnpm install + vite dev)...")
        try:
            run(["docker", "compose", "-f", COMPOSE_FILE, "restart", "frontend"],
                cwd=DEPLOY_DIR, timeout=120)
            print("  \033[32m[OK] 前端容器已重启\033[0m")
        except subprocess.CalledProcessError:
            print("  \033[31m[ERROR] 容器重启失败\033[0m")
            return False

    # Step 2: Run tests on host if requested
    if test:
        print(f"\n[测试] 运行前端测试...")
        try:
            # type-check
            run(["pnpm", "type-check"], cwd=FRONTEND_DIR, timeout=120)
            print("  \033[32m[OK] type-check 通过\033[0m")

            # lint
            run(["pnpm", "lint"], cwd=FRONTEND_DIR, timeout=60, check=False)
            print("  \033[32m[OK] lint 完成\033[0m")

            # unit tests
            run(["pnpm", "test:unit"], cwd=FRONTEND_DIR, timeout=300)
            print("  \033[32m[OK] 单元测试通过\033[0m")
        except subprocess.CalledProcessError:
            print("  \033[31m[ERROR] 前端测试失败\033[0m")
            return False

    return True


def show_status() -> None:
    """Show container status and access URLs."""
    step_header("服务状态")
    try:
        run(["docker", "compose", "-f", COMPOSE_FILE, "ps"],
            cwd=DEPLOY_DIR, check=False)
    except Exception:
        pass

    print(f"""
\033[1;32m  开发环境已就绪\033[0m

  前端:     \033[36mhttp://localhost:5173\033[0m
  后端 API: \033[36mhttp://localhost:8080/api\033[0m
  Swagger:  \033[36mhttp://localhost:8080/api/swagger-ui.html\033[0m
  MinIO:    \033[36mhttp://localhost:9001\033[0m
""")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="CGC-PMS 开发环境重建脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python scripts/rebuild.py                # 重建后端 + 前端
  python scripts/rebuild.py backend         # 仅重建后端
  python scripts/rebuild.py frontend        # 仅重建前端
  python scripts/rebuild.py --test          # 重建 + 运行全部测试
  python scripts/rebuild.py backend --test   # 重建后端 + 运行测试
        """,
    )
    parser.add_argument(
        "target", nargs="?", default="all",
        choices=["all", "backend", "frontend"],
        help="重建目标 (默认: all)",
    )
    parser.add_argument(
        "--test", action="store_true",
        help="重建后运行测试 (type-check + lint + unit test)",
    )
    args = parser.parse_args()

    # Check Docker
    if not check_docker():
        print("\033[31m[ERROR] Docker 未运行。请先启动 Docker Desktop。\033[0m")
        sys.exit(1)

    success = True

    if args.target in ("all", "backend"):
        if not build_backend(test=args.test):
            success = False

    if args.target in ("all", "frontend"):
        if not build_frontend(test=args.test):
            success = False

    if success:
        show_status()
    else:
        print("\n\033[31m  构建未完全成功，请检查上方错误信息。\033[0m")
        sys.exit(1)


if __name__ == "__main__":
    main()
