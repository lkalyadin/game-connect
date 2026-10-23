# Проект: gameconnect

Доработка Capacitor-плагина игровых сервисов для игры **Adventure Mazes**
(репозиторий игры: `../adventuremazes` = `/home/deployagent/projects/adventuremazes`,
web + Capacitor Android / Android TV).
Здесь живёт форк плагина и всё, что мы в него добавляем — прежде всего **облачные
сохранения (Google Play Games Saved Games / Snapshots)**.

## Где работает агент (VPS vs компьютер владельца)

Агент выполняет команды **на VPS** (`94.141.97.249`, Ubuntu, пользователь `deployagent`),
а не на компьютере владельца. Серверные пути и инструменты (`/home/deployagent/projects/...`,
bash, `sg docker`, systemd-таймеры) — это нормальная среда агента, а не его машина.

Компьютер владельца — **Windows 11**, на нём уже стоят **Node.js, JDK и Android SDK / Android Studio**.
Отсюда правила для любых инструкций, которые мы пишем владельцу:

- писать **под Windows**: PowerShell (или cmd), пути вида `C:\...` и `%USERPROFILE%`,
  `npx.cmd`/`npx`, `copy`/`xcopy`/`robocopy`; без `sudo`, `chmod`, `export`, `$(...)`,
  bash-цепочек `&&` и `VAR=value command`, если не дан эквивалент для Windows;
- у каждого блока команд помечать, где он выполняется — «на VPS» или «на Windows»;
  не смешивать серверные и локальные шаги в одном неподписанном блоке;
- сборка плагина и приложения, `npm pack`, `gradlew`, Android Studio, `adb` и тесты
  на устройстве — **на машине владельца**; на VPS нет JDK и Android SDK, здесь доступны
  только проверки JS-уровня (`tsc`, `rollup`, node-скрипты) и сверка исходников;
- файл, созданный агентом на VPS, сам к владельцу не попадёт: передача — через git
  (`push` / `pull`) или `scp` из PowerShell. Не давать владельцу серверные пути вроде
  `/tmp/opencode/...` как будто они есть у него локально;
- `/home/deployagent/projects/gameconnect` (клон на VPS) и локальный клон владельца —
  **разные копии**, синхронизация только через git.

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

## Репозиторий и ветки

| | |
|---|---|
| `origin` | `git@github-gameconnect:lkalyadin/game-connect.git` (приватный) |
| `upstream` | `https://github.com/Visnalize/capacitor-game-connect.git` (только чтение) |
| `main` | вендоренный upstream `8.0.0` + наши `AGENTS.md`/`docs/`, **кода сейвов тут нет** |
| `feat/cloud-save` | облачные сохранения (активная рабочая ветка) |

`github-gameconnect` — алиас из `~/.ssh/config` (ключ `~/.ssh/id_ed25519_gameconnect`,
на GitHub это deploy key с write-доступом только к этому репозиторию). Ключ от
`adventuremazes` переиспользовать нельзя: GitHub не разрешает один deploy key
на разные репозитории.

Обычный цикл: работаем в `feat/cloud-save` → `git push` → PR/мерж в `main` на GitHub.
`main` при этом остаётся базой форка, чтобы правки можно было сопоставлять с upstream.

## Структура upstream (куда лезть)

```
src/definitions.ts        # единственное место, где объявлен TS-API (по нему docgen делает README)
src/index.ts              # registerPlugin('CapacitorGameConnect')
src/web.ts                # web-заглушки — обязательны для каждого нового метода
src/interfaces/           # типы (player-score, user, saved-game)
android/src/main/java/com/openforge/capacitorgameconnect/
    CapacitorGameConnectPlugin.java   # @PluginMethod обёртки, ActivityResultLauncher, @ActivityCallback
    CapacitorGameConnect.java         # ачивки, лидерборды, вход
    SavedGames.java                   # облачные сохранения (snapshots)
    SignInCallback.java / PlayerResultCallback.java
ios/Plugin/CapacitorGameConnect.swift # GameKit (сохранений пока нет)
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

- `java`/`gradle`/Android SDK нет, `sudo` у `deployagent` нет → **Android-часть здесь
  не собирается и не проверяется на устройстве**.
- node/npm поставил агент без sudo: `/home/deployagent/.local/node22/bin` (Node 22).
  Пользоваться так:

```bash
cd /home/deployagent/projects/gameconnect
export PATH=/home/deployagent/.local/node22/bin:$PATH
npm install          # один раз (package-lock в upstream был устаревший — обновлён)
npm run build        # clean + docgen (README) + tsc + rollup
npx tsc --noEmit     # быстрая проверка типов
npx prettier --check "src/**/*.ts"
```

- Java проверяется только глазами и `javac` из `/tmp/opencode/jdk` (без Android SDK
  компиляция невозможна); реальные сигнатуры GMS сверены через `javap` по
  `play-services-games-v2-22.1.0`.

Исходники upstream уже лежат **в корне этого репозитория** (версия `8.0.0`,
upstream-коммит `fbcc813`), наши добавления — `AGENTS.md` и `docs/`.

**Важно:** вендоринг сделан снимком файлов, **общей истории с upstream нет**
(`git merge-base HEAD upstream/main` пуст). Поэтому `git merge upstream/main`
не сработает («refusing to merge unrelated histories»), а `git diff` сравнивает
только содержимое файлов. Обновление с upstream:

```bash
cd /home/deployagent/projects/gameconnect
git fetch upstream
git log --oneline HEAD..upstream/main       # что нового в upstream
git diff HEAD upstream/main --stat          # какие файлы разошлись
# переносить точечно, чтобы не потерять наши правки:
git checkout upstream/main -- src/web.ts    # пример: взять файл из upstream
```

Если понадобится полноценная история upstream, лучше один раз пересоздать
репозиторий от `upstream/main` и перенести поверх наши коммиты, чем тянуть
`--allow-unrelated-histories`.

## Текущее состояние

Ветка `feat/cloud-save`: добавлены облачные сохранения (Google Play Games Saved Games).

| Что | Где |
|---|---|
| Типы | `src/interfaces/saved-game.interface.ts` |
| API (6 методов) | `src/definitions.ts` → README генерируется docgen'ом |
| Web-fallback (localStorage) | `src/web.ts` |
| Android-логика | `android/.../SavedGames.java` |
| Обёртки + экран выбора сейва | `android/.../CapacitorGameConnectPlugin.java` |
| Разбор и решения | `docs/cloud-save-research.md` § 5 |

Ещё не сделано: iOS (методы объявлены, в Swift их нет), сборка под Android,
включение Saved Games в Play Console, проверка на телефоне/ТВ, интеграция в игру.

**Сверка с upstream** (23.10.2026, `bash scripts/verify-upstream-identical.sh`):
код ачивок/лидербордов совпадает с `upstream/main` (fbcc813) побайтово
(`CapacitorGameConnect.java`, оба callback'а, `build.gradle`, `AndroidManifest.xml`,
`src/index.ts`, интерфейсы), а в `CapacitorGameConnectPlugin.java`, `src/definitions.ts`
и `src/web.ts` **нет ни одной удалённой или изменённой строки** — только добавления.
Сборка JS из исходников upstream той же цепочкой даёт идентичный `dist/esm/index.js`
и `web.js`/`plugin.cjs.js`, отличающиеся только добавленными методами.

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
7. **Открытый снапшот нельзя оставлять**: `loadSavedGame` после чтения делает
   `discardAndClose`, `saveSavedGame`/`resolveSavedGameConflict` — `commitAndClose`.
   Иначе следующий `open()` падает с «snapshot already open».
