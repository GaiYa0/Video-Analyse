@echo off
:: 需右键「以管理员身份运行」
echo === easySVA 开放局域网端口 ===

netsh advfirewall firewall delete rule name="easySVA HTTP 80" >nul 2>&1
netsh advfirewall firewall delete rule name="easySVA HTTP 8080" >nul 2>&1
netsh advfirewall firewall add rule name="easySVA HTTP 80" dir=in action=allow protocol=TCP localport=80 profile=any
netsh advfirewall firewall add rule name="easySVA HTTP 8080" dir=in action=allow protocol=TCP localport=8080 profile=any

netsh interface portproxy delete v4tov4 listenaddress=0.0.0.0 listenport=8080 >nul 2>&1
netsh interface portproxy add v4tov4 listenaddress=0.0.0.0 listenport=8080 connectaddress=127.0.0.1 connectport=80

echo.
echo --- 防火墙规则 ---
netsh advfirewall firewall show rule name="easySVA HTTP 8080"
echo --- 端口转发 ---
netsh interface portproxy show all
echo.
echo 本机: http://localhost:8080/
echo 同学: http://^<你的IP^>:8080/
echo.
pause
