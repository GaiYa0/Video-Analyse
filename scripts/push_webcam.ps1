# 本机摄像头推流到 ZLM（睡岗测试用）
# 保持此窗口打开；关闭窗口即停推流
$ffmpeg = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.1-full_build\bin\ffmpeg.exe"
if (-not (Test-Path $ffmpeg)) {
  $ffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
}
if (-not $ffmpeg) {
  Write-Error "未找到 ffmpeg，请先 winget install Gyan.FFmpeg"
  exit 1
}

$camera = "Integrated Camera"
$rtmp = "rtmp://127.0.0.1:9995/live/webcam"

Write-Host "推流: $camera -> $rtmp"
Write-Host "按 Ctrl+C 停止"

& $ffmpeg -hide_banner `
  -fflags nobuffer -flags low_delay `
  -f dshow -rtbufsize 2M -i video="$camera" `
  -c:v libx264 -pix_fmt yuv420p -preset ultrafast -tune zerolatency `
  -profile:v baseline -bf 0 -g 30 -an `
  -f flv $rtmp
