@echo off
echo 检查编译环境...

echo 1. 检查Java版本:
java -version 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Java未安装或未配置PATH
    echo 请安装Java 17并设置环境变量
    pause
    exit /b 1
)

echo.
echo 2. 检查JAVA_HOME:
if "%JAVA_HOME%"=="" (
    echo JAVA_HOME未设置
    echo 请设置JAVA_HOME环境变量
    pause
    exit /b 1
) else (
    echo JAVA_HOME=%JAVA_HOME%
)

echo.
echo 3. 尝试编译项目:
call mvnw.cmd compile -q

echo.
echo 编译检查完成！
pause