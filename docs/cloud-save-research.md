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

## 5. Предлагаемый API плагина

```ts
export type ConflictPolicy =
  | 'mostRecentlyModified'   // RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
  | 'longestPlaytime'
  | 'lastKnownGood'
  | 'manual';

export interface SavedGameInfo {
  name: string;
  description?: string;
  playedTimeMillis: number;
  lastModifiedMillis: number;
}

export interface SavedGameData extends SavedGameInfo {
  data: string;              // UTF-8 / JSON, плагин конвертирует в байты
  conflict?: boolean;        // true — конфликт был разрешён автоматически/вручную
}

export interface CapacitorGameConnectPlugin {
  // ... 8 существующих методов ...

  /** Прочитать сейв; если его нет и create !== false — создать пустой */
  loadGameData(options: {
    name: string;
    create?: boolean;
    conflictPolicy?: ConflictPolicy;
  }): Promise<SavedGameData>;

  /** Записать сейв (open → writeBytes → commitAndClose) */
  saveGameData(options: {
    name: string;
    data: string;
    description?: string;
    playedTimeMillis?: number;
  }): Promise<SavedGameInfo>;

  /** Список сохранений игрока */
  getSavedGames(): Promise<{ games: SavedGameInfo[] }>;

  /** Удалить сейв */
  deleteGameData(options: { name: string }): Promise<void>;

  /** Штатный экран Play Games «выбрать сохранение» */
  showSavedGames(options?: {
    title?: string;
    allowAddButton?: boolean;
    allowDelete?: boolean;
    maxCount?: number;
  }): Promise<{ name?: string; isNew?: boolean }>;
}
```

Скелет Java (в `CapacitorGameConnect.java`):

```java
public void loadGameData(PluginCall call) {
  String name = call.getString("name");
  boolean create = call.getBoolean("create", true);
  int policy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;

  PlayGames.getSnapshotsClient(activity)
    .open(name, create, policy)
    .addOnSuccessListener(result -> {
      // result.isConflict() == true — серверная/локальная версии, нужен выбор
      Snapshot snapshot = result.getData();
      try {
        byte[] bytes = snapshot.getSnapshotContents().readFully();
        JSObject ret = new JSObject();
        ret.put("data", new String(bytes, StandardCharsets.UTF_8));
        ret.put("conflict", result.isConflict());
        ret.put("name", name);
        call.resolve(ret);
      } catch (IOException e) {
        call.reject("read snapshot failed: " + e.getMessage());
      }
    })
    .addOnFailureListener(e -> call.reject(e.getMessage()));
}
```

Запись — `open(...)` → `snapshot.getSnapshotContents().writeBytes(data.getBytes(UTF_8))`
→ `SnapshotMetadataChange.Builder().setDescription(desc).build()` →
`commitAndClose(snapshot, change)`; резолв — по завершении `commitAndClose`.

Два места, где нужна аккуратность:

- **Конфликт при записи.** Если `open()` вернул `isConflict()`, нельзя просто писать
  поверх: по документации нужно либо взять одну из версий и вызвать
  `resolveConflict(conflictId, snapshot)`, либо честно отдать конфликт в JS
  (`conflict: true` + оба варианта) и дать игре решить. Для автосейва практично:
  политика `mostRecentlyModified` по умолчанию + возможность `manual` для UI «какой
  сейв оставить».
- **Размер.** 3 МБ — это про данные, но JSON-сейв игры лучше держать в килобайтах и
  не пихать туда историю партий: снапшот перезаписывается целиком при каждом коммите.

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
   путь без штатного UI: `loadGameData`/`saveGameData` по фиксированному имени слота.
   Это надо проверить на конкретной приставке до того, как закладывать UI в дизайн.
2. **Подпись.** Тест «в debug-сборке» почти наверняка даст ошибку — нужен release-ключ,
   совпадающий с Play App Signing.
3. **Задержка активации.** После включения галочки в консоли до 24 часов; не забыть
   почистить данные Google Play services на тестовом устройстве.
4. **Формат данных.** Сейв игры сейчас, скорее всего, лежит в `localStorage`. Нужен
   явный формат (JSON-строка), версия схемы внутри и правило «что делать, если схема
   сейва старее» — иначе после обновления игры старые облачные сейвы поломают старт.

## 7. Порядок работ

1. Форк + клон в этот каталог (`git clone` upstream, remote `upstream`, своя ветка).
2. `npm install && npm run build` на дев-машине (здесь нет node/JDK — только правки).
3. Включить Saved Games в Play Console (и подождать/почистить данные).
4. TS-типы + web-заглушки (контракт фиксируем до Java).
5. Java: load → save → list → delete, затем штатный UI.
6. Проверка: телефон (release-подпись, тестовый аккаунт) → ТВ-приставка.
7. `npm run docgen` (README), версия `8.1.0`, changelog.
8. В игре: автосейв при старте/паузе/выходе, загрузка при старте, экран конфликта.
