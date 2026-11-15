#!/usr/bin/env bash
# Снимок состояния VPN на время подключения CrystalN.
# Сеть не нужна: всё пишется в /tmp/crystaln-vpn-capture.txt.
# Запускать, ПОКА туннель подключен:  bash tools/capture_vpn_state.sh
set -uo pipefail
OUT=/tmp/crystaln-vpn-capture.txt
exec >"$OUT" 2>&1

echo "===== $(date) ====="
echo "--- интерфейсы ---"
ip -br link
ip -br addr
echo "--- правила маршрутизации ---"
ip rule show
echo "--- таблица 51820 ---"
ip route show table 51820 2>&1
echo "--- маршруты (main) ---"
ip route show
echo "--- счётчики туннельного интерфейса ---"
for i in $(ip -br link | awk -F: '/^cn-/{gsub(/ /,"",$1); print $1}'); do
    ip -s link show "$i"
done
echo "--- DNS ---"
resolvectl status 2>&1 | grep -E "Link|Current DNS|DNS Servers|Default Route" | head -40
echo "--- ping 1.1.1.1 (через туннель, если он full-tunnel) ---"
ping -c 3 -W 3 1.1.1.1 2>&1 | tail -4
echo "--- TCP 1.1.1.1:443 ---"
curl -m 8 -s -o /dev/null -w "code=%{http_code} time=%{time_total}s\n" https://1.1.1.1 2>&1
echo "--- DNS-резолв (getent) ---"
getent hosts google.com 2>&1 | head -3
echo "--- HTTPS по имени (DNS+TCP+TLS) ---"
curl -m 10 -s -o /dev/null -w "code=%{http_code} time=%{time_total}s\n" https://www.google.com/generate_204 2>&1
echo "--- состояние WireGuard/AWG (если доступно) ---"
sudo -n awg show all 2>&1 | head -30 || true
echo "--- лог демона amneziawg-go ---"
tail -50 /var/run/amneziawg/cn-*.log 2>/dev/null
echo "===== конец снимка ====="
