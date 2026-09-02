@echo off
set FFMPEG=%LOCALAPPDATA%\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.1-full_build\bin\ffmpeg.exe
if not exist "%FFMPEG%" (
  echo ffmpeg not found
  exit /b 1
)
title easySVA webcam push
echo Pushing Integrated Camera to rtmp://127.0.0.1:9995/live/webcam
echo Close this window to stop.
"%FFMPEG%" -hide_banner -fflags nobuffer -flags low_delay -f dshow -rtbufsize 2M -i video="Integrated Camera" -c:v libx264 -pix_fmt yuv420p -preset ultrafast -tune zerolatency -profile:v baseline -bf 0 -g 30 -an -f flv rtmp://127.0.0.1:9995/live/webcam
pause
