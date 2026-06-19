@echo off
rem ===========================================================================
rem  Build the VaultMesh "install-and-use" package for Windows (.msi).
rem
rem  The build bundles a full Java runtime AND the rclone engine, so the
rem  resulting installer needs nothing preinstalled on the target machine.
rem  Requires a JDK 17+ to run the build itself.
rem
rem  Usage:  scripts\package.cmd
rem ===========================================================================
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "ROOT=%%~fI"

echo ==^> VaultMesh packaging
echo     Project: %ROOT%

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java 17+ is required to build, but 'java' was not found on PATH.
  echo        Install Temurin/OpenJDK 17+ and try again.
  exit /b 1
)

echo ==^> Building native distribution ^(bundles runtime + rclone engine^)...
call "%ROOT%\gradlew.bat" -p "%ROOT%" :app-desktop:packageDistributionForCurrentOS
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

set "OUT=%ROOT%\app-desktop\build\compose\binaries\main"
echo.
echo ==^> Done. Installer^(s^) in: %OUT%\msi
if exist "%OUT%\msi" start "" "%OUT%\msi"

endlocal
