@echo off
set /p choice=����ѡ�񷢲�������p=production,d=dev��
if %choice%==p goto p
if %choice%==d goto d

:d
echo ��ʼ���...
call mvn -T 1C clean deploy -P dev -Dmaven.test.skip=true 
echo ����������������
goto end

:p
echo ��ʼ���...
call mvn -T 1C clean deploy -P production -Dmaven.test.skip=true 
echo ����������������

:end
pause