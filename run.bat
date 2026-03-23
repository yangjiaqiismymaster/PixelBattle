@echo off
echo ============================
echo  像素对战 — 编译 ^& 运行脚本
echo ============================

if not exist "out" mkdir out

echo 正在编译...
javac -encoding UTF-8 -d out src/game/common/*.java src/game/server/*.java src/game/client/*.java

if %ERRORLEVEL% neq 0 (
    echo 编译失败！请检查错误信息。
    pause
    exit /b 1
)

echo 编译成功！
echo.
echo 选择启动方式:
echo   1. 启动服务器
echo   2. 启动客户端 (localhost)
echo   3. 启动客户端 (输入IP)
echo.
set /p choice="请输入选项 (1/2/3): "

if "%choice%"=="1" (
    echo 启动服务器...
    java -cp out game.server.GameServer
)
if "%choice%"=="2" (
    echo 启动客户端连接到 localhost...
    java -cp out game.client.GameClient localhost
)
if "%choice%"=="3" (
    set /p ip="请输入服务器IP: "
    java -cp out game.client.GameClient %ip%
)
pause
