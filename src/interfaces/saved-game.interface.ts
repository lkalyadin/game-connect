/**
 * Policy used by Google Play Games to resolve a conflict between the local and
 * the server version of the same saved game.
 *
 * - `mostRecentlyModified` (default) — the newest version wins
 * - `longestPlaytime` — the version with the biggest played time wins
 * - `lastKnownGood` — the last version that was known to be correct wins
 * - `manual` — nothing is resolved automatically, both versions are returned
 *   to the caller and must be resolved with `resolveSavedGameConflict`
 */
export type SavedGameConflictPolicy =
  | 'mostRecentlyModified'
  | 'longestPlaytime'
  | 'lastKnownGood'
  | 'manual';

/**
 * Structured metadata of a saved game.
 */
export interface SavedGameMetadata {
  /**
   * Developer supplied save name (the `name` used to open the save). Google
   * Play Games returns it as `SnapshotMetadata.getUniqueName()`.
   */
  name?: string;
  /** Google Play Games identifier of the save, stable across devices */
  snapshotId?: string;
  /** Short description shown to the player by the Play Games saved games UI */
  description?: string;
  /** Played time in milliseconds, shown by the Play Games saved games UI */
  playedTimeMillis: number;
  /** Last modification timestamp in milliseconds */
  lastModifiedMillis: number;
  /** URI of the cover image, when the save has one */
  coverImageUri?: string;
}

/** One of the two conflicting versions of a saved game. */
export interface SavedGameVersion extends SavedGameMetadata {
  data: string;
}

/** A saved game loaded from Google Play Games. */
export interface SavedGame extends SavedGameMetadata {
  /** Name the save was opened with (from the metadata, or the requested one) */
  name: string;
  /** Save payload, UTF-8 encoded (usually a JSON string) */
  data: string;
  /** `true` when Google Play Games reported a conflict */
  conflict: boolean;
  /** Identifier required by `resolveSavedGameConflict` */
  conflictId?: string;
  /** Server version, present only when `conflict` is `true` */
  server?: SavedGameVersion;
  /** Local version, present only when `conflict` is `true` */
  local?: SavedGameVersion;
}

/** Result of the Play Games "select saved game" screen. */
export interface SavedGameSelection extends SavedGameMetadata {
  /**
   * `true` when the player pressed "new save" instead of picking an existing
   * one; the game is then expected to create a save of its own.
   */
  isNew: boolean;
}
