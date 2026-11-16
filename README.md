<div align="center">

<img src="desktop/src/main/resources/crystaln/icon.png" width="128" alt="CrystalN">

# CrystalN

**Мультипротокольный VPN-клиент для Android и Linux**

Один процесс ядра, живая статистика трафика, умный выбор сервера,
защита от рекламы — без аналитики и телеметрии.

[Протоколы](#поддерживаемые-протоколы) · [Установка](#установка) · [Сборка](#сборка) · [Архитектура](#архитектура)

</div>

---

## Поддерживаемые протоколы

| Протокол | Ядро | Транспорты / фичи |
|---|---|---|
| VMess | Xray | tcp, ws, gRPC, httpupgrade, xhttp, kcp, quic, h2 |
| VLESS | Xray | те же + flow (xtls-rprx-vision) |
| Trojan | Xray | TLS, все транспорты |
| Shadowsocks | Xray | все методы ядра |
| SOCKS5 / HTTP | Xray | с авторизацией и без |
| WireGuard | amneziawg-go | wg-quick конфиг, AllowedIPs, DNS |
| AmneziaWG | amneziawg-go | junk-параметры Jc/Jmin/Jmax/S1/S2/H1–H4 |
| OpenVPN | openvpn 2.6 | .ovpn конфиг, UDP/TCP, инлайн-сертификаты, auth-user-pass |

TLS и REALITY настраиваются для любого Xray-профиля (SNI, uTLS fingerprint,
ALPN, publicKey/shortId/spiderX).

## Возможности

- 🪄 **Первый запуск без борьбы** — короткий мастер настройки: приветствие →
  загрузка ядер (Linux) и импорт первого профиля → подключение.
- 🌐 **Два режима** — локальный прокси (SOCKS+HTTP, без root) и полный TUN
  через tun2socks.
- 🚀 **Автовыбор быстрейшего сервера** — перед автоподключением пингуются все
  серверы и берётся самый живой.
- 🛡️ **Anti-leak** — DNS через туннель и блокировка рекламы
  (`geosite:category-ads-all → block`).
- 📊 **Статистика** — трафик и время по каждому профилю, счётчики в карточке.
- 🩺 **Диагностика** — DNS, доступность сервера (TCP для TCP-протоколов,
  ICMP-ping для UDP-протоколов) и offline-проверка конфига.
- 🔐 **Экспорт-«сейф»** — AES-256-GCM бэкап профилей, подписок и настроек с
  паролем; при импорте пароль запрашивается автоматически.
- 🔄 **Обновления** — приложение сверяется с GitHub Releases и предлагает
  открыть страницу релиза, когда вышла новая версия.

## Установка

### Android

Скачайте `crystaln-*-debug.apk` из [Releases](https://github.com/lightcodegit/crystaln/releases/latest)
(universal — работает на любом телефоне) или соберите сами (см. [Сборка](#сборка))
и установите. Понадобится Android 8+.

### Linux

Три способа — выбирайте свой:

**AppImage — универсально, без установки** (любой дистрибутив):

```bash
chmod +x crystaln-0.1.0-x86_64.AppImage
./crystaln-0.1.0-x86_64.AppImage
```

> Нужен только `libfuse2` (или запуск с `APPIMAGE_EXTRACT_AND_RUN=1`).

**Gear Lever** (GNOME, Flathub) — откройте AppImage кнопкой «Открыть»: он сам
перенесёт приложение в свою папку, создаст ярлык в меню и будет следить за
обновлениями. Метаданные интеграции уже встроены в AppImage.

**Скрипт-setup** — работает на любом дистрибутиве, без root:

```bash
tools/setup.sh install                    # в ~/.local (без root)
tools/setup.sh install app.AppImage --system  # системно (sudo)
tools/setup.sh uninstall                  # удалить; профили сохраняются
```

**Windows** — установщик `.msi`:

```bat
gradlew.bat :desktop:packageMsi
```

Ставится в Program Files, ярлык в меню «Пуск». На Windows доступны все
режимы, как на Linux:

- **Прокси** — SOCKS `127.0.0.1:10808` / HTTP `127.0.0.1:10809` через xray.
- **TUN** — tun2socks + wintun: весь трафик системы через VPN. Права
  администратора запрашиваются один раз за сессию (один UAC-запрос).
- **WireGuard / AmneziaWG / OpenVPN** — туннель поднимается как
  Windows-сервис (`amneziawg.exe /installtunnelservice`), OpenVPN запускается
  с драйвером wintun. Все бинарники (включая wintun.dll, amneziawg.exe и
  openvpn.exe) скачиваются кнопкой «Обновить бинарники» и работают
  портативно — сторонний софт устанавливать не нужно.

Только на Windows: иконка в системном трее со статусом подключения
(серая точка — VPN выключен, зелёная — подключено; закрытие окна
сворачивает приложение, VPN продолжает работать; правый клик — меню
с подключением, отключением, статусом и выходом), уведомления Windows
при подключении / отключении VPN и автозагрузка при входе в систему
(«Настройки → Подключение → Запускать при входе в систему», запись
в HKCU Run).

**rpm / deb** — классические пакеты (в комплекте свой JRE):

```bash
sudo dnf install ./crystaln-0.1.0-1.x86_64.rpm   # Fedora
```

### Root на Linux — один раз, навсегда

TUN-режиму нужны права root. При первом подключении приложение ставит
**systemd-сервис-хелпер** (как в AmneziaVPN): пароль запрашивается один раз,
затем все подключение/отключение/смена сети идут через сервис без диалогов.
Доступ к сервису ограничен uid'ом установщика (SO_PEERCRED).

<details>
<summary>Как вручную удалить root-хелпер</summary>

```bash
sudo systemctl disable --now crystaln-helper.socket
sudo rm /etc/systemd/system/crystaln-helper.{socket,@.service} \
        /usr/lib/crystaln/crystaln-helper.py /etc/crystaln-helper.uid
sudo systemctl daemon-reload
```

</details>

## Сборка

Требования: JDK 21 (toolchain), Android SDK (платформа 35) — для Android-версии.
Desktop-модуль собирается на **Linux, Windows и macOS**.

```bash
# 1. Скачать ядро Xray (один раз; WireGuard придёт сам из Maven Central)
tools/fetch_cores.sh              # Windows: bash tools/fetch_cores.sh

# 2. Android: debug-APK
./gradlew assembleDebug            # → app/build/outputs/apk/debug/

# Linux (desktop): запуск окна
./gradlew :desktop:run

# Linux: пакеты
./gradlew :desktop:packageAppImage                    # AppImage (любой Linux)
./gradlew :desktop:packageRpm                         # rpm (нужен rpmbuild)
./gradlew :desktop:packageDistributionForCurrentOS    # deb+rpm (deb нужен dpkg-deb)

# Windows: установщик msi (gradlew.bat на Windows)
./gradlew :desktop:packageMsi

# Тесты
./gradlew :app:testDebugUnitTest :desktop:test
```

<details>
<summary>Ядра: сборка AmneziaWG и OpenVPN для Android</summary>

```bash
bash tools/build_awg_core.sh          # нужны Go 1.25+ и Android NDK → AAR
bash tools/build_openvpn_core.sh      # нужен NDK; OpenSSL/LZO/LZ4 линкуются статически
```

</details>

## Импорт серверов

- Кнопка «импорт»: буфер обмена, файл, QR-код или ссылка.
  Парсит построчно `vless://`, `trojan://`, `ss://` (SIP002 и legacy),
  `vmess://` (v2rayN), `socks5://`, `http(s)://`.
- **WireGuard/AWG** — вставьте готовый wg-quick конфиг
  (`PrivateKey/Address/DNS` + `[Peer]`).
- **OpenVPN** — вставьте `.ovpn` текст (сертификаты должны быть inline:
  `<ca>`, `<cert>`, `<key>`).
- **Подписки** — HTTPS-URL в настройках; фоновое обновление раз в 6 часов,
  слияние без дублей, удаление подписки не трогает ручные профили.
- Повторный импорт тех же ссылок обновляет существующие профили.

## Особенности платформ

### Linux

- Протоколы: Xray-семейство через официальный xray-core; WireGuard через
  системный `wg-quick` или userspace `amneziawg-go`; AmneziaWG — userspace
  (`amneziawg-go` + `awg setconf`, модуль ядра не нужен); OpenVPN через
  системный `openvpn`.
- Бинарники (`xray`, `tun2socks`, `amneziawg-go`, `awg`, `geosite.dat`)
  скачиваются в `~/.local/share/crystaln/bin` кнопкой «Обновить бинарники».
- Данные: конфиги в `~/.config/crystaln`, журнал в
  `~/.local/share/crystaln/logs`. Дочерние процессы останавливаются при
  закрытии окна.
- Кнопка «назад» окна и привычная навигация как на Android; при первом
  запуске — мастер настройки.

### Android

- Плитка Quick Settings и домашний виджет: тумблер одним тапом.
- Адаптивная иконка (API 26+) с monochrome-слоем для Android 13+.
- Мягкий перезапуск туннелей Xray после смены сети (Wi-Fi ↔ мобильная);
  WireGuard/AWG переживают смену собственным watchdog'ом.
- Кнопка назад ходит по истории экранов (редактор ← настройки ← главный).

## Архитектура

```
ui/            Material UI: список профилей, статус, редактор
data/          Профили (JSON), парсер share-ссылок, подписки,
               диагностика, зашифрованный экспорт (vault), проверка обновлений
service/       CrystalVpnService — VpnService для семейства Xray
wg/            WireGuardTunnel — официальный GoBackend (свой VpnService)
xray/          Генератор конфига Xray + обёртка над libv2ray
net/           (desktop) TunnelManager, привилегированный root-хелпер
```

<details>
<summary>Как это работает внутри</summary>

- **Xray-семейство.** Сервис поднимает TUN (`establish()`), свой пакет
  исключён из VPN (`addDisallowedApplication`) — трафик ядра не зацикливается.
  fd передаётся в `CoreController.startLoop(config, fd)`; inbound —
  `{"protocol": "tun"}` со сниффингом http/tls/quic.
- **WireGuard.** Официальный `com.wireguard.android:tunnel` (wireguard-go):
  бэкенд сам стартует свой VpnService (`GoBackend$VpnService`), конфиг —
  из wg-quick текста.
- **OpenVPN.** Ядро `libovpnexec.so` — дочерний процесс (как в ics-openvpn);
  приложение слушает management-сокет, отвечает `IFCONFIG`/`ROUTE`/`DNSSERVER`,
  передаёт fd через SCM_RIGHTS (`needok 'OPENTUN' ok`). Статистика —
  `>BYTECOUNT`.
- **Linux root-хелпер.** systemd socket-активация: `crystaln-helper.socket`
  слушает `/run/crystaln-helper.sock`, на каждое соединение стартует root-процесс
  `crystaln-helper@.service`; приложение шлёт base64-скрипты, получает код
  возврата и вывод. Проверка SO_PEERCRED против `/etc/crystaln-helper.uid`.

</details>

## Ограничения

- Нет per-app прокси и kill-switch (planned).
- VPN/TUN-сценарии проверяются на реальных устройствах: unit-тесты покрывают
  конфиги, парсеры и хранилища, но не поднимают туннель.
- OpenVPN: только inline-сертификаты, `http-proxy` игнорируется, TAP не
  поддерживается на Android.
- Hysteria2/TUIC потребовали бы sing-box — архитектура движков позволяет
  добавить третьим ядром.

## Известные особенности сети

Если Gradle падает с `(bad_record_mac) Tag mismatch` при скачивании — это
нестабильный канал к repo.maven.apache.org; в `settings.gradle.kts` подключено
зеркало `maven-central.storage-download.googleapis.com`, а в `gradle.properties`
принудителен TLS 1.2. Повторная сборка подхватывает уже скачанное из кэша.

---

<div align="center">

CrystalN · v0.1.0 · без аналитики, без телеметрии

</div>

## Лицензия

Код проекта — [MIT](LICENSE). Сторонние ядра и библиотеки (Xray, tun2socks,
amneziawg, OpenVPN, Compose) распространяются под своими лицензиями —
см. [NOTICE.md](NOTICE.md).
