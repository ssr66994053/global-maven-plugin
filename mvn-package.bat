@echo off
set /p choice=请您选择发布环境（p=production,d=dev）
if %choice%==p goto p
if %choice%==d goto d

:d
echo 开始打包...
call mvn clean package -P dev -Dmaven.test.skip=true 
echo 打包完成
goto end

:p
echo 开始打包...
call mvn clean package -P production -Dmaven.test.skip=true 
echo 打包完成

:end
pause