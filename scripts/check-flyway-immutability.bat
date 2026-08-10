@echo off
REM Blocks changes to already-committed Flyway migrations.
REM Local mode checks staged changes. CI mode checks an explicit base...HEAD range.
REM Usage: scripts\check-flyway-immutability.bat [--base sha-or-ref]

setlocal

if not exist "backend\src\main\resources\db\migration" goto missing
if not exist "backend\src\main\resources\db\migration-legacy" goto missing
if not exist "backend\src\main\resources\db\migration-h2" goto missing
if not exist "backend\src\main\resources\db\migration-h2-legacy" goto missing

if "%~1"=="" goto scan_staged
if /I not "%~1"=="--base" goto usage
if "%~2"=="" goto usage
if not "%~3"=="" goto usage
set "BASE=%~2"
git rev-parse --verify "%BASE%" >nul 2>nul
if errorlevel 1 goto bad_base
goto scan_range

:scan_staged
git diff --cached --name-only --diff-filter=MDR -- "backend/src/main/resources/db/migration/V*.sql" "backend/src/main/resources/db/migration-legacy/V*.sql" "backend/src/main/resources/db/migration-h2/V*.sql" "backend/src/main/resources/db/migration-h2-legacy/V*.sql" >nul
if errorlevel 1 exit /b 2
for /f "delims=" %%f in ('git diff --cached --name-only --diff-filter=MDR -- "backend/src/main/resources/db/migration/V*.sql" "backend/src/main/resources/db/migration-legacy/V*.sql" "backend/src/main/resources/db/migration-h2/V*.sql" "backend/src/main/resources/db/migration-h2-legacy/V*.sql"') do call :record "%%f"
goto result

:scan_range
git diff "%BASE%...HEAD" --name-only --diff-filter=MDR -- "backend/src/main/resources/db/migration/V*.sql" "backend/src/main/resources/db/migration-legacy/V*.sql" "backend/src/main/resources/db/migration-h2/V*.sql" "backend/src/main/resources/db/migration-h2-legacy/V*.sql" >nul
if errorlevel 1 exit /b 2
for /f "delims=" %%f in ('git diff "%BASE%...HEAD" --name-only --diff-filter=MDR -- "backend/src/main/resources/db/migration/V*.sql" "backend/src/main/resources/db/migration-legacy/V*.sql" "backend/src/main/resources/db/migration-h2/V*.sql" "backend/src/main/resources/db/migration-h2-legacy/V*.sql"') do call :record "%%f"
goto result

:record
if not defined FOUND echo ERROR: Already-committed Flyway migrations changed.
set "FOUND=1"
echo   - %~1
exit /b 0

:result
if defined FOUND goto blocked
echo Flyway immutability verified.
endlocal
exit /b 0

:blocked
echo Create a new V{next}__description.sql migration instead.
echo See: docs/standards/07-数据库与迁移规范.md
endlocal
exit /b 1

:bad_base
echo ERROR: Unknown Flyway immutability base: %BASE%
endlocal
exit /b 2

:missing
echo ERROR: Required Flyway migration directory is missing.
endlocal
exit /b 2

:usage
echo Usage: %~nx0 [--base sha-or-ref]
endlocal
exit /b 2
