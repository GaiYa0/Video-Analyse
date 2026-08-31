# 配置 Windows 局域网访问 easySVA（需管理员权限，右键「以管理员身份运行」）
# 原因：WSL2 mirrored 模式下，本机用 http://10.x.x.x/ 访问 80 端口常失败；
#       通过 8080 转发到 WSL 的 80 可让本机与同学都能打开。

#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"

Write-Host "=== easySVA 局域网访问配置 ===" -ForegroundColor Cyan

# 确保 IP Helper 在跑（portproxy 依赖它）
$iphlpsvc = Get-Service iphlpsvc
if ($iphlpsvc.Status -ne 'Running') {
    Start-Service iphlpsvc
}
Write-Host "IP Helper: $($iphlpsvc.Status)"

# 防火墙：放行 80（同学直连 WSL mirrored）和 8080（Windows 转发入口）
$rules = @(
    @{ Name = 'easySVA HTTP 80'; Port = 80 },
    @{ Name = 'easySVA HTTP 8080'; Port = 8080 }
)
foreach ($r in $rules) {
    $existing = Get-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "防火墙规则已存在: $($r.Name)" -ForegroundColor Yellow
    } else {
        New-NetFirewallRule -DisplayName $r.Name -Direction Inbound -Action Allow -Protocol TCP -LocalPort $r.Port -Profile Any | Out-Null
        Write-Host "已添加防火墙规则: $($r.Name) TCP $($r.Port)" -ForegroundColor Green
    }
}

# 删除可能存在的错误 80 转发（会导致 502）
netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=80 2>$null | Out-Null

# 8080 -> WSL nginx(80)，本机与同学统一用这个端口更稳
netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=8080 2>$null | Out-Null
netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=8080 connectaddress=127.0.0.1 connectport=80
Write-Host "已配置端口转发: 0.0.0.0:8080 -> 127.0.0.1:80" -ForegroundColor Green

netsh interface portproxy show all

Write-Host ""
Write-Host "本机浏览器（任选其一）：" -ForegroundColor Green
Write-Host "  http://localhost/"
Write-Host "  http://localhost:8080/"
Write-Host ""
Write-Host "发给同学 B/C（同一 WiFi/局域网）：" -ForegroundColor Green
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
Write-Host "注意：" -ForegroundColor Yellow
Write-Host "  1. 本机不要用 http://<你的IP>/（80端口），WSL mirrored 下常会失败"
Write-Host "  2. 若开了 Clash 等代理，浏览器访问局域网请开「直连」或关代理"
Write-Host "  3. 校园网可能禁止设备互访，同学连不上时换同一 WiFi 或用手机热点试"
