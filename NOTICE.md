# Сторонние компоненты

Код CrystalN распространяется под MIT (см. LICENSE). Приложение использует
сторонние ядра и библиотеки — у каждого своя лицензия:

| Компонент | Лицензия | Как используется |
|---|---|---|
| [Xray-core](https://github.com/XTLS/Xray-core) | MPL-2.0 | отдельный исполняемый файл (`xray`), запускается как процесс |
| [tun2socks](https://github.com/xjasonlyu/tun2socks) | MIT | отдельный бинарник (TUN-режим) |
| [amneziawg-go](https://github.com/amnezia-vpn/amneziawg-go) | MIT | отдельный бинарник / AAR |
| [amneziawg-tools](https://github.com/amnezia-vpn/amneziawg-tools) | GPL-2.0 | `awg` — отдельный бинарник |
| [OpenVPN](https://github.com/OpenVPN/openvpn) | GPL-2.0 | отдельное ядро (`libovpnexec.so`), собирается из исходников скриптом `tools/build_openvpn_core.sh` |
| [wireguard-android](https://git.zx2c4.com/wireguard-android) | Apache-2.0 | Gradle-зависимость |
| [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) | Apache-2.0 | UI |
| [Kotlin](https://kotlinlang.org) | Apache-2.0 | язык/рантайм |
| zxing | Apache-2.0 | QR-коды |
| org.json | JSON License | работа с JSON |

Ядра Xray, tun2socks, amneziawg и OpenVPN запускаются как **отдельные
процессы** и не линкуются в код приложения; их бинарники скачиваются из
официальных релизов или собираются из исходников при сборке. Исходники
GPL-компонентов доступны по ссылкам выше.
