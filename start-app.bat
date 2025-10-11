@echo off
REM 设置控制台编码为UTF-8
chcp 65001 > nul

REM 设置Java编码相关环境变量
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8

REM 启动应用
echo 正在启动法律助手应用...
mvn spring-boot:run
