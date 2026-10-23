import type { PlayerScore } from './interfaces/player-score.interface';
import type {
  SavedGame,
  SavedGameConflictPolicy,
  SavedGameMetadata,
  SavedGameSelection,
} from './interfaces/saved-game.interface';

export interface CapacitorGameConnectPlugin {
  /**
   * * Method to sign-in a user
   *
   *
   */
  signIn(): Promise<{
    player_name: string;
    player_id: string;
  }>;

  /**
   * * Method to display the Leaderboards
   *
   * @param leaderboardID as string
   */
  showLeaderboard(options: { leaderboardID: string }): Promise<void>;

  /**
   * * Method to display all Leaderboards
   *
   */
  showAllLeaderboards(): Promise<void>;

  /**
   * * Method to submit a score to the Google Play Services SDK
   *
   */
  submitScore(options: {
    leaderboardID: string;
    totalScoreAmount: number;
  }): Promise<void>;

  /**
   * * Method to display the Achievements view
   *
   */
  showAchievements(): Promise<void>;

  /**
   * * Method to unlock an achievement
   *
   */
  unlockAchievement(options: { achievementID: string }): Promise<void>;

  /**
   * * Method to increment the progress of an achievement
   */
  incrementAchievementProgress(options: {
    achievementID: string;
    pointsToIncrement: number;
  }): Promise<void>;

  /**
   * * Method to get total player score from a leaderboard
   *
   * @param options { leaderboardID: string }
   */
  getUserTotalScore(options: { leaderboardID: string }): Promise<PlayerScore>;

  /**
   * * Method to load a saved game from Google Play Games (Saved Games)
   *
   * Either `name` or `snapshotId` must be provided. When `snapshotId` is used
   * (for example the value returned by `showSavedGames`), the save is looked up
   * through the saved games list first.
   *
   * With the default conflict policy the most recently modified version wins
   * and the conflict is resolved silently. With `conflictPolicy: 'manual'` the
   * call resolves with `conflict: true` plus both versions and nothing is
   * written until `resolveSavedGameConflict` is called.
   */
  loadSavedGame(options: {
    name?: string;
    snapshotId?: string;
    /** Create an empty save when it does not exist yet, defaults to `true` */
    create?: boolean;
    conflictPolicy?: SavedGameConflictPolicy;
  }): Promise<SavedGame>;

  /**
   * * Method to write a saved game to Google Play Games (Saved Games)
   *
   * The payload is sent as UTF-8 bytes and cannot exceed 3 MB.
   */
  saveSavedGame(options: {
    name: string;
    data: string;
    description?: string;
    playedTimeMillis?: number;
    conflictPolicy?: SavedGameConflictPolicy;
  }): Promise<SavedGameMetadata>;

  /**
   * * Method to list the saved games of the current player
   *
   * Each entry contains the save name, its identifier, description, played time
   * and the last modification timestamp.
   */
  listSavedGames(): Promise<{ games: SavedGameMetadata[] }>;

  /**
   * * Method to delete a saved game
   *
   * Either `name` or `snapshotId` must be provided.
   */
  deleteSavedGame(options: {
    name?: string;
    snapshotId?: string;
  }): Promise<void>;

  /**
   * * Method to resolve a saved game conflict
   *
   * Call it after `loadSavedGame` resolved with `conflict: true`. The chosen
   * version is committed; pass `data` to store a merged payload instead of the
   * chosen version as is.
   */
  resolveSavedGameConflict(options: {
    name: string;
    conflictId: string;
    resolution: 'server' | 'local';
    data?: string;
  }): Promise<SavedGame>;

  /**
   * * Method to display the Google Play Games "saved games" screen
   *
   * Resolves with `{ isNew: true }` when the player pressed "new save", or with
   * the metadata (including `name` and `snapshotId`) of the picked save. Use
   * `name` (or `snapshotId`) with `loadSavedGame` to read its contents.
   */
  showSavedGames(options?: {
    title?: string;
    allowAddButton?: boolean;
    allowDelete?: boolean;
    maxCount?: number;
  }): Promise<SavedGameSelection>;
}
