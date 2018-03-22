@echo off
set /p choice=请您选择发布环境（p=production,d=dev）
if %choice%==p goto p
if %choice%==d goto d

:d
echo 开始打包...
call mvn -T 1C clean deploy -P dev -Dmaven.test.skip=true 
echo 打包到开发环境完成
goto end

:p
echo 开始打包...
call mvn -T 1C clean deploy -P production -Dmaven.test.skip=true 
echo 打包到生产环境完成

:end
pause