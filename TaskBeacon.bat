@echo off
setlocal
set "TASKBEACON_EXE=%~dp0release\TaskBeacon\TaskBeacon.exe"

if not exist "%TASKBEACON_EXE%" (
    echo TaskBeacon has not been packaged yet.
    echo Run: .\gradlew.bat :app:portableExe
    pause
    exit /b 1
)

start "" /D "%~dp0release\TaskBeacon" "%TASKBEACON_EXE%"
