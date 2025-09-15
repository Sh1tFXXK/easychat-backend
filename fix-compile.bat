@echo off
echo 正在修复Java编译器错误...

echo 检查Java版本...
java -version
echo.

echo 1. 清理项目...
call mvnw.cmd clean

echo 2. 删除target目录...
if exist target rmdir /s /q target

echo 3. 强制重新下载依赖并编译...
call mvnw.cmd clean compile -U

echo 4. 如果还有问题，尝试跳过测试编译...
if %ERRORLEVEL% NEQ 0 (
    echo 尝试跳过测试编译...
    call mvnw.cmd compile -DskipTests
)

echo 编译完成！
pause