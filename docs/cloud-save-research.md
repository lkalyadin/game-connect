# Облачные сохранения в capacitor-game-connect: что нужно и насколько сложно

Дата анализа: 2026-10-23. Изучены upstream-код плагина (тег `8.0.0`) и документация
Google Play Games Services (страницы обновлены 2026-10-06).

## 1. Короткий ответ

| Вопрос | Ответ |
|---|---|
| Нужны ли новые настройки в Google Cloud? | **Нет.** Saved Games работают через тот же проект Play Games Services: тот же OAuth-клиент, те же API (`games`), новых scopes/ключей не требуется. Сейвы лежат в Application Data Folder **Google Drive самого игрока**, разработчик к ним доступа не имеет. |
| Нужны ли настройки в Play Console? | **Да, одна галочка.** `Grow users → Play Games Services → Setup and management → Configuration → Edit properties → Saved Games = ON → Save`. Активация — **до 24 часов**; чтобы проверить сразу, на тестовом устройстве чистят данные Google Play services (`Settings → Apps → Google Play services → Manage space → Clear all data`). |
| Нужны ли изменения в Android-проекте игры? | **Нет**, если ачивки и лидерборды уже работают: та же зависимость `play-services-games-v2`, тот же Project ID, никаких новых permission, тот же keystore. |
| Насколько сложно добавить в плагин? | **Средне.** ~200–250 строк Java + TS-типы + web-заглушки. Основная работа — не API, а обработка конфликтов и асинхронности, плюс проверка на живом устройстве. |

Важное ограничение, о котором легко забыть: **сейвы не появятся «сами по себе»,
если галочка в консоли выключена** — все вызовы будут падать с ошибкой сервиса.

## 2. Что именно требуется от Google Play Console и Cloud

### Play Console (обязательно)

1. Приложение уже связано с Play Games Services (ачивки/лидерборды работают) — это
   предварительное условие.
2. Включить **Saved Games** (шаги выше). Это отдельный переключатель, он не
   включается автоматически вместе с ачивками.
3. Убедиться, что сборка, на которой тестируем, подписана **тем же ключом**, чей
   SHA-1 прописан в Play Games Services (Play App Signing). Debug-сборка с другим
   SHA-1 для snapshots не работает так же, как и для лидербордов.
4. Тестовые аккаунты (`Test and release → Setup and management → Testers`) — как и
   сейчас.

### Google Cloud (ничего не нужно)

- Отдельных API включать не надо: Saved Games — часть Play Games Services.
- OAuth-scope `https://www.googleapis.com/auth/games` уже покрывает сейвы,
  дополнительных consent-экранов не появится.
- Данные видны только игре: папка Application Data Folder в Drive игрока.
- **Не путать с REST API:** серверный доступ к снапшотам (`games/v1/snapshots`)
  существует, но требует server-side access (`requestServerSideAccess`) и нужен
  только если захотим читать/писать сейвы из бэкенда. Для плагина не нужен.

### Android-проект (ничего не нужно)

- `com.google.android.gms:play-services-games-v2` уже подключён в `android/build.gradle`.
- Manifest: новых permission/`<queries>` не требуется.
- `minSdk 24` в плагине — с запасом (SnapshotsClient живёт с древних API).

## 3. Что даёт API: возможности и лимиты

- **Лимиты:** 3 МБ на один файл сохранения, 800 КБ на обложку; трафик не
  тарифицируется. Слотов у игрока может быть много (это не «один сейв на игрока»).
- **Метаданные:** ID, имя (не показывается игроку), описание, last modified,
  played time, обложка. Всё это нужно для штатного экрана выбора сохранений.
- **Офлайн:** запись возможна без сети — Play Games кэширует и синхронизирует позже.
  Отсюда и берутся конфликты.
- **Конфликты:** `SnapshotsClient.DataOrConflict.isConflict()`; если `true`, есть две
  версии — серверная (`getSnapshot()`) и локальная (`getConflictingSnapshot()`).
  Разрешение: `resolveConflict(conflictId, snapshot)` либо автоматические политики
  `RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED` / `LONGEST_PLAYTIME` /
  `LAST_KNOWN_GOOD` / `MANUAL`.
- **Штатный UI выбора сейва:** `getSelectSnapshotIntent(title, allowAddButton,
  allowDelete, maxCount)` → `startActivityForResult` → в результате приходит
  `EXTRA_SNAPSHOT_METADATA` (выбран существующий) или `EXTRA_SNAPSHOT_NEW`.
- **Уровень качества Google (Level Up):** `LU-CS-GAA` — состояние должно сохраняться
  в облако и подтягиваться при старте; `LU-CS-GAB` — обязательна политика разрешения
  конфликтов (в т.ч. сценарий «гость → вход в аккаунт с прогрессом»). То есть
  «ручные слоты по кнопке» формально guideline не закрывают — нужен автосейв.

## 4. Разбор плагина: что придётся трогать

```
src/definitions.ts                                  объявление API (источник README/docgen)
src/web.ts                                          заглушки для web
android/.../CapacitorGameConnectPlugin.java          @PluginMethod-обёртки + ActivityResultLauncher
android/.../CapacitorGameConnect.java                логика Play Games v2
ios/Plugin/CapacitorGameConnect.swift                GameKit
```

Наблюдения по коду, которые прямо влияют на добавление сейвов:

1. **Логика вынесена правильно.** `CapacitorGameConnect.java` — обычный Java-класс с
   `AppCompatActivity`; `Plugin.java` только маршрутизирует. Добавление сейвов ложится
   в ту же схему: методы в `CapacitorGameConnect`, обёртки в `...Plugin.java`.
2. **Часть методов резолвится мгновенно.** `submitScore`, `unlockAchievement`,
   `incrementAchievementProgress`, `showLeaderboard` вызывают `call.resolve()` сразу,
   не дожидаясь `Task`. Для сейвов так нельзя: игре нужно знать, записалось ли, и
   получить данные при чтении. Образец честной асинхронности в коде один —
   `getUserTotalScore` (resolve/reject в `addOnSuccessListener/addOnFailureListener`).
3. **`startActivityIntent` уже есть, но колбэк пустой.** В `load()` регистрируется
   `StartActivityForResult` с пустым `onActivityResult`. Для штатного экрана выбора
   сохранений этого мало: нужно поймать результат, вытащить
   `EXTRA_SNAPSHOT_METADATA` и отрезолвить **тот** `PluginCall`, который вызвал экран
   (шаблонный приём Capacitor — `@ActivityCallback` либо поле `pendingCall`).
4. **Нет проверки авторизации.** `signIn` — отдельный метод, остальные предполагают,
   что игра уже вошла. Для сейвов стоит явно проверять `isAuthenticated()` и
   отклонять с понятным текстом, иначе получим невнятную ошибку GMS.
5. **`play-services-games-v2:+`** — плавающая версия. Для стабильных сборок лучше
   зафиксировать (например, `21.x`), иначе поведение уплывёт между сборками.
6. **`android/local.properties` закоммичен в upstream** — мусор, при форке лучше убрать.
7. **iOS-часть независима.** GameKit умеет то же самое (`GKLocalPlayer.saveGameData`,
   `fetchSavedGames`, `resolveConflictsForSavedGames`), но это отдельная реализация —
   если TV/Android в приоритете, iOS можно сделать вторым этапом.

## 5. Что реализовано (ветка `feat/cloud-save`)

API добавлен в `src/definitions.ts`, реальные типы — `src/interfaces/saved-game.interface.ts`:

```ts
export type SavedGameConflictPolicy =
  | 'mostRecentlyModified'   // RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED (по умолчанию)
  | 'longestPlaytime'        // RESOLUTION_POLICY_LONGEST_PLAYTIME
  | 'lastKnownGood'          // RESOLUTION_POLICY_LAST_KNOWN_GOOD
  | 'manual';                // RESOLUTION_POLICY_MANUAL — конфликт отдаётся в JS

loadSavedGame({ name?, snapshotId?, create?, conflictPolicy? }): Promise<SavedGame>
saveSavedGame({ name, data, description?, playedTimeMillis?, conflictPolicy? }): Promise<SavedGameMetadata>
listSavedGames(): Promise<{ games: SavedGameMetadata[] }>
deleteSavedGame({ name?, snapshotId? }): Promise<void>
resolveSavedGameConflict({ name, conflictId, resolution: 'server' | 'local', data? }): Promise<SavedGame>
showSavedGames({ title?, allowAddButton?, allowDelete?, maxCount? }): Promise<SavedGameSelection>
```

`SavedGame` = метаданные + `data` (UTF-8 строка) + `conflict`/`conflictId` +
опциональные `server`/`local` с обоими вариантами сейва.

### Файлы

| Файл | Что |
|---|---|
| `src/interfaces/saved-game.interface.ts` | все типы сейвов |
| `src/definitions.ts` | 6 новых методов + JSDoc (из него docgen собирает README) |
| `src/web.ts` | web-fallback на `localStorage` (load/save/list/delete), UI недоступен |
| `android/.../SavedGames.java` | вся логика Play Games Saved Games (~440 строк) |
| `android/.../CapacitorGameConnectPlugin.java` | `@PluginMethod`-обёртки + `@ActivityCallback` для экрана выбора |

### Решения, принятые по ходу

1. **Сейв закрывается сразу после чтения.** После `open()` снапшот остаётся открытым,
   и следующий `open` того же сейва падает с «snapshot already open». Поэтому
   `loadSavedGame` после `readFully()` делает `discardAndClose()` (данные на сервере
   не меняются), а `saveSavedGame`/`resolveSavedGameConflict` — `commitAndClose()`.
2. **`create: true` не создаёт пустой сейв на сервере.** `loadSavedGame` открывает
   несуществующий сейв как пустой и возвращает `data: ''`, но `discardAndClose` его не
   коммитит — реальная запись появляется только после `saveSavedGame`. Так «просто
   прочитать» не плодит пустые сейвы.
3. **Имя сейва доступно.** В документации его нет, но в интерфейсе есть
   `SnapshotMetadata.getUniqueName()` (проверено по `play-services-games-v2-22.1.0`),
   это и есть имя, с которым сейв открывали. Плагин отдаёт его в `name`, поэтому
   `listSavedGames()` и `showSavedGames()` возвращают имена — можно сразу звать
   `loadSavedGame({ name })`.
4. **Конфликт не разрешается молча, если выбрана `manual`.** `loadSavedGame` вернёт
   `conflict: true` + `server`/`local` + `conflictId`; `saveSavedGame` в этой ситуации
   отклоняется с кодом ошибки `CONFLICT` и тем же payload'ом; дальше игра зовёт
   `resolveSavedGameConflict`. `resolveSavedGameConflict` заново открывает сейв с
   политикой `MANUAL` (держать открытый снапшот в памяти между вызовами не нужно) и
   сверяет `conflictId`.
5. **Авторизация проверяется в каждом методе** (`isAuthenticated()` до работы с
   снапшотами); при отсутствии входа — reject с кодом `NOT_SIGNED_IN`.
6. **Лимит 3 МБ проверяется до записи** (по длине UTF-8, до `writeBytes`).
7. **Экран выбора сейва** — через `getSelectSnapshotIntent` + Capacitor
   `startActivityForResult`/`@ActivityCallback`; результат отдаёт `isNew: true`
   (нажали «новый») либо метаданные выбранного сейва.

### Что осталось

- **iOS не реализован**: на iOS методы есть в типах, но в `CapacitorGameConnect.swift`
  их нет — вызов упадёт с «not implemented». Нужен отдельный этап на
  `GKLocalPlayer.saveGameData` / `fetchSavedGames` / `resolveConflictsForSavedGames`
  (в GameKit нет штатного экрана выбора сохранений, `showSavedGames` придётся рисовать
  в игре).
- **Не собрано под Android.** На этом VPS нет JDK/Android SDK; Java проверена только
  на разбор синтаксиса и сверена с реальными классами `play-services-games-v2-22.1.0`
  через `javap`. `npm run build` (tsc + rollup + docgen) проходит.
- Проверка на устройстве: включить Saved Games в консоли, release-подпись, тестовый
  аккаунт, телефон + ТВ.
- В игре: автосейв при старте/паузе/выходе, загрузка при старте, экран конфликта,
  версия схемы сейва внутри JSON.

## 6. Оценка сложности

| Работа | Оценка |
|---|---|
| TS-типы + web-заглушки | 1–2 часа |
| Java: load/save/delete/list | 1 день с отладкой |
| Штатный экран выбора сейва (`@ActivityCallback`) | 2–4 часа |
| Разрешение конфликтов + UX в игре | 0.5–1 день (зависит от того, что решаем показывать) |
| Проверка на устройстве/ТВ + включение в консоли (24 ч ожидания) | 1 день календарно |
| iOS (GameKit saved games) — отдельным этапом | ~1 день |

Итого по Android: **2–3 рабочих дня**, из которых большая часть — не код, а проверка
на подписанной сборке и обработка конфликтов.

Риски, которые стоит проверить вживую:

1. **Android TV.** Игра — web + Capacitor Android / Android TV. Системные экраны Play
   Games (выбор сохранений, лидерборды) на ТВ-приставках могут не показываться или не
   управляться пультом — Play Games-приложения на ТВ обычно нет. Значит на TV надёжнее
   путь без штатного UI: `loadSavedGame`/`saveSavedGame` по фиксированному имени слота.
   Это надо проверить на конкретной приставке до того, как закладывать UI в дизайн.
2. **Подпись.** Тест «в debug-сборке» почти наверняка даст ошибку — нужен release-ключ,
   совпадающий с Play App Signing.
3. **Задержка активации.** После включения галочки в консоли до 24 часов; не забыть
   почистить данные Google Play services на тестовом устройстве.
4. **Формат данных.** Сейв игры сейчас, скорее всего, лежит в `localStorage`. Нужен
   явный формат (JSON-строка), версия схемы внутри и правило «что делать, если схема
   сейва старее» — иначе после обновления игры старые облачные сейвы поломают старт.

## 7. Статус работ

Сделано в ветке `feat/cloud-save`:

1. ✅ Исходники upstream в корне репозитория, remote `upstream` настроен.
2. ✅ `npm install` + `npm run build` (tsc, rollup, docgen → README) — зелёные.
3. ✅ TS-контракт: 6 методов + типы + web-fallback на `localStorage`.
4. ✅ Java: load / save / list / delete / resolveConflict / select UI.
5. ⏳ Saved Games в Play Console — **не включено**, это делает владелец аккаунта
   (галочка + ожидание активации до 24 ч).
6. ⏳ Сборка плагина и проверка на устройстве — только на дев-машине.
7. ⏳ iOS (GameKit saved games) — отдельный этап.
8. ⏳ Интеграция в игру: автосейв, загрузка при старте, экран конфликта, версия схемы.
