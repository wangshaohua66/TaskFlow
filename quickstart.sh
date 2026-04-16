#!/bin/bash

# TaskFlow 快速启动脚本

echo "======================================"
echo "TaskFlow - 任务管理系统"
echo "======================================"
echo ""

# 检查Java版本
echo "1. 检查Java环境..."
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "   Java版本: $java_version"

if [[ ! "$java_version" =~ ^17 ]]; then
    echo "   ⚠️  警告: 建议使用Java 17"
fi
echo ""

# 编译项目
echo "2. 编译项目..."
mvn clean compile -q
if [ $? -eq 0 ]; then
    echo "   ✅ 编译成功"
else
    echo "   ❌ 编译失败"
    exit 1
fi
echo ""

# 运行测试(可选)
echo "3. 运行单元测试..."
read -p "是否运行测试? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    mvn test -q
    test_result=$?
    echo ""
    if [ $test_result -eq 0 ]; then
        echo "   ✅ 所有测试通过"
    else
        echo "   ❌ 部分测试失败,请检查"
        exit 1
    fi
else
    echo "   ⏭️  跳过测试"
fi
echo ""

# 启动应用
echo "4. 启动应用..."
echo "   访问地址: http://localhost:8080"
echo "   按 Ctrl+C 停止服务"
echo ""

mvn spring-boot:run
