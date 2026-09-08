# easySVA 演示机一键启动（业务栈 + WVP）
# 用法：
#   .\scripts\start_demo.ps1              # 前后端 + ZLM + Analyzer + WVP
#   .\scripts\start_demo.ps1 -WithGbSim   # 再起国标模拟器（默认 monisleep.mp4）
#   .\scripts\start_demo.ps1 -WithStream  # 额外推 cup 到 live/test1
# 本文件须 UTF-8 with BOM，否则 Windows PowerShell 5.1 会把中文引号解析坏掉。

param(
    [switch]$WithGbSim,
    [switch]$WithStream,
    [switch]$SkipHealth
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$driveLetter = $repoRoot.Substring(0, 1).ToLower()
$repoTail = $repoRoot.Substring(2) -replace '\\', '/'
$mntRoot = "/mnt/$driveLetter$repoTail"

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
$wvpScript = "$mntRoot/scripts/start_wvp.sh"
& wsl -d Ubuntu-22.04 -u root -- bash $wvpScript
if ($LASTEXITCODE -ne 0) {
    throw "start_wvp.sh failed with exit code $LASTEXITCODE"
}

if ($WithGbSim) {
    Write-Host ""
    Write-Host "=== [3/3] 国标模拟器（REGISTER + 自动点播 monisleep）===" -ForegroundColor Cyan
    $gbScript = "$mntRoot/scripts/start_gb_sim.sh"
    $gbVideo = if ($env:VIDEO) { $env:VIDEO } else { "$mntRoot/docs/fixtures/monisleep.mp4" }
    Write-Host "VIDEO=$gbVideo"
    & wsl -d Ubuntu-22.04 -u root -- env "VIDEO=$gbVideo" bash $gbScript
    if ($LASTEXITCODE -ne 0) {
        throw "start_gb_sim.sh failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host ""
    Write-Host "=== [3/3] 跳过国标模拟器（需要时加 -WithGbSim）===" -ForegroundColor Yellow
}

if (-not $SkipHealth) {
    Write-Host ""
    Write-Host "=== 开机自检 check_demo_stack.sh ===" -ForegroundColor Cyan
    $check = "$mntRoot/scripts/check_demo_stack.sh"
    if ($WithGbSim) {
        & wsl -d Ubuntu-22.04 -u root -- bash $check --dual-sleep
    } else {
        & wsl -d Ubuntu-22.04 -u root -- bash $check
    }
    if ($LASTEXITCODE -ne 0) {
        Write-Host "自检未全部通过（STACK_FAIL）。可再跑: wsl ... bash scripts/check_demo_stack.sh --dual-sleep" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "启动完成。" -ForegroundColor Green
Write-Host "  业务: http://localhost:8080/   admin / admin123"
Write-Host "  WVP:  http://127.0.0.1:18080/  admin / SvaDemo@2026"
if ($WithGbSim) {
    Write-Host "  国标: 业务页同步 demo-ipc 后预览；双源睡岗见启动手册「双源睡岗保栈」"
}
Write-Host "关闭: wsl -d Ubuntu-22.04 -u root -- bash $mntRoot/scripts/stop_all.sh"