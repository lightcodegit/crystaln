# Участие в разработке

## Требования

- JDK 17+ (Android) и JDK 21 (модуль `desktop`, toolchain)
- Android SDK, платформа 35
- Linux: `mksquashfs` (squashfs-tools) — для AppImage

## Быстрый старт

```bash
tools/fetch_cores.sh          # ядро Xray (один раз)
./gradlew :app:testDebugUnitTest :desktop:test
./gradlew :desktop:run        # запуск Linux-версии
```

## Версии

Единственный источник версии — `appVersion` / `appVersionCode` в
`gradle.properties`. Их используют Android `versionName`, Linux
`packageVersion`/AppImage и проверка обновлений. Меняйте только там.

## Стиль

- Kotlin, официальный стиль (`kotlin.code.style=official`).
- Комментарии — по-русски, только там, где объясняют «почему», а не «что».
- Новое поведение — с unit-тестом (парсеры, конфиги, хранилища).

## Коммиты

Формат: `тип(область): краткое описание` — например
`fix(desktop): сокет root-хелпера доступен пользователю`.
Типы: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`.

## Релизы

1. Поднимите `appVersion` (и `appVersionCode` для Android) в `gradle.properties`.
2. `git tag v0.2.0 && git push origin v0.2.0`
3. Workflow `Release` соберёт APK, AppImage, rpm, deb и опубликует релиз —
   после этого приложение увидит обновление.
