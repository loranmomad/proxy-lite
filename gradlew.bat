@rem Gradle wrapper bootstrap for Windows
@echo off
set DIR=%~dp0
where gradle >nul 2>&1
if %errorlevel%==0 (
  gradle %*
  goto :eof
)
if exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  goto :eof
)
echo gradle not found and gradle-wrapper.jar missing. 1>&2
exit /b 1
