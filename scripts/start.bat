@echo off
setlocal

set "APP_HOME=%~dp0.."
set "JAR_PATH=%APP_HOME%\target\people-wiki-0.0.1-SNAPSHOT.jar"

if "%KNOWLEDGE_BASE_DATA_PATH%"=="" set "KNOWLEDGE_BASE_DATA_PATH=%USERPROFILE%\.knowledge-base\data\knowledge-base"
if "%KNOWLEDGE_BASE_INDEX_PATH%"=="" set "KNOWLEDGE_BASE_INDEX_PATH=%USERPROFILE%\.knowledge-base\index"
if "%KNOWLEDGE_BASE_IMAGES_PATH%"=="" set "KNOWLEDGE_BASE_IMAGES_PATH=%USERPROFILE%\.knowledge-base\images"

if not exist "%JAR_PATH%" (
  echo 未找到 Jar：%JAR_PATH%
  echo 请先执行：mvn clean package
  exit /b 1
)

java -jar "%JAR_PATH%" %*
