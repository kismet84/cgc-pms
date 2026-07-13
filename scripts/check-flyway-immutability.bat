@echo off
REM check-flyway-immutability.bat
REM Pre-commit hook (Windows): warns if already-committed Flyway migration files are being modified.
REM Exit code 0 (warning only) — does not block commits.
REM
REM Usage: Add to .git/hooks/pre-commit or call from CI:
REM   scripts\check-flyway-immutability.bat

setlocal enabledelayedexpansion

set "MIGRATION_DIR=backend\src\main\resources\db\migration"
set "WARNING=WARNING: Modifying already-applied Flyway migrations. Use new V90+ migrations instead."

if not exist "%MIGRATION_DIR%" (
    exit /b 0
)

REM Find staged, modified (not new) V*.sql files
set "FOUND="
for /f "tokens=*" %%f in ('git diff --cached --name-only --diff-filter=M -- "%MIGRATION_DIR%\V*.sql" 2^>nul') do (
    if not defined FOUND (
        echo.
        echo ============================================================
        echo %WARNING%
        echo ============================================================
        echo Modified migration files:
        set "FOUND=1"
    )
    echo   - %%f
)

if defined FOUND (
    echo ============================================================
    echo Already-applied migrations should NEVER be modified in-place.
    echo Instead: create a new V{next}__description.sql migration.
    echo See: docs/standards/07-数据库与迁移规范.md
    echo ============================================================
    echo.
)

endlocal
exit /b 0
