# Безопасность

## Как сообщить об уязвимости

Не открывайте публичный issue. Напишите через GitHub Security Advisory
(вкладка **Security → Report a vulnerability**) или приватно владельцу
репозитория.

## Что важно знать о привилегиях (Linux)

Для TUN-режима приложение ставит root-хелпер через systemd:

- `crystaln-helper.socket` слушает `/run/crystaln-helper.sock`, сокет доступен
  на запись всем (`0666`), но сервис принимает соединение только от uid,
  записанного в `/etc/crystaln-helper.uid` (проверка `SO_PEERCRED`).
- Хелпер выполняет shell-скрипты от root. Любой процесс под **вашим**
  пользователем может попросить его выполнить команду — это осознанный
  компромисс, тот же, что у десктопных VPN-клиентов с root-демоном.
- Удалить хелпер: `sudo systemctl disable --now crystaln-helper.socket` и
  удалить `/etc/systemd/system/crystaln-helper.{socket,@.service}`,
  `/usr/lib/crystaln/`, `/etc/crystaln-helper.uid`, `/etc/crystaln-helper.version`.

## Данные

- Профили: `~/.config/crystaln` (Linux), внутреннее хранилище (Android).
- Экспорт-«сейф»: AES-256-GCM, PBKDF2-HMAC-SHA256, 180 000 итераций.
- Телеметрии и аналитики нет. Сеть используется только для: загрузки ядер,
  подписок, диагностики и проверки релизов на GitHub.
