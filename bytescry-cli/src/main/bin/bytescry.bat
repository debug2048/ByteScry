@echo off
setlocal

set "BYTESCRY_HOME=%~dp0.."
set "CLASSPATH=%BYTESCRY_HOME%\lib\*"

java -cp "%CLASSPATH%" com.github.bytescry.cli.ByteScryCli %*
