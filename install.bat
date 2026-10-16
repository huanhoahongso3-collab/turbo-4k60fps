@echo off
rem One click: download the newest CI build and install it on the phone over USB.
rem Needs: GitHub CLI (gh) logged in, adb on PATH, USB debugging enabled on the phone.
setlocal
set OUT=%TEMP%\turbo4k
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"

echo Downloading the latest Turbo4K build...
gh release download dev --pattern Turbo4K.apk --dir "%OUT%" || goto :fail

echo Installing on the phone...
adb install -r "%OUT%\Turbo4K.apk" || goto :fail

adb shell am start -n dhp.tpl.turbo.v4k60fps.unsupported/.MainActivity >nul
echo Done. Turbo4K is open on your phone.
pause
exit /b 0

:fail
echo Something failed. Check that the phone is plugged in with USB debugging on, and that "gh auth status" works.
pause
exit /b 1
