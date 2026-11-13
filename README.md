# CrystalN

Мультипротокольный VPN-клиент для Android. Один процесс ядра, выбор профиля,
живая статистика трафика, уведомление с кнопкой отключения.

## Поддерживаемые протоколы

| Протокол    | Ядро                              | Транспорты / фичи |
|-------------|-----------------------------------|-------------------|
| VMess       | Xray (wire userspace)             | tcp, ws, gRPC, httpupgrade, xhttp, kcp, quic, h2 |
| VLESS       | Xray                              | те же + flow (xtls-rprx-vision) |
| Trojan      | Xray                              | TLS, все транспорты |
| Shadowsocks | Xray                              | все методы ядра |
| SOCKS5      | Xray                              | с авторизацией и без |
| HTTP        | Xray                              | с авторизацией и без |
| WireGuard   | wireguard-go (официальный AAR)    | wg-quick конфиг, AllowedIPs, DNS |

TLS и REALITY настраиваются для любого Xray-профиля (SNI, uTLS fingerprint,
ALPN, publicKey/shortId/spiderX).

## Сборка

Требования: JDK 17+, Android SDK (платформа 35).

```sh
# 1. Скачать ядро Xray (один раз; WireGuard придёт сам из Maven Central)
tools/fetch_cores.sh          # windows: bash tools/fetch_cores.sh

# 2. Собрать debug-APK
./gradlew assembleDebug        # windows: gradlew.bat assembleDebug
```

Готовые APK: `app/build/outputs/apk/debug/` — универсальный `app-debug.apk`
и лёгкие per-ABI варианты (`app-arm64-v8a-debug.apk` и т.д.). Для телефона
обычно нужен arm64-v8a.

## Импорт серверов

- Кнопка «импорт» внизу справа: читает буфер обмена и парсит построчно
  `vless://`, `trojan://`, `ss://` (SIP002 и legacy), `vmess://` (v2rayN),
  `socks5://`, `http(s)://`.
- WireGuard: создайте профиль WireGuard и вставьте готовый wg-quick конфиг
  (PrivateKey/Address/DNS + [Peer] PublicKey/Endpoint/AllowedIPs).

## Архитектура

```
ui/            Compose-free Material UI: список профилей, статус, редактор
data/          Профили (JSON-файл в filesDir), парсер share-ссылок
service/       CrystalVpnService — VpnService для семейства Xray
wg/            WireGuardTunnel — официальный GoBackend (свой VpnService)
xray/          Генератор конфига Xray + обёртка над libv2ray
```

Как это работает:

- **Xray-семейство.** Сервис поднимает TUN-интерфейс (`establish()`),
  свой пакет исключён из VPN (`addDisallowedApplication`) — трафик ядра не
  зацикливается. fd интерфейса передаётся в `CoreController.startLoop(config, fd)`,
  входящий inbound — `{"protocol": "tun"}` со сниффингом http/tls/quic.
- **WireGuard.** Официальный `com.wireguard.android:tunnel` (wireguard-go):
  бэкенд сам стартует свой VpnService (объявлен в манифесте как
  `GoBackend$VpnService`), конфиг парсится из wg-quick текста.
- Статистика: Xray отдаёт счётчики через `queryAllOutboundTrafficStats()`,
  WireGuard — через `GoBackend.getStatistics()`.

## Ограничения v0.1

- Задержка/тест профилей: доступен через `measureOutboundDelay`, в UI пока не выведен.
- Нет per-app прокси и kill-switch (planned).
- OpenVPN не встроен: официального Android-AAR у ics-openvpn нет; план —
  отдельный модуль с сборкой ядра.
- Hysteria2/TUIC потребовали бы sing-box; архитектура движков это позволяет
  добавить третьим ядром.

## Известные особенности сети

Если Gradle падает с `(bad_record_mac) Tag mismatch` при скачивании — это
нестабильный канал к repo.maven.apache.org; в `settings.gradle.kts` подключено
зеркало `maven-central.storage-download.googleapis.com`, а в `gradle.properties`
принудителен TLS 1.2. Повторная сборка подхватывает уже скачанное из кэша.
