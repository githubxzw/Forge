#!/bin/bash
# Forge 一键打包：构建前端 → 拷入后端静态资源 → 打包可执行 jar
set -e
cd "$(dirname "$0")"

# 使用 JDK17（本机默认 1.8 时自动探测）
if [ -z "$JAVA_HOME" ]; then
  if [ -d "$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home" ]; then
    export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/corretto-17.0.13/Contents/Home"
  fi
fi

echo "==> [1/3] 构建 forge-web"
cd forge-web
npm install --no-audit --no-fund
npm run build

echo "==> [2/3] 拷贝前端产物到 forge-server/static"
cd ..
rm -rf forge-server/src/main/resources/static
mkdir -p forge-server/src/main/resources/static
cp -r forge-web/dist/* forge-server/src/main/resources/static/

echo "==> [3/3] 打包 forge-server"
cd forge-server
mvn -q -DskipTests package
echo ""
echo "✅ 打包完成: forge-server/target/forge-server.jar"
echo "   运行: java -jar forge-server/target/forge-server.jar"
