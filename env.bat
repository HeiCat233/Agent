@echo off
REM Reverse Learning Agent - local toolchain environment (portable, does not touch system settings).
REM Usage: run this once in cmd before mvn/java, e.g.  call env.bat
REM NOTE: keep this file pure ASCII; cmd.exe reads .bat as the OEM codepage (GBK on zh-CN)
REM       and non-ASCII comments corrupt the parsed commands.

REM Override the defaults below via environment variables on machines that differ.
if not defined JAVA_HOME      set JAVA_HOME=D:\dev\jdk-17.0.2
if not defined MAVEN_HOME     set MAVEN_HOME=D:\dev\apache-maven-3.9.9
if not defined MAVEN_REPO     set MAVEN_REPO=D:\dev\m2

set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

REM ZHIPUAI_API_KEY: set it yourself (setx ZHIPUAI_API_KEY "your-key"); never commit it.
echo [env] JAVA_HOME=%JAVA_HOME%
echo [env] MAVEN_HOME=%MAVEN_HOME%
echo [env] MAVEN_REPO=%MAVEN_REPO%
echo [env] JDK/Maven ready. Set ZHIPUAI_API_KEY before using the cloud-glm channel.
