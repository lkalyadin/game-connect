<p align="center">
  <img src="https://s3.amazonaws.com/tw-inlineimages/467579/0/0/ff47c124a5732bf549b43532a7ac19e1.png"/>
</p>
<p align="center">
  <a href="http://www.openforge.io/">Official Website</a> |
  <a href="https://www.youtube.com/@OpenForge/videos">Subscribe to Youtube Channel</a>
</p>

# Introduction

Capacitor plugin for connecting and using services by Apple Game Center and Google Play Game Services. Features included are access to Sign-In, Leaderboard, and Achievements.

---

| Capacitor Version | Support Status |
| -----------    | :----:   |
| Capacitor v8   | ✅       |
| Capacitor v7   | ❌       |
| Capacitor v6   | ❌       |
| Capacitor v5   | ✅       |
| Capacitor v4   | ✅       |
| Capacitor v3   | ✅       |
| Capacitor v2   | ❌       |
| Capacitor v1   | ❌       |

✅ - Supported
🚧 - WIP Support
❌ - No plans to support

## Maintainers

The lovely folks at OpenForge! Feel free to tag any of the following:

| Maintainer | Github |
| ---------- | :----: |
| Ricardo   | @Ricardo385 |
| Paulina | @paulpauldevelops |
| Jedi | @jedihacks |

## Example Projects

Checkout these existing Ionic/Angular/Capacitor mobile game with the plugin installed and integrated:

- [Rock The Steps](https://github.com/openforge/rock-the-steps-app)
- [OpenFarm](https://github.com/openforge/openfarm-puzzle-game)

# Getting Started

## Install

```bash
npm install @openforge/capacitor-game-connect
npx cap sync
```

## Additional Code Setup

## Android

In order to use the plugin, you need to make sure to include this meta-data tag in your `AndroidManifest.xml` file of your app:

```xml
<meta-data android:name="com.google.android.gms.games.APP_ID" android:value="@string/game_services_project_id"/>
```

Then you need to set your Game Services Project ID in your `strings.xml` file inside your `app/src/main/res/values.xml`:

```xml
<string translatable="false"  name="game_services_project_id">YOU_APP_ID</string>
```

- Then on your `MainActivity.class` file you will need to import and register the plugin in your onCreate method:

```ts
@Override
public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CapacitorGameConnectPlugin.class);
    super.onCreate(savedInstanceState);
}
```

## Setup for Android

Follow this guide to configure correctly your Google Play Console to be able to use the Capacitor Game Connect plugin:

1. Go to your [Google Play Console](https://play.google.com/console)
2. If you don't have an app created, create one as a Game.
3. Go to Play Games Services under Grow section.
    - Cick Configuration
    - Select the option 'No, my game doesn’t use Google APIs', set a name and click Create.
4. Let's create a OAuth consent screen in Google Cloud Platform:
    - Go to your [Google Cloud Platform](https://console.cloud.google.com/). Make sure you have selected the correct app you want to create the OAuth consent screen.
    - Go to APIs & Services section, then in the sidebar click on OAuth consent screen
    - Choose your User Type (External or Internal) and click Create.
    - Fill the App Informationn required filds and then click on Save and Continue button at the bottom.
    - On the Scopes tab, click on Add or remove scopes and in the Search box type the following:
        - `auth/games` and click enter.
        - Select the two options that appears. `.../auth/games` and `./auth/games_lite`
        - Then start a new search and type `drive.appdata` and select the option showed.
        - Click the Update button.
        - Click Save and Continue button at the bottom.
    - Let's add your Test Users. ** This will be super important because your Google Play Services will only work with these users while is not into Production
        - Click Add Users button and type your users email.
        - Click Add.
        - Click Save and Continue button.
    - And that's all! Click on Back to Dashboard button and then click on Publish App button to finish creating your OAuth consent screen.
    - If you want to modify you information there, simple click on Back To Testing button and then click on Edit App at the top.
5. Back to your Google Play Console
6. In the Credentials section, click on Add Credential
    - Select the Type "Android"
    - Fill the other options
    - On the Authorization section, click Create OAuth client.
        - In the Popup that has been displayed, click on the `Create OAuth Client ID` link attached. This will be open a new window redirecting to the Google Cloud Platform Credentials section.
        - Select the Application Type. Should be Android.
        - Name your OAuth 2.0 client
        - Type your package name of your application.
        - Run the following command in your terminal:
            - `keytool -keystore path-to-debug-or-production-keystore -list -v`
            - If you don't have your keystore created yet, you can follow [this link](https://developer.android.com/studio/publish/app-signing#generate-key) to create it.
            - Once you get your SHA-1 certificate fingerprint, copy and paste it into the required field.
            - Click Create and then back to your Google Play Console.
    - Now that you have completed created your Credential ID, click in the dropdown option and select the one you have created.
    - Click Save Changes button at the bottom.
7. Back to your configurations and click Review and Publish button at the top.
    - Review if there are Actions Required to complete and fill them.

## Creating Achievements on Android

Before use the `Achievement Methods` of the plugin, you need to setup your Achievements and Leaderboards in your Google Play Console following the next steps:

1. Navigate to your Google Play Console portal and select your app.
2. On the sidebar, go to Grow section and then Setup and management
3. Click Leaderboards and configure it by filling the fields. Then click save and publish your changes
4. Click Achievements and configure it by filling the fields. Then click save and publish your changes
5. Make sure all your changes are published by going to the Publishing section.

## Setup for iOS

1. Click on Target App in xcode
2. Add team to Signing and Capabilities
3. Add Game Center Capability
4. Go to your Apps in <https://appstoreconnect.apple.com/> and add your application
5. Scroll down in your App Store tab from your application view and check the Game Center field
6. Go to Services tab and configure both Leaderboards and Achievements
7. Go back to App Store tab and select you Leaderboards and Achievements configurations

## API

<docgen-index>

* [`signIn()`](#signin)
* [`showLeaderboard(...)`](#showleaderboard)
* [`showAllLeaderboards()`](#showallleaderboards)
* [`submitScore(...)`](#submitscore)
* [`showAchievements()`](#showachievements)
* [`unlockAchievement(...)`](#unlockachievement)
* [`incrementAchievementProgress(...)`](#incrementachievementprogress)
* [`getUserTotalScore(...)`](#getusertotalscore)
* [`loadSavedGame(...)`](#loadsavedgame)
* [`saveSavedGame(...)`](#savesavedgame)
* [`listSavedGames()`](#listsavedgames)
* [`deleteSavedGame(...)`](#deletesavedgame)
* [`resolveSavedGameConflict(...)`](#resolvesavedgameconflict)
* [`showSavedGames(...)`](#showsavedgames)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### signIn()

```typescript
signIn() => Promise<{ player_name: string; player_id: string; }>
```

* Method to sign-in a user

**Returns:** <code>Promise&lt;{ player_name: string; player_id: string; }&gt;</code>

--------------------


### showLeaderboard(...)

```typescript
showLeaderboard(options: { leaderboardID: string; }) => Promise<void>
```

* Method to display the Leaderboards

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ leaderboardID: string; }</code> |

--------------------


### showAllLeaderboards()

```typescript
showAllLeaderboards() => Promise<void>
```

* Method to display all Leaderboards

--------------------


### submitScore(...)

```typescript
submitScore(options: { leaderboardID: string; totalScoreAmount: number; }) => Promise<void>
```

* Method to submit a score to the Google Play Services SDK

| Param         | Type                                                              |
| ------------- | ----------------------------------------------------------------- |
| **`options`** | <code>{ leaderboardID: string; totalScoreAmount: number; }</code> |

--------------------


### showAchievements()

```typescript
showAchievements() => Promise<void>
```

* Method to display the Achievements view

--------------------


### unlockAchievement(...)

```typescript
unlockAchievement(options: { achievementID: string; }) => Promise<void>
```

* Method to unlock an achievement

| Param         | Type                                    |
| ------------- | --------------------------------------- |
| **`options`** | <code>{ achievementID: string; }</code> |

--------------------


### incrementAchievementProgress(...)

```typescript
incrementAchievementProgress(options: { achievementID: string; pointsToIncrement: number; }) => Promise<void>
```

* Method to increment the progress of an achievement

| Param         | Type                                                               |
| ------------- | ------------------------------------------------------------------ |
| **`options`** | <code>{ achievementID: string; pointsToIncrement: number; }</code> |

--------------------


### getUserTotalScore(...)

```typescript
getUserTotalScore(options: { leaderboardID: string; }) => Promise<PlayerScore>
```

* Method to get total player score from a leaderboard

| Param         | Type                                    | Description |
| ------------- | --------------------------------------- | ----------- |
| **`options`** | <code>{ leaderboardID: string; }</code> | : string }  |

**Returns:** <code>Promise&lt;<a href="#playerscore">PlayerScore</a>&gt;</code>

--------------------


### loadSavedGame(...)

```typescript
loadSavedGame(options: { name?: string; snapshotId?: string; create?: boolean; conflictPolicy?: SavedGameConflictPolicy; }) => Promise<SavedGame>
```

* Method to load a saved game from Google Play Games (Saved Games)

Either `name` or `snapshotId` must be provided. When `snapshotId` is used
(for example the value returned by `showSavedGames`), the save is looked up
through the saved games list first.

With the default conflict policy the most recently modified version wins
and the conflict is resolved silently. With `conflictPolicy: 'manual'` the
call resolves with `conflict: true` plus both versions and nothing is
written until `resolveSavedGameConflict` is called.

| Param         | Type                                                                                                                                                    |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ name?: string; snapshotId?: string; create?: boolean; conflictPolicy?: <a href="#savedgameconflictpolicy">SavedGameConflictPolicy</a>; }</code> |

**Returns:** <code>Promise&lt;<a href="#savedgame">SavedGame</a>&gt;</code>

--------------------


### saveSavedGame(...)

```typescript
saveSavedGame(options: { name: string; data: string; description?: string; playedTimeMillis?: number; conflictPolicy?: SavedGameConflictPolicy; }) => Promise<SavedGameMetadata>
```

* Method to write a saved game to Google Play Games (Saved Games)

The payload is sent as UTF-8 bytes and cannot exceed 3 MB.

| Param         | Type                                                                                                                                                                           |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **`options`** | <code>{ name: string; data: string; description?: string; playedTimeMillis?: number; conflictPolicy?: <a href="#savedgameconflictpolicy">SavedGameConflictPolicy</a>; }</code> |

**Returns:** <code>Promise&lt;<a href="#savedgamemetadata">SavedGameMetadata</a>&gt;</code>

--------------------


### listSavedGames()

```typescript
listSavedGames() => Promise<{ games: SavedGameMetadata[]; }>
```

* Method to list the saved games of the current player

Each entry contains the save name, its identifier, description, played time
and the last modification timestamp.

**Returns:** <code>Promise&lt;{ games: SavedGameMetadata[]; }&gt;</code>

--------------------


### deleteSavedGame(...)

```typescript
deleteSavedGame(options: { name?: string; snapshotId?: string; }) => Promise<void>
```

* Method to delete a saved game

Either `name` or `snapshotId` must be provided.

| Param         | Type                                                 |
| ------------- | ---------------------------------------------------- |
| **`options`** | <code>{ name?: string; snapshotId?: string; }</code> |

--------------------


### resolveSavedGameConflict(...)

```typescript
resolveSavedGameConflict(options: { name: string; conflictId: string; resolution: 'server' | 'local'; data?: string; }) => Promise<SavedGame>
```

* Method to resolve a saved game conflict

Call it after `loadSavedGame` resolved with `conflict: true`. The chosen
version is committed; pass `data` to store a merged payload instead of the
chosen version as is.

| Param         | Type                                                                                               |
| ------------- | -------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ name: string; conflictId: string; resolution: 'server' \| 'local'; data?: string; }</code> |

**Returns:** <code>Promise&lt;<a href="#savedgame">SavedGame</a>&gt;</code>

--------------------


### showSavedGames(...)

```typescript
showSavedGames(options?: { title?: string | undefined; allowAddButton?: boolean | undefined; allowDelete?: boolean | undefined; maxCount?: number | undefined; } | undefined) => Promise<SavedGameSelection>
```

* Method to display the Google Play Games "saved games" screen

Resolves with `{ isNew: true }` when the player pressed "new save", or with
the metadata (including `name` and `snapshotId`) of the picked save. Use
`name` (or `snapshotId`) with `loadSavedGame` to read its contents.

| Param         | Type                                                                                                 |
| ------------- | ---------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ title?: string; allowAddButton?: boolean; allowDelete?: boolean; maxCount?: number; }</code> |

**Returns:** <code>Promise&lt;<a href="#savedgameselection">SavedGameSelection</a>&gt;</code>

--------------------


### Interfaces


#### PlayerScore

| Prop               | Type                |
| ------------------ | ------------------- |
| **`player_score`** | <code>number</code> |


#### SavedGame

A saved game loaded from Google Play Games.

| Prop             | Type                                                          | Description                                                             |
| ---------------- | ------------------------------------------------------------- | ----------------------------------------------------------------------- |
| **`name`**       | <code>string</code>                                           | Name the save was opened with (from the metadata, or the requested one) |
| **`data`**       | <code>string</code>                                           | Save payload, UTF-8 encoded (usually a JSON string)                     |
| **`conflict`**   | <code>boolean</code>                                          | `true` when Google Play Games reported a conflict                       |
| **`conflictId`** | <code>string</code>                                           | Identifier required by `resolveSavedGameConflict`                       |
| **`server`**     | <code><a href="#savedgameversion">SavedGameVersion</a></code> | Server version, present only when `conflict` is `true`                  |
| **`local`**      | <code><a href="#savedgameversion">SavedGameVersion</a></code> | Local version, present only when `conflict` is `true`                   |


#### SavedGameVersion

One of the two conflicting versions of a saved game.

| Prop       | Type                |
| ---------- | ------------------- |
| **`data`** | <code>string</code> |


#### SavedGameMetadata

Structured metadata of a saved game.

| Prop                     | Type                | Description                                                                                                                          |
| ------------------------ | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **`name`**               | <code>string</code> | Developer supplied save name (the `name` used to open the save). Google Play Games returns it as `SnapshotMetadata.getUniqueName()`. |
| **`snapshotId`**         | <code>string</code> | Google Play Games identifier of the save, stable across devices                                                                      |
| **`description`**        | <code>string</code> | Short description shown to the player by the Play Games saved games UI                                                               |
| **`playedTimeMillis`**   | <code>number</code> | Played time in milliseconds, shown by the Play Games saved games UI                                                                  |
| **`lastModifiedMillis`** | <code>number</code> | Last modification timestamp in milliseconds                                                                                          |
| **`coverImageUri`**      | <code>string</code> | URI of the cover image, when the save has one                                                                                        |


#### SavedGameSelection

Result of the Play Games "select saved game" screen.

| Prop        | Type                 | Description                                                                                                                          |
| ----------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| **`isNew`** | <code>boolean</code> | `true` when the player pressed "new save" instead of picking an existing one; the game is then expected to create a save of its own. |


### Type Aliases


#### SavedGameConflictPolicy

Policy used by Google Play Games to resolve a conflict between the local and
the server version of the same saved game.

- `mostRecentlyModified` (default) — the newest version wins
- `longestPlaytime` — the version with the biggest played time wins
- `lastKnownGood` — the last version that was known to be correct wins
- `manual` — nothing is resolved automatically, both versions are returned
  to the caller and must be resolved with `resolveSavedGameConflict`

<code>'mostRecentlyModified' | 'longestPlaytime' | 'lastKnownGood' | 'manual'</code>

</docgen-api>

# Testing Limitations

### Android

In order to test the functionality, you must have a physical Android device. Trying to connect to Google Play Services through Android Studio Emulator/Simulator will not work.
Note: Certain functionality may require having to sign your APK when building to your device.
