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
| WireGuard   | amneziawg-go (официальное ядро Amnezia) | wg-quick конфиг, AllowedIPs, DNS |
| AmneziaWG   | amneziawg-go                      | junk-параметры Jc/Jmin/Jmax/S1/S2/H1–H4 |
| OpenVPN     | openvpn 2.6 (официальное ядро, TARGET_ANDROID) | .ovpn конфиг, UDP/TCP, TLS-инлайн-сертификаты, auth-user-pass |

TLS и REALITY настраиваются для любого Xray-профиля (SNI, uTLS fingerprint,
ALPN, publicKey/shortId/spiderX).

## AmneziaWG (настоящее ядро)

AmneziaWG работает через официальный `amneziawg-go` userspace-кор
(репозиторий `amnezia-vpn/amneziawg-android`, пакет `org.amnezia.awg.*`).
Обычные WireGuard-конфиги ходят через то же ядро: amneziawg-go — надмножество
WireGuard, при нулевых junk-параметрах поведение идентично.

Параметры обфускации хранятся в профиле и добавляются в секцию `[Interface]`
конфига (стандарт AmneziaWG); ключи, уже прописанные в конфиге, не дублируются.

Сборка ядра (нужны Go 1.25+ и Android NDK):

```sh
bash tools/build_awg_core.sh   # клонирует third_party/amneziawg-android и собирает AAR
```

## OpenVPN (официальное ядро)

OpenVPN работает через официальный `openvpn 2.6`, собранный из исходников для
Android (`TARGET_ANDROID`): tun-интерфейс создаёт приложение через
`VpnService.establish()`, а openvpn получает его fd через management-интерфейс
(SCM_RIGHTS) — официальный механизм из `doc/android.txt` ядра, тот же, что
использует ics-openvpn. Обвязка — чистая Java (`LocalSocket`/`LocalServerSocket`
+ `setFileDescriptorsForSend`), без JNI.

Кастомные директивы, неприменимые на Android (скрипты up/down, plugin,
внешние helper-файлы, свои management/status), вырезаются из конфига;
`auth-user-pass` заменяется на credentials-файл (логин/пароль из редактора
профиля). Инлайн-сертификаты (`<ca>`, `<cert>`, `<key>`) и статические ключи
работают как есть.

Сборка ядра (нужен Android NDK; OpenSSL/LZO/LZ4 скачиваются и линкуются
статически):

```sh
bash tools/build_openvpn_core.sh   # кладёт libovpnexec.so в app/src/main/jniLibs/<abi>/
```

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
- OpenVPN: вставьте готовый `.ovpn` конфиг (сертификаты должны быть встроены
  в текст: `<ca>`, `<cert>`, `<key>`) в редакторе профиля; либо просто
  импортируйте текст конфига из буфера обмена.

## Архитектура

```
ui/            Compose-free Material UI: список профилей, статус, редактор
data/          Профили (JSON-файл в filesDir), парсер share-ссылок
service/       CrystalVpnService — VpnService для семейства Xray
wg/            WireGuardTunnel — официальный GoBackend (свой VpnService)
xray/          Генератор конфига Xray + обёртка над libv2ray
openvpn/       OpenVpnService — официальное ядро openvpn (child process,
               management-интерфейс, tun-fd через SCM_RIGHTS)
```

Как это работает:

- **Xray-семейство.** Сервис поднимает TUN-интерфейс (`establish()`),
  свой пакет исключён из VPN (`addDisallowedApplication`) — трафик ядра не
  зацикливается. fd интерфейса передаётся в `CoreController.startLoop(config, fd)`,
  входящий inbound — `{"protocol": "tun"}` со сниффингом http/tls/quic.
- **WireGuard.** Официальный `com.wireguard.android:tunnel` (wireguard-go):
  бэкенд сам стартует свой VpnService (объявлен в манифесте как
  `GoBackend$VpnService`), конфиг парсится из wg-quick текста.
- **OpenVPN.** Ядро `libovpnexec.so` запускается дочерним процессом
  (`ProcessBuilder`, как в ics-openvpn); приложение слушает management-сокет,
  отвечает на запросы `IFCONFIG`/`ROUTE`/`DNSSERVER`, создаёт TUN через
  `establish()` и передаёт fd в ответе `needok 'OPENTUN' ok` через
  SCM_RIGHTS. Статистика — `>BYTECOUNT:in,out` management-сообщения.
- Статистика: Xray отдаёт счётчики через `queryAllOutboundTrafficStats()`,
  WireGuard — через `GoBackend.getStatistics()`.

## Ограничения v0.1

- Задержка/тест профилей: доступен через `measureOutboundDelay`, в UI пока не выведен.
- Нет per-app прокси и kill-switch (planned).
- OpenVPN: не поддерживаются конфиги с external-файлами (cert/key/ca в
  отдельных файлах — только inline), `http-proxy` игнорируется, TAP не
  поддерживается (Android умеет только TUN), авторизация — только
  статический логин/пароль (стерео-токены/OTP не выводятся в UI).
- Hysteria2/TUIC потребовали бы sing-box; архитектура движков это позволяет
  добавить третьим ядром.

## Известные особенности сети

Если Gradle падает с `(bad_record_mac) Tag mismatch` при скачивании — это
нестабильный канал к repo.maven.apache.org; в `settings.gradle.kts` подключено
зеркало `maven-central.storage-download.googleapis.com`, а в `gradle.properties`
принудителен TLS 1.2. Повторная сборка подхватывает уже скачанное из кэша.
