# Проект: gameconnect

Доработка Capacitor-плагина игровых сервисов для игры **Adventure Mazes**
(репозиторий игры: `../adventuremazes` = `/home/deployagent/projects/adventuremazes`,
web + Capacitor Android / Android TV).
Здесь живёт форк плагина и всё, что мы в него добавляем — прежде всего **облачные
сохранения (Google Play Games Saved Games / Snapshots)**.

## Что за плагин

| | |
|---|---|
| Upstream | https://github.com/Visnalize/capacitor-game-connect (`main`, тег `8.0.0`) |
| Пакет | `@openforge/capacitor-game-connect` (в README/коде — OpenForge LLC, MIT) |
| Имя плагина в Capacitor | `CapacitorGameConnect` |
| Версия SDK | Capacitor 8 (`@capacitor/core ^8.0.0`) |
| Android | Java 17, `minSdk 24`, `compileSdk 36`, `com.google.android.gms:play-services-games-v2:+` |
| iOS | Swift, GameKit (`GKAchievement`, `GKLeaderboard`, `GKGameCenterViewController`) |

Текущий API (8 методов): `signIn`, `showLeaderboard`, `showAllLeaderboards`,
`submitScore`, `showAchievements`, `unlockAchievement`,
`incrementAchievementProgress`, `getUserTotalScore`.

Чего в нём **нет** и что мы добавляем: сохранения в облаке — загрузка текущего сейва,
запись, удаление, список слотов, штатный экран выбора сохранения.

## Структура upstream (куда лезть)

```
src/definitions.ts        # единственное место, где объявлен TS-API (по нему docgen делает README)
src/index.ts              # registerPlugin('CapacitorGameConnect')
src/web.ts                # web-заглушки — обязательны для каждого нового метода
android/src/main/java/com/openforge/capacitorgameconnect/
    CapacitorGameConnectPlugin.java   # @PluginMethod обёртки, ActivityResultLauncher
    CapacitorGameConnect.java         # вся логика Play Games v2
    SignInCallback.java / PlayerResultCallback.java
ios/Plugin/CapacitorGameConnect.swift # GameKit
```

## Документация по теме (локально, часть анализа)

- `docs/cloud-save-research.md` — что нужно донастроить в Play Console / Cloud /
  Android-проекте, лимиты, оценка сложности, предложенный API и план работ.
- Ключевые внешние ссылки:
  - https://developer.android.com/games/pgs/savedgames — Cloud save (концепция, лимиты 3 МБ)
  - https://developer.android.com/games/pgs/android/saved-games — SnapshotsClient, конфликты
  - https://developer.android.com/games/pgs/console/enable-features#enabling-saved-games — включить Saved Games
  - https://developer.android.com/games/pgs/quality#saved-games — чек-лист качества

## Окружение этого сервера (важно)

VPS со студенческим стендом (см. `../students/AGENTS.md`), **не** дев-машина:

- нет `node`/`npm`, нет `java`/`gradle`, нет Android SDK; `sudo` у `deployagent` нет;
- поэтому здесь можно править код, TS-типы и документацию, но **собрать плагин
  (`npm run build`, `gradlew`) и проверить на устройстве нельзя** — это делается
  на машине с Android Studio / на телефоне или ТВ-приставке.
- при желании node ставится без sudo в `~` (официальный тарбол с nodejs.org),
  но JDK + Android SDK тянуть на этот VPS смысла нет.

Исходники upstream уже лежат **в корне этого репозитория** (версия `8.0.0`,
upstream-коммит `fbcc813`), наши добавления — `AGENTS.md` и `docs/`. Обновление
с upstream:

```bash
cd /home/deployagent/projects/gameconnect
git fetch upstream                     # remote добавлен при вендоринге
git log --oneline HEAD..upstream/main  # что нового
git merge upstream/main                # или cherry-pick нужного
```

Node/JDK на этом VPS нет, поэтому `npm ci` / `npm run build` здесь не запускаются.

## Правила

1. **Совместимость:** существующие 8 методов не ломать — только добавлять.
   Изменение сигнатур = major-версия.
2. **Каждый новый метод = 4 места:** `src/definitions.ts`, `src/web.ts` (заглушка),
   `CapacitorGameConnect.java` (логика), `CapacitorGameConnectPlugin.java` (`@PluginMethod`).
3. **Асинхронность:** `call.resolve()/reject()` только по завершении `Task` Play Games.
   В upstream часть методов (`submitScore`, `unlockAchievement`) резолвится мгновенно —
   для сейвов так делать нельзя, иначе игра не узнает об ошибке.
4. **Авторизация:** методы сейвов сначала проверяют `isAuthenticated()` (иначе понятный
   reject), не полагаться на то, что игра уже вызвала `signIn`.
5. **README генерируется** из `definitions.ts` (`npm run docgen`) — руками не править.
6. **Тест только на подписанной сборке** с тем же SHA-1, что в Play App Signing,
   и под тестовым аккаунтом из Play Console.
