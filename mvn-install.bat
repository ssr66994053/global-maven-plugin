@echo off
set /p choice=����ѡ��tomcat���л�����p=production,d=dev��
if %choice%==p goto p
if %choice%==d goto d

:d
echo ��ʼ���...
call mvn clean install -P dev -Dmaven.test.skip=true 
echo ������
goto end

:p
echo ��ʼ���...
call mvn clean install -P production -Dmaven.test.skip=true 
echo ������

:end
pause