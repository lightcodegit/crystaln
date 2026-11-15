#!/usr/bin/env bash
# Снимок состояния VPN на время подключения CrystalN.
# Сеть не нужна: всё пишется в /tmp/crystaln-vpn-capture.txt.
# Запускать, ПОКА туннель подключен:  bash tools/capture_vpn_state.sh
set -uo pipefail
OUT=/tmp/crystaln-vpn-capture.txt
exec >"$OUT" 2>&1

echo "===== $(date) ====="
IFACE=""
echo "Жду появления туннеля cn-* (до 120 с) — включите соединение в приложении…"
for i in $(seq 1 240); do
    IFACE=$(ip -br link | awk -F: '/^cn-/{gsub(/ /,"",$1); print $1; exit}')
    [ -n "$IFACE" ] && break
    sleep 0.5
done
if [ -z "$IFACE" ]; then
    echo "!!! Туннель cn-* так и не появился — снимок снят в отключённом состоянии."
fi
echo "Туннель: ${IFACE:-нет}. Снимаю состояние."
ip -br link
ip -br addr
echo "--- правила маршрутизации ---"
ip rule show
echo "--- таблица 51820 ---"
ip route show table 51820 2>&1
echo "--- маршруты (main) ---"
ip route show
echo "--- счётчики туннельного интерфейса ($IFACE) ---"
[ -n "$IFACE" ] && ip -s link show "$IFACE"
echo "--- таблица маршрутизации туннеля ---"
[ -n "$IFACE" ] && ip route show table 51820 2>&1
echo "--- параметры туннеля (awg, без root: UAPI-сокет) ---"
[ -n "$IFACE" ] && ls -la /var/run/amneziawg/ 2>&1
echo "--- DNS ---"
resolvectl status 2>&1 | grep -E "Link |Current DNS|DNS Servers|Default Route|DNS Domain" | head -40
echo "--- резолв заблокированных имён (проверка подмены) ---"
for n in youtube.com www.youtube.com instagram.com; do
    printf "%s -> " "$n"; getent hosts "$n" 2>/dev/null | head -1 || echo "(не резолвится)"
done
echo "--- сравнение: DNS провайдера vs туннельный ---"
python3 - <<'EOF'
import socket, struct
def query(server, name):
    try:
        q = b''.join(bytes([len(p)]) + p.encode() for p in name.split('.')) + b'\x00'
        pkt = struct.pack('>HHHHHH', 0x1234, 0x0100, 1, 0, 0, 0) + q + struct.pack('>HH', 1, 1)
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.settimeout(3)
        s.sendto(pkt, (server, 53))
        data, _ = s.recvfrom(512)
        ips = []
        i = len(pkt)
        while i + 16 <= len(data):
            t, c, _, _, l = struct.unpack('>HHBBH', data[i:i+10])
            if t == 1 and l == 4:
                ips.append('.'.join(str(b) for b in data[i+10:i+14]))
            i += 10 + l
        return ips[:2] or ['(без A-записей)']
    except Exception as e:
        return [f'(ошибка: {e})']
for name in ('youtube.com', 'www.google.com'):
    for srv in ('192.168.1.1', '1.1.1.1'):
        print(f'{name} via {srv}: {query(srv, name)}')
EOF
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
