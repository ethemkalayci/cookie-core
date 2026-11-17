@echo off
setlocal
cd /d "%~dp0"
echo Cookie Core - validation locale hors ligne
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%~dp0tools\store\Validate-Local.ps1" -OpenReport
set "validation_exit=%ERRORLEVEL%"
echo.
echo Validation terminee. Voir le rapport HTML et les resultats detailles.
echo Code 0 : controles automatiques passes. Code 1 : echec. Code 2 : blocage.
echo Les essais manuels et Store restent a renseigner dans le rapport.
pause
exit /b %validation_exit%