#!/bin/bash
echo "============================"
echo " 像素对战 — 编译 & 运行"
echo "============================"

mkdir -p out

echo "正在编译..."
javac -encoding UTF-8 -d out src/game/common/*.java src/game/server/*.java src/game/client/*.java

if [ $? -ne 0 ]; then
    echo "编译失败！"
    exit 1
fi

echo "编译成功！"
echo ""
echo "选择启动方式:"
echo "  1. 启动服务器"
echo "  2. 启动客户端 (localhost)"
echo "  3. 启动客户端 (输入IP)"
read -p "请输入选项 (1/2/3): " choice

case $choice in
    1) java -cp out game.server.GameServer ;;
    2) java -cp out game.client.GameClient localhost ;;
    3) read -p "请输入服务器IP: " ip; java -cp out game.client.GameClient "$ip" ;;
esac
