# 本机摄像头推流到 ZLM（睡岗测试用）
# 保持此窗口打开；关闭窗口即停推流
# 用法：.\scripts\push_webcam.ps1
#       .\scripts\push_webcam.ps1 -Camera "ASUS FHD webcam"
param(
    [string]$Camera = "",
    [string]$Rtmp = "rtmp://127.0.0.1:9995/live/webcam"
)

$ffmpeg = "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe\ffmpeg-9.0.1-full_build\bin\ffmpeg.exe"
if (-not (Test-Path $ffmpeg)) {
    $ffmpeg = (Get-Command ffmpeg -ErrorAction SilentlyContinue).Source
}
if (-not $ffmpeg) {
    Write-Error "未找到 ffmpeg，请先 winget install Gyan.FFmpeg"
    exit 1
}

function Get-DshowVideoDevices {
    $raw = & $ffmpeg -hide_banner -list_devices true -f dshow -i dummy 2>&1 | Out-String
    $devices = New-Object System.Collections.Generic.List[string]
    $inVideo = $false
    foreach ($line in ($raw -split "`r?`n")) {
        if ($line -match "DirectShow video devices") {
            $inVideo = $true
            continue
        }
        if ($line -match "DirectShow audio devices") {
            $inVideo = $false
            continue
        }
        if ($inVideo -and $line -match '"([^"]+)"') {
            $devices.Add($Matches[1]) | Out-Null
        }
    }
    return $devices
}

if (-not $Camera) {
    $devices = Get-DshowVideoDevices
    $preferred = @("ASUS FHD webcam", "Integrated Camera")
    foreach ($name in $preferred) {
        if ($devices -contains $name) {
            $Camera = $name
            break
        }
    }
    if (-not $Camera -and $devices.Count -gt 0) {
        $Camera = $devices[0]
    }
    if ($devices.Count -gt 0) {
        Write-Host "可用视频设备: $($devices -join ', ')"
    }
}

if (-not $Camera) {
    Write-Error "未找到 DirectShow 摄像头。可执行: ffmpeg -list_devices true -f dshow -i dummy"
    exit 1
}

Write-Host "推流: $Camera -> $Rtmp"
Write-Host "按 Ctrl+C 停止"

& $ffmpeg -hide_banner `
    -fflags nobuffer -flags low_delay `
    -f dshow -rtbufsize 2M -i video="$Camera" `
    -c:v libx264 -pix_fmt yuv420p -preset ultrafast -tune zerolatency `
    -profile:v baseline -bf 0 -g 30 -an `
    -f flv $Rtmp
