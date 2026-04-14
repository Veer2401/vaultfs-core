@echo off
setlocal enabledelayedexpansion

:: ──────────────────────────────────────────────────────────────
:: VaultFS Installer for Windows
:: Usage: curl -fsSL https://raw.githubusercontent.com/ThreatGuardian/vaultfs-core/main/install.bat -o install.bat && install.bat
:: ──────────────────────────────────────────────────────────────

:: ─── Banner ──────────────────────────────────────────────────
echo.
echo   ======================================
echo   =                                    =
echo   =     VaultFS Installer (Windows)    =
echo   =                                    =
echo   ======================================
echo   Secure file system simulator for your terminal.
echo.

:: ─── Check Java ──────────────────────────────────────────────
echo [*] Checking prerequisites...
echo.

java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo   [X] Java is not installed.
    echo.
    echo   Download and install Java from:
    echo     https://adoptium.net
    echo.
    exit /b 1
)

:: Parse Java version
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER_RAW=%%~g
)
for /f "delims=." %%a in ("!JAVA_VER_RAW!") do set JAVA_MAJOR=%%a
echo   [OK] Java !JAVA_MAJOR! found

:: ─── Check Node.js ───────────────────────────────────────────
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo   [X] Node.js is not installed.
    echo.
    echo   Download and install Node.js from:
    echo     https://nodejs.org
    echo.
    exit /b 1
)

for /f "tokens=1 delims=v." %%a in ('node --version') do set NODE_MAJOR=%%a
echo   [OK] Node.js found

:: ─── Check npm ───────────────────────────────────────────────
npm --version >nul 2>&1
if %errorlevel% neq 0 (
    echo   [X] npm is not installed. It usually comes with Node.js.
    exit /b 1
)
echo   [OK] npm found

:: ─── Check git ───────────────────────────────────────────────
git --version >nul 2>&1
if %errorlevel% neq 0 (
    echo   [X] git is not installed.
    echo.
    echo   Download from: https://git-scm.com/download/win
    echo.
    exit /b 1
)
echo   [OK] git found
echo.

:: ─── Install directory ──────────────────────────────────────
set "INSTALL_DIR=%USERPROFILE%\.vaultfs"
set "REPO_URL=https://github.com/ThreatGuardian/vaultfs-core.git"

if exist "%INSTALL_DIR%" (
    echo   VaultFS is already installed at %INSTALL_DIR%
    set /p "CHOICE=  Reinstall? (y/n) "
    if /i "!CHOICE!" neq "y" (
        set /p "UPGRADE=  Would you like to upgrade instead? (y/n) "
        if /i "!UPGRADE!" equ "y" (
            goto :upgrade
        )
        echo.
        echo   Installation cancelled.
        exit /b 0
    )
    echo   Removing existing installation...
    rmdir /s /q "%INSTALL_DIR%"
)

:: ─── Clone repo ─────────────────────────────────────────────
echo.
echo [*] Cloning repository...
git clone --depth 1 "%REPO_URL%" "%INSTALL_DIR%"
if %errorlevel% neq 0 (
    echo   [X] Failed to clone repository.
    exit /b 1
)
echo   [OK] Repository cloned
goto :build

:upgrade
echo.
echo [*] Upgrading VaultFS...
cd /d "%INSTALL_DIR%"
git pull
if %errorlevel% neq 0 (
    echo   [X] git pull failed.
    exit /b 1
)
echo   [OK] Repository updated

:build
:: ─── Build frontend ─────────────────────────────────────────
echo.
echo [*] Building frontend...
cd /d "%INSTALL_DIR%\frontend"

echo   Installing npm dependencies...
call npm install --silent
if %errorlevel% neq 0 (
    echo   [X] npm install failed.
    exit /b 1
)
echo   [OK] Dependencies installed

echo   Building React app...
call npm run build --silent
if %errorlevel% neq 0 (
    echo   [X] Frontend build failed.
    exit /b 1
)
echo   [OK] Frontend built

:: ─── Compile Java ───────────────────────────────────────────
echo.
echo [*] Compiling Java sources...
cd /d "%INSTALL_DIR%"

javac -d out src\models\*.java src\datastructures\*.java src\utils\*.java src\auth\*.java src\sync\*.java src\filesystem\*.java src\Main.java
if %errorlevel% neq 0 (
    echo   [X] Java compilation failed.
    exit /b 1
)
echo   [OK] All sources compiled

:: ─── Read version ──────────────────────────────────────────
set /p VAULTFS_VERSION=<"%INSTALL_DIR%\version.txt"
echo   [OK] Version: v%VAULTFS_VERSION%

:: ─── Create launcher ────────────────────────────────────────
echo.
echo [*] Creating launcher...

if not exist "%INSTALL_DIR%\bin" mkdir "%INSTALL_DIR%\bin"

(
    echo @echo off
    echo setlocal enabledelayedexpansion
    echo set "INSTALL_DIR=%%USERPROFILE%%\.vaultfs"
    echo.
    echo if "%%~1"=="--version" (
    echo     set /p VER=^<"%%INSTALL_DIR%%\version.txt"
    echo     echo VaultFS v%%VER%%
    echo     exit /b 0
    echo ^
    echo.
    echo if "%%~1"=="-v" (
    echo     set /p VER=^<"%%INSTALL_DIR%%\version.txt"
    echo     echo VaultFS v%%VER%%
    echo     exit /b 0
    echo ^
    echo.
    echo if "%%~1"=="update" (
    echo     echo Checking for updates...
    echo     cd /d "%%INSTALL_DIR%%"
    echo     git fetch origin main
    echo     for /f "tokens=*" %%%%a in ('git rev-parse HEAD'^^^) do set LOCAL=%%%%a
    echo     for /f "tokens=*" %%%%b in ('git rev-parse origin/main'^^^) do set REMOTE=%%%%b
    echo     if "%%LOCAL%%"=="%%REMOTE%%" (
    echo         echo Already up to date.
    echo     ^^ else (
    echo         echo Update available! Pulling latest...
    echo         git pull origin main
    echo         cd frontend ^&^& call npm install ^&^& call npm run build ^&^& cd ..
    echo         javac -d out src\models\*.java src\datastructures\*.java src\utils\*.java src\auth\*.java src\sync\*.java src\filesystem\*.java src\Main.java
    echo         set /p VER=^<"%%INSTALL_DIR%%\version.txt"
    echo         echo VaultFS updated to v%%VER%%!
    echo     ^
    echo     exit /b 0
    echo ^
    echo.
    echo if "%%~1"=="doctor" (
    echo     echo.
    echo     echo VaultFS Doctor — Health Check
    echo     echo =================================
    echo     java -version ^>nul 2^>^&1 ^&^& echo [OK] Java installed ^|^| echo [X] Java: NOT FOUND
    echo     node --version ^>nul 2^>^&1 ^&^& echo [OK] Node installed ^|^| echo [X] Node: NOT FOUND
    echo     git --version ^>nul 2^>^&1 ^&^& echo [OK] Git installed ^|^| echo [X] Git: NOT FOUND
    echo     if exist "%%INSTALL_DIR%%" ^( echo [OK] Install dir exists ^^ ^) else ^( echo [X] Install dir NOT FOUND ^^
    echo     if exist "%%INSTALL_DIR%%\out\Main.class" ^( echo [OK] Java classes compiled ^^ ^) else ^( echo [X] Java classes NOT compiled ^^
    echo     if exist "%%INSTALL_DIR%%\frontend\dist" ^( echo [OK] Frontend built ^^ ^) else ^( echo [X] Frontend NOT built ^^
    echo     if exist "%%INSTALL_DIR%%\version.txt" ^( set /p VER=^<"%%INSTALL_DIR%%\version.txt" ^&^& echo [OK] Version: v%%VER%% ^^ ^) else ^( echo [!] Version file missing ^^
    echo     echo.
    echo     exit /b 0
    echo ^
    echo.
    echo java -Dvaultfs.home="%%INSTALL_DIR%%" -cp "%%INSTALL_DIR%%\out" Main %%*
    echo endlocal
) > "%INSTALL_DIR%\bin\vaultfs.bat"

echo   [OK] Launcher created at %INSTALL_DIR%\bin\vaultfs.bat

:: ─── Add to PATH ────────────────────────────────────────────
echo.
echo [*] Configuring PATH...

echo %PATH% | findstr /i /c:".vaultfs\bin" >nul 2>&1
if %errorlevel% neq 0 (
    setx PATH "%USERPROFILE%\.vaultfs\bin;%PATH%"
    echo   [OK] Added to PATH
) else (
    echo   [OK] PATH already configured
)

:: ─── Check if this was an upgrade ───────────────────────────
if /i "!UPGRADE!" equ "y" (
    goto :upgrade_done
)

:: ─── Done ───────────────────────────────────────────────────
echo.
echo   ======================================
echo   =                                    =
echo   =     VaultFS installed!             =
echo   =                                    =
echo   ======================================
echo.
echo   Open a NEW terminal and type: vaultfs
echo.
echo   Installed to: %INSTALL_DIR%
echo   Launcher:     %INSTALL_DIR%\bin\vaultfs.bat
echo.
goto :eof

:upgrade_done
echo.
echo   ======================================
echo   =                                    =
echo   =     VaultFS upgraded!              =
echo   =                                    =
echo   ======================================
echo.
echo   VaultFS upgraded to v%VAULTFS_VERSION%!
echo   Open a NEW terminal and type: vaultfs
echo.

endlocal
