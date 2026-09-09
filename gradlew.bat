@rem Minimal Gradle wrapper launcher for Windows.
@if "%DEBUG%"=="" @echo off
set DIRNAME=%~dp0
if "%JAVA_HOME%"=="" (
  set JAVA_EXE=java.exe
) else (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
)
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
