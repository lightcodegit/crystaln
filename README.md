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

## Linux-версия

Desktop-вариант в модуле `desktop` — то же ядро (профили, парсеры, генератор
конфигов Xray) под чистым JVM, Compose Desktop GUI, раннеры внешних процессов.
Интерфейс — полный перенос Android-версии: liquid-glass дизайн, палитры,
главный экран с кольцом подключения, редактор профилей, настройки, подписки,
диагностика, шеринг с QR-кодом и статистика трафика.

- **Режимы:** локальный прокси (SOCKS+HTTP, без root) и полный TUN через
  tun2socks (нужны права root — скрипты выполняются через `sudo -n` или
  `pkexec`; само приложение root-прав не требует).
  Пароль запрашивается один раз: при первом privileged-вызове приложение
  ставит systemd-сервис-хелпер (`crystaln-helper.socket` +
  `crystaln-helper@.service`, скрипт в `/usr/lib/crystaln`, доступ
  ограничен uid установщика через SO_PEERCRED). После этого все
  root-операции (подключение, отключение, смена сети) идут через сервис
  без диалогов — как в AmneziaVPN. Фолбэк — сессионный хелпер через
  `pkexec` (если сервиса нет или systemd не подходит).
  Удаление сервиса: `sudo systemctl disable --now crystaln-helper.socket &&
  sudo rm /etc/systemd/system/crystaln-helper.{socket,@.service}
  /usr/lib/crystaln/crystaln-helper.py /etc/crystaln-helper.uid &&
  sudo systemctl daemon-reload`.
- **Протоколы:** Xray-семейство (VMess/VLESS/Trojan/Shadowsocks/SOCKS/HTTP)
  через официальный xray-core, WireGuard через системный `wg-quick`
  (пакет `wireguard-tools`) либо, при его отсутствии, через тот же
  WireGuard-совместимый `amneziawg-go`. OpenVPN через системный `openvpn`.
  AmneziaWG работает в userspace через `amneziawg-go` и `awg setconf`
  (модуль ядра не нужен); поддерживаются junk-параметры, DNS и AllowedIPs.
- **Бинарники** (`xray`, `tun2socks`, `amneziawg-go`, `awg`, `geosite.dat`)
  скачиваются в `~/.local/share/crystaln/bin` кнопкой «Обновить бинарники»
  или при первом использовании. amneziawg-go собирается из официального
  upstream для linux/amd64 и linux/arm64 в публичном репозитории
  `lightcodegit/crystaln-binaries` (исходники клиента там не публикуются).
- **Данные:** конфиги в `~/.config/crystaln` (profiles.json, subscriptions.json,
  settings.json — форматы как на Android), журнал в
  `~/.local/share/crystaln/logs`. При закрытии окна дочерние процессы
  останавливаются автоматически.

```sh
export JAVA_HOME=/путь/к/jdk-21

# Тесты и сборка
./gradlew :desktop:test :desktop:build

# Запуск окна
./gradlew :desktop:run

# deb-пакет (нужен dpkg-deb: Debian/Ubuntu) и rpm (нужен rpmbuild: Fedora)
./gradlew :desktop:packageDistributionForCurrentOS
# только rpm (Fedora): jpackage Oracle-JDK не умеет deb без dpkg
./gradlew :desktop:packageRpm
```

Готовый пакет: `desktop/build/compose/binaries/main/rpm/crystaln-1.0.0-1.x86_64.rpm`.
Установка на Fedora: `sudo dnf install ./crystaln-1.0.0-1.x86_64.rpm` —
приложение ставится в `/opt/crystaln` и появляется в меню приложений
(jpackage регистрирует ярлык через `xdg-desktop-menu`); в комплекте
свой JRE-runtime, системный JDK не нужен.

## Импорт серверов

- Кнопка «импорт» внизу справа: читает буфер обмена и парсит построчно
  `vless://`, `trojan://`, `ss://` (SIP002 и legacy), `vmess://` (v2rayN),
  `socks5://`, `http(s)://`.
- WireGuard: создайте профиль WireGuard и вставьте готовый wg-quick конфиг
  (PrivateKey/Address/DNS + [Peer] PublicKey/Endpoint/AllowedIPs).
- OpenVPN: вставьте готовый `.ovpn` конфиг (сертификаты должны быть встроены
  в текст: `<ca>`, `<cert>`, `<key>`) в редакторе профиля; либо просто
  импортируйте текст конфига из буфера обмена.

## Подписки

Сохранённые URL (HTTPS) в настройках: при добавлении подписка сразу
загружается, серверы сливаются со списком без дублей (идентичность сервера =
протокол + хост + порт + учётные данные). Фоновое обновление всех включённых
подписок — раз в 6 часов через WorkManager; удаление подписки удаляет только
её серверы, вручную добавленные профили не трогаются. Повторный импорт тех
же ссылок обновляет существующие профили вместо создания дубликатов.

## Диагностика

Настройки → «Диагностика»: DNS-резолв и TCP-подключение к серверу выбранного
профиля (с таймингами) плюс offline-проверка полноты конфигурации (uuid,
пароли, PrivateKey/PublicKey/Endpoint для WG, remote/`<ca>` для OpenVPN,
REALITY pbk/shortId). Проверки не устанавливают VPN и безопасны при активном
туннеле.

## Повседневные удобства

- Плитка Quick Settings: один тап подключает/отключает выбранный профиль;
  если VPN-разрешение ещё не выдано — открывается приложение.
- Адаптивная иконка (API 26+) с monochrome-слоем для тематических иконок
  Android 13+.
- Автоподключение выбранного профиля при запуске и после перезагрузки;
  опция «автовыбор самого быстрого»: перед автоподключением пингуются все
  серверы (TCP connect, параллельно) и берётся самый отзывчивый живой.
- Кнопка «проверить все» в шапке списка — задержки всех серверов разом.
- Мягкий перезапуск туннелей Xray-семейства после смены сети (Wi-Fi ↔
  мобильная): соединения умирают молча, WireGuard/AWG переживают это
  собственным watchdog'ом по stale-handshake, VLESS/VMess/Trojan —
  перезапуском с дебаунсом 4 с и лимитом 30 с.
- Статистика по профилям: накопленные байты RX/TX и время сессий
  сохраняются per-profile (JSON), видно в карточке («Всего: 1.2 ГБ»).

## Anti-leak: DNS и маршрутизация

Настройки → «Приватность» (применяется к профилям Xray-семейства):

- **DNS через туннель** — порт 53 внутри TUN перехватывается правилом
  маршрутизации и уходит в outbound `dns-out` через прокси, провайдер не
  видит DNS-запросы; апстрим настраивается (по умолчанию 1.1.1.1).
- **Блокировка рекламы** — правило `geosite:category-ads-all → block`:
  рекламные и трекерские домены режектятся до выхода с устройства. База
  geosite (v2fly dlc.dat) скачивается один раз при включении; неудачная
  загрузка откатывает переключатель — настройка не врёт о состоянии.

## Поделиться профилем

Кнопка Share на карточке: QR-код (zxing) и текст ссылки — v2ray-ссылки
(vless/trojan/vmess/ss/socks/http) для Xray-семейства, сырой wg-quick и
.ovpn текст для WireGuard/AWG/OpenVPN. Обратная сериализация покрыта
roundtrip-тестами через собственный парсер: что экспортирует приложение,
оно же может импортировать. Плюс поиск по списку (имя/адрес/протокол) и
группировка профилей по подпискам, вручную добавленные — отдельная группа.

## Виджет

Домашний экран: статус туннеля (подключено / подключение / выключено) и
тумблер одним тапом — политика та же, что у QS-плитки: при уже выданном
VPN-разрешении подключение идёт прямо из виджета, иначе открывается
приложение. Обновляется на каждой смене состояния (VpnManager шлёт redraw)
и при перерисовке лаунчером.

## Экспорт и защита профилей

Экспорт — из меню импорта. Два режима: открытый JSON и «сейф» — тот же
экспорт, зашифрованный паролем (AES-256-GCM, ключ PBKDF2-HMAC-SHA256,
180 000 итераций, случайные соль и IV). Конверт самописываемый: при импорте
такого файла приложение само запрашивает пароль; неверный пароль и
повреждённый файл дают одинаковую ошибку без деталей.

С версии crystaln/2 в бэкап попадает не только список профилей, но и
подписки (URL, флаги, интервал обновления) и настройки приложения (тема,
автоподключение, приватность и т.д.). Импорт восстанавливает всё через
обычные механизмы слияния — дубликаты не плодятся. Обои-изображение в
бэкап не входит (бинарный файл).

## Архитектура

```
ui/            Compose-free Material UI: список профилей, статус, редактор
data/          Профили (JSON-файл в filesDir), парсер share-ссылок,
               подписки, диагностика, зашифрованный экспорт (vault)
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

## Ограничения v0.2

- Нет per-app прокси и kill-switch (planned).
- VPN/TUN-сценарии проверяются только на реальном устройстве: unit-тесты
  покрывают конфиги, парсеры и хранилища, но не поднимают туннель.
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
