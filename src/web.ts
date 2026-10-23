import { WebPlugin } from '@capacitor/core';

import type { CapacitorGameConnectPlugin } from './definitions';
import type { PlayerScore } from './interfaces/player-score.interface';
import type {
  SavedGame,
  SavedGameConflictPolicy,
  SavedGameMetadata,
  SavedGameSelection,
} from './interfaces/saved-game.interface';
import type { User } from './interfaces/user.interface';

const SAVED_GAMES_STORAGE_KEY = 'capacitor-game-connect:saved-games';
const MAX_SAVE_BYTES = 3 * 1024 * 1024;

interface WebSavedGameRecord {
  data: string;
  description?: string;
  playedTimeMillis: number;
  lastModifiedMillis: number;
}

type WebSavedGamesStore = Record<string, WebSavedGameRecord>;

export class CapacitorGameConnectWeb
  extends WebPlugin
  implements CapacitorGameConnectPlugin
{
  /**
   * * Method to sign-in a user to Google Play Services
   * * TODO: migrate interface to a different file
   * * Add web support for signIn
   */
  async signIn(): Promise<User> {
    return Promise.resolve({} as User);
  }

  /**
   * Method to display the Leaderboards view from Google Play Services SDK
   *
   * @param leaderboardID as string
   */
  async showLeaderboard(options: { leaderboardID: string }): Promise<void> {
    console.info('showLeaderboard function has been called', options);
    return Promise.resolve();
  }

  /**
   * * Method to display all Leaderboards
   *
   * @returns Promise
   */
  async showAllLeaderboards(): Promise<void> {
    console.info('showAllLeaderboards function has been called');
    return Promise.resolve();
  }

  /**
   * * Method to submit a score to the Google Play Services SDK
   *
   * @returns Promise
   */
  async submitScore(options: {
    leaderboardID: string;
    totalScoreAmount: number;
  }): Promise<void> {
    console.info('submitScore function has been called', options);
    return Promise.resolve();
  }

  /**
   * * Method to display the Achievements view from Google Play SDK
   *
   * @returns Promise
   */
  async showAchievements(): Promise<void> {
    return Promise.resolve();
  }

  /**
   * * Method to unlock an achievement
   *
   * @returns  Promise
   */
  async unlockAchievement(options: { achievementID: string }): Promise<void> {
    console.info('unlockAchievement function has been called', options);
    return Promise.resolve();
  }

  /**
   * * Method to increment the progress of an achievement
   *
   * @returns Promise
   */
  async incrementAchievementProgress(options: {
    achievementID: string;
    pointsToIncrement: number;
  }): Promise<void> {
    console.info(
      'incrementAchievementProgress function has been called',
      options,
    );
    return Promise.resolve();
  }

  /**
   * * Function to get the total player score from a specific leaderboard
   *
   * @param options { leaderboardID: string }
   * @returns Promise<PlayerScore>
   */
  async getUserTotalScore(options: {
    leaderboardID: string;
  }): Promise<PlayerScore> {
    console.info('getUserTotalScore function has been called', options);
    return Promise.resolve({} as PlayerScore);
  }

  /**
   * Web fallback for the Google Play Games saved games API.
   *
   * Saves are kept in `localStorage` so the game can exercise its cloud save
   * flow in a browser. There is no cross-device sync and no conflict handling.
   */
  async loadSavedGame(options: {
    name?: string;
    snapshotId?: string;
    create?: boolean;
    conflictPolicy?: SavedGameConflictPolicy;
  }): Promise<SavedGame> {
    const name = this.requireName(options.name, options.snapshotId);
    const store = this.readSavedGames();
    let record = store[name];

    if (!record) {
      if (options.create === false) {
        throw new Error(`Saved game "${name}" does not exist`);
      }
      record = {
        data: '',
        playedTimeMillis: 0,
        lastModifiedMillis: Date.now(),
      };
      store[name] = record;
      this.writeSavedGames(store);
    }

    return {
      name,
      data: record.data,
      description: record.description,
      playedTimeMillis: record.playedTimeMillis,
      lastModifiedMillis: record.lastModifiedMillis,
      snapshotId: this.snapshotIdFor(name),
      conflict: false,
    };
  }

  async saveSavedGame(options: {
    name: string;
    data: string;
    description?: string;
    playedTimeMillis?: number;
    conflictPolicy?: SavedGameConflictPolicy;
  }): Promise<SavedGameMetadata> {
    const size = new TextEncoder().encode(options.data).length;
    if (size > MAX_SAVE_BYTES) {
      throw new Error(
        `Saved game is too large (${size} bytes), the limit is ${MAX_SAVE_BYTES} bytes`,
      );
    }

    const store = this.readSavedGames();
    const record: WebSavedGameRecord = {
      data: options.data,
      description: options.description,
      playedTimeMillis:
        options.playedTimeMillis ?? store[options.name]?.playedTimeMillis ?? 0,
      lastModifiedMillis: Date.now(),
    };
    store[options.name] = record;
    this.writeSavedGames(store);

    return {
      name: options.name,
      snapshotId: this.snapshotIdFor(options.name),
      description: record.description,
      playedTimeMillis: record.playedTimeMillis,
      lastModifiedMillis: record.lastModifiedMillis,
    };
  }

  async listSavedGames(): Promise<{ games: SavedGameMetadata[] }> {
    const store = this.readSavedGames();
    return {
      games: Object.keys(store)
        .sort()
        .map(name => ({
          name,
          snapshotId: this.snapshotIdFor(name),
          description: store[name].description,
          playedTimeMillis: store[name].playedTimeMillis,
          lastModifiedMillis: store[name].lastModifiedMillis,
        })),
    };
  }

  async deleteSavedGame(options: {
    name?: string;
    snapshotId?: string;
  }): Promise<void> {
    const name = this.requireName(options.name, options.snapshotId);
    const store = this.readSavedGames();
    if (!store[name]) {
      throw new Error(`Saved game "${name}" does not exist`);
    }
    delete store[name];
    this.writeSavedGames(store);
  }

  resolveSavedGameConflict(_options: {
    name: string;
    conflictId: string;
    resolution: 'server' | 'local';
    data?: string;
  }): Promise<SavedGame> {
    throw this.unavailable('Saved game conflicts do not exist on web');
  }

  showSavedGames(_options?: {
    title?: string;
    allowAddButton?: boolean;
    allowDelete?: boolean;
    maxCount?: number;
  }): Promise<SavedGameSelection> {
    throw this.unavailable('showSavedGames is not supported on web');
  }

  private requireName(name?: string, snapshotId?: string): string {
    if (name) {
      return name;
    }
    throw new Error(
      snapshotId
        ? 'Loading saved games by snapshotId is not supported on web, pass name instead'
        : 'Either name or snapshotId is required',
    );
  }

  /**
   * Stable pseudo identifier, so callers that use `snapshotId` keep working.
   */
  private snapshotIdFor(name: string): string {
    return `web:${encodeURIComponent(name)}`;
  }

  private readSavedGames(): WebSavedGamesStore {
    try {
      const raw = localStorage.getItem(SAVED_GAMES_STORAGE_KEY);
      return raw ? (JSON.parse(raw) as WebSavedGamesStore) : {};
    } catch {
      return {};
    }
  }

  private writeSavedGames(store: WebSavedGamesStore): void {
    try {
      localStorage.setItem(SAVED_GAMES_STORAGE_KEY, JSON.stringify(store));
    } catch (error) {
      console.warn('Failed to persist saved games in localStorage', error);
    }
  }
}
