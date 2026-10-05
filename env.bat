@echo off
REM 逆向学习辅助Agent - 本机环境变量（绿色版，不污染系统）
REM 用法：在运行 mvn / java 前执行一次本脚本，或双击后继续操作
set JAVA_HOME=D:\dev\jdk-17.0.2
set PATH=%JAVA_HOME%\bin;D:\dev\apache-maven-3.9.9\bin;%PATH%
set ZHIPUAI_API_KEY=
echo [env] JAVA_HOME=%JAVA_HOME%
echo [env] Maven / JDK 已就绪（ZHIPUAI_API_KEY 当前为空，走 GLM 通道前请设置）
cd /d D:\develop\git\webAgent
