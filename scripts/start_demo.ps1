# easySVA 演示机一键启动（业务栈 + WVP）
# 用法：
#   .\scripts\start_demo.ps1              # 前后端 + ZLM + Analyzer + WVP
#   .\scripts\start_demo.ps1 -WithGbSim   # 再起国标模拟器（默认 monisleep.mp4 睡岗片源）
#   .\scripts\start_demo.ps1 -WithStream  # 额外推 cup 到 live/test1（直连测试）

param(
    [switch]$WithGbSim,
    [switch]$WithStream
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

Write-Host "=== [1/3] easySVA 业务栈（MariaDB / Redis / Nginx / backend / ZLM / Analyzer）===" -ForegroundColor Cyan
$easysva = Join-Path $PSScriptRoot "start_easysva.ps1"
if ($WithStream) {
    & $easysva -WithStream
} else {
    & $easysva
}
if ($LASTEXITCODE -ne 0) {
    throw "start_easysva.ps1 failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "=== [2/3] WVP 国标平台（Web 18080 / SIP 5060 + ZLM hook）===" -ForegroundColor Cyan
$wvpScript = "/mnt/e/video-analysis/Video-Analyse-main/scripts/start_wvp.sh"
# Prefer repo path under current drive
$driveLetter = $repoRoot.Substring(0, 1).ToLower()
$repoTail = $repoRoot.Substring(2) -replace '\\', '/'
$wvpScript = "/mnt/$driveLetter$repoTail/scripts/start_wvp.sh"
& wsl -d Ubuntu-22.04 -u root -- bash $wvpScript
if ($LASTEXITCODE -ne 0) {
    throw "start_wvp.sh failed with exit code $LASTEXITCODE"
}

if ($WithGbSim) {
    Write-Host ""
    Write-Host "=== [3/3] 国标模拟器（REGISTER + 自动点播 monisleep）===" -ForegroundColor Cyan
    $gbScript = "/mnt/$driveLetter$repoTail/scripts/start_gb_sim.sh"
    $gbVideo = if ($env:VIDEO) { $env:VIDEO } else { "/mnt/$driveLetter$repoTail/docs/fixtures/monisleep.mp4" }
    Write-Host "VIDEO=$gbVideo"
    & wsl -d Ubuntu-22.04 -u root -- env "VIDEO=$gbVideo" bash $gbScript
    if ($LASTEXITCODE -ne 0) {
        throw "start_gb_sim.sh failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host ""
    Write-Host "=== [3/3] 跳过国标模拟器（需要时加 -WithGbSim）===" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "启动完成。" -ForegroundColor Green
Write-Host "  业务: http://localhost:8080/   admin / admin123"
Write-Host "  WVP:  http://127.0.0.1:18080/  admin / SvaDemo@2026"
if ($WithGbSim) {
    Write-Host "  国标: 业务页同步 demo-ipc 后预览；或 WVP → 国标设备 → 通道 → 播放"
}
Write-Host "关闭: wsl -d Ubuntu-22.04 -u root -- bash /mnt/$driveLetter$repoTail/scripts/stop_all.sh"
