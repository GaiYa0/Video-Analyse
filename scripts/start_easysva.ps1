# easySVA 演示机一键启动（同学 A · Windows + WSL2）
# 用法：在 PowerShell 中执行 .\scripts\start_easysva.ps1
# 附带测试推流：.\scripts\start_easysva.ps1 -WithStream

param(
    [switch]$WithStream
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$driveLetter = $repoRoot.Substring(0, 1).ToLower()
$repoTail = $repoRoot.Substring(2) -replace '\\', '/'
$wslScript = "/mnt/$driveLetter$repoTail/scripts/start_easysva.sh"

$args = @("-d", "Ubuntu-22.04", "-u", "root", "--", "bash", $wslScript)
if ($WithStream) {
    $args += "--with-stream"
}

Write-Host "启动 easySVA: wsl $($args -join ' ')" -ForegroundColor Cyan
& wsl @args
if ($LASTEXITCODE -ne 0) {
    throw "start_easysva.sh failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "本机浏览器（不要用本机 IP 的 80 端口）：" -ForegroundColor Green
Write-Host "  http://localhost/"
Write-Host "  http://localhost:8080/  （局域网转发入口，推荐）"
Write-Host ""
Write-Host "发给同学 B/C（同一 WiFi，用 :8080）：" -ForegroundColor Green
Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object {
        $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|VMware|Hyper-V' -and
        $_.IPAddress -notlike '127.*' -and
        $_.IPAddress -notlike '169.254.*' -and
        $_.IPAddress -notlike '192.168.154.*' -and
        $_.IPAddress -notlike '192.168.204.*'
    } |
    ForEach-Object {
        Write-Host "  http://$($_.IPAddress):8080/  ($($_.InterfaceAlias))"
    }

Write-Host ""
Write-Host "账号 admin / admin123" -ForegroundColor Green
Write-Host "同学连不上时，以管理员运行: .\scripts\setup_windows_lan_access.ps1" -ForegroundColor Yellow
