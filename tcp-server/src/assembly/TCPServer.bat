@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>&1
if errorlevel 1 (
  echo Java was not found on PATH. Install JDK 26 and try again.
  exit /b 1
)
java --enable-native-access=javafx.graphics --module-path "%~dp0lib" --add-modules javafx.controls -jar "%~dp0TCPServer.jar" %*
endlocal
