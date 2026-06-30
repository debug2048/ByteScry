@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "LIB_DIR=%SCRIPT_DIR%..\lib"
set "JAVAFX_DIR=%SCRIPT_DIR%..\javafx"

java --module-path "%JAVAFX_DIR%" --add-modules javafx.controls -cp "%LIB_DIR%\*" com.github.bytescry.gui.ByteScryGuiLauncher %*

endlocal
