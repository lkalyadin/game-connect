package com.openforge.capacitorgameconnect;

import android.content.Intent;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotContents;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataBuffer;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Google Play Games Saved Games (snapshots) implementation.
 *
 * The saved games service is a separate Play Console switch, see
 * https://developer.android.com/games/pgs/console/enable-features#enabling-saved-games
 */
public class SavedGames {

    private static final String TAG = "CapacitorGameConnect";

    /** Google Play Games allows 3 MB of binary data per saved game. */
    private static final int MAX_SAVE_BYTES = 3 * 1024 * 1024;

    private static final String DEFAULT_SELECT_TITLE = "Saved games";

    private final AppCompatActivity activity;

    public SavedGames(AppCompatActivity activity) {
        this.activity = activity;
    }

    private interface SnapshotHandler {
        void handle(SnapshotsClient.DataOrConflict<Snapshot> result);
    }

    /** Lets the plugin own the activity result plumbing. */
    public interface IntentLauncher {
        void launch(PluginCall call, Intent intent);
    }

    /**
     * * Method to load a saved game, either by name or by snapshot id
     */
    public void loadSavedGame(PluginCall call) {
        final String name = call.getString("name");
        final boolean create = !Boolean.FALSE.equals(call.getBoolean("create", true));
        final int policy = mapConflictPolicy(call.getString("conflictPolicy"));

        requireSignedIn(
            call,
            () -> withSnapshot(call, create, policy, result -> {
                if (result.isConflict()) {
                    resolveConflictResult(call, name, result.getConflict());
                    return;
                }
                Snapshot snapshot = result.getData();
                try {
                    JSObject savedGame = snapshotToJs(name, snapshot, false);
                    closeSnapshot(snapshot);
                    call.resolve(savedGame);
                } catch (IOException e) {
                    call.reject("Failed to read saved game: " + e.getMessage());
                }
            })
        );
    }

    /**
     * * Method to write a saved game
     */
    public void saveSavedGame(PluginCall call) {
        final String name = call.getString("name");
        final String data = call.getString("data");
        if (name == null || name.trim().isEmpty()) {
            call.reject("name is required");
            return;
        }
        if (data == null) {
            call.reject("data is required");
            return;
        }

        final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SAVE_BYTES) {
            call.reject(
                "Saved game is too large (" + bytes.length + " bytes), the limit is " + MAX_SAVE_BYTES + " bytes"
            );
            return;
        }

        final String description = call.getString("description");
        final Long playedTimeMillis = call.getLong("playedTimeMillis");
        final int policy = mapConflictPolicy(call.getString("conflictPolicy"));

        requireSignedIn(
            call,
            () -> withSnapshot(call, true, policy, result -> {
                if (result.isConflict()) {
                    try {
                        call.reject(
                            "Saved game conflict, resolve it with resolveSavedGameConflict first",
                            "CONFLICT",
                            conflictToJs(name, result.getConflict())
                        );
                    } catch (IOException e) {
                        call.reject("Failed to read conflicting saved games: " + e.getMessage());
                    }
                    return;
                }

                Snapshot snapshot = result.getData();
                SnapshotContents contents = snapshot.getSnapshotContents();
                if (contents == null || contents.isClosed() || !contents.writeBytes(bytes)) {
                    closeSnapshot(snapshot);
                    call.reject("Failed to write saved game, the snapshot is closed or read-only");
                    return;
                }

                SnapshotMetadataChange.Builder metadataChange = new SnapshotMetadataChange.Builder();
                if (description != null) {
                    metadataChange.setDescription(description);
                }
                if (playedTimeMillis != null) {
                    metadataChange.setPlayedTimeMillis(playedTimeMillis);
                }

                client()
                    .commitAndClose(snapshot, metadataChange.build())
                    .addOnSuccessListener(metadata -> {
                        JSObject savedGame = metadataToJs(metadata);
                        savedGame.put("name", name);
                        call.resolve(savedGame);
                    })
                    .addOnFailureListener(e -> call.reject("Failed to commit saved game: " + e.getMessage()));
            })
        );
    }

    /**
     * * Method to list the saved games of the current player
     */
    public void listSavedGames(PluginCall call) {
        requireSignedIn(
            call,
            () -> client()
                .load(false)
                .addOnSuccessListener(annotatedData -> {
                    JSArray games = new JSArray();
                    SnapshotMetadataBuffer buffer = annotatedData.get();
                    if (buffer != null) {
                        List<SnapshotMetadata> metadata = buffer.freeze();
                        for (SnapshotMetadata item : metadata) {
                            games.put(metadataToJs(item));
                        }
                    }
                    JSObject result = new JSObject();
                    result.put("games", games);
                    call.resolve(result);
                })
                .addOnFailureListener(e -> call.reject("Failed to list saved games: " + e.getMessage()))
        );
    }

    /**
     * * Method to delete a saved game, either by name or by snapshot id
     */
    public void deleteSavedGame(PluginCall call) {
        final String name = call.getString("name");

        requireSignedIn(
            call,
            () -> withSnapshot(call, false, SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED, result -> {
                if (result.isConflict()) {
                    try {
                        call.reject(
                            "Saved game conflict, resolve it with resolveSavedGameConflict first",
                            "CONFLICT",
                            conflictToJs(name, result.getConflict())
                        );
                    } catch (IOException e) {
                        call.reject("Failed to read conflicting saved games: " + e.getMessage());
                    }
                    return;
                }

                Snapshot snapshot = result.getData();
                SnapshotMetadata metadata = snapshot.getMetadata();
                if (metadata == null) {
                    closeSnapshot(snapshot);
                    call.reject("Saved game has no metadata and cannot be deleted");
                    return;
                }

                SnapshotContents contents = snapshot.getSnapshotContents();
                if (contents != null && !contents.isClosed()) {
                    // Close the opened snapshot first, deleting an open one is not allowed.
                    client()
                        .discardAndClose(snapshot)
                        .addOnCompleteListener(task -> deleteSnapshot(call, metadata));
                } else {
                    deleteSnapshot(call, metadata);
                }
            })
        );
    }

    private void deleteSnapshot(PluginCall call, SnapshotMetadata metadata) {
        client()
            .delete(metadata)
            .addOnSuccessListener(snapshotId -> call.resolve())
            .addOnFailureListener(e -> call.reject("Failed to delete saved game: " + e.getMessage()));
    }

    /**
     * * Method to resolve a conflict reported by loadSavedGame
     */
    public void resolveSavedGameConflict(PluginCall call) {
        final String name = call.getString("name");
        final String conflictId = call.getString("conflictId");
        final String resolution = call.getString("resolution", "server");
        final String data = call.getString("data");

        if (name == null || name.trim().isEmpty()) {
            call.reject("name is required");
            return;
        }
        if (conflictId == null || conflictId.trim().isEmpty()) {
            call.reject("conflictId is required");
            return;
        }

        final byte[] bytes = data == null ? null : data.getBytes(StandardCharsets.UTF_8);
        if (bytes != null && bytes.length > MAX_SAVE_BYTES) {
            call.reject(
                "Saved game is too large (" + bytes.length + " bytes), the limit is " + MAX_SAVE_BYTES + " bytes"
            );
            return;
        }

        requireSignedIn(
            call,
            () -> client()
                .open(name, false, SnapshotsClient.RESOLUTION_POLICY_MANUAL)
                .addOnSuccessListener(result -> {
                    if (!result.isConflict()) {
                        // The conflict is gone (another device or a previous call resolved it).
                        Snapshot snapshot = result.getData();
                        try {
                            JSObject savedGame = snapshotToJs(name, snapshot, false);
                            closeSnapshot(snapshot);
                            call.resolve(savedGame);
                        } catch (IOException e) {
                            call.reject("Failed to read saved game: " + e.getMessage());
                        }
                        return;
                    }

                    SnapshotsClient.SnapshotConflict conflict = result.getConflict();
                    if (!conflictId.equals(conflict.getConflictId())) {
                        try {
                            call.reject(
                                "Conflict id does not match, reload the saved game",
                                "CONFLICT",
                                conflictToJs(name, conflict)
                            );
                        } catch (IOException e) {
                            call.reject("Failed to read conflicting saved games: " + e.getMessage());
                        }
                        return;
                    }

                    Snapshot chosen = "local".equals(resolution)
                        ? conflict.getConflictingSnapshot()
                        : conflict.getSnapshot();

                    client()
                        .resolveConflict(conflict.getConflictId(), chosen)
                        .addOnSuccessListener(resolved -> {
                            if (resolved.isConflict()) {
                                call.reject("Saved game is still in conflict, try again");
                                return;
                            }
                            commitResolvedSnapshot(call, name, resolved.getData(), bytes);
                        })
                        .addOnFailureListener(e -> call.reject("Failed to resolve saved game conflict: " + e.getMessage()));
                })
                .addOnFailureListener(e -> call.reject("Failed to open saved game '" + name + "': " + e.getMessage()))
        );
    }

    /**
     * * Intent of the Play Games "select saved game" screen, the caller launches it
     */
    public void selectSavedGame(
        PluginCall call,
        String title,
        boolean allowAddButton,
        boolean allowDelete,
        int maxCount,
        IntentLauncher launcher
    ) {
        final String screenTitle = title == null || title.isEmpty() ? DEFAULT_SELECT_TITLE : title;

        requireSignedIn(
            call,
            () -> client()
                .getSelectSnapshotIntent(screenTitle, allowAddButton, allowDelete, maxCount)
                .addOnSuccessListener(intent -> launcher.launch(call, intent))
                .addOnFailureListener(e -> call.reject("Failed to open the saved games screen: " + e.getMessage()))
        );
    }

    private void commitResolvedSnapshot(PluginCall call, String name, Snapshot snapshot, byte[] data) {
        try {
            final String payload = data != null ? new String(data, StandardCharsets.UTF_8) : readSnapshotData(snapshot);

            if (data != null) {
                SnapshotContents contents = snapshot.getSnapshotContents();
                if (contents == null || contents.isClosed() || !contents.writeBytes(data)) {
                    call.reject("Failed to write saved game, the snapshot is closed or read-only");
                    return;
                }
            }

            client()
                .commitAndClose(snapshot, new SnapshotMetadataChange.Builder().build())
                .addOnSuccessListener(metadata -> {
                    JSObject result = metadataToJs(metadata);
                    result.put("name", name);
                    result.put("data", payload);
                    result.put("conflict", false);
                    call.resolve(result);
                })
                .addOnFailureListener(e -> call.reject("Failed to commit saved game: " + e.getMessage()));
        } catch (IOException e) {
            call.reject("Failed to read saved game: " + e.getMessage());
        }
    }

    /**
     * Opens a snapshot by name or by snapshot id, depending on what the call
     * provides, and forwards the result to the handler.
     */
    private void withSnapshot(PluginCall call, boolean createIfMissing, int policy, SnapshotHandler handler) {
        final String name = call.getString("name");
        final String snapshotId = call.getString("snapshotId");

        if (name != null && !name.trim().isEmpty()) {
            client()
                .open(name, createIfMissing, policy)
                .addOnSuccessListener(result -> handler.handle(result))
                .addOnFailureListener(e -> call.reject("Failed to open saved game '" + name + "': " + e.getMessage()));
            return;
        }

        if (snapshotId != null && !snapshotId.trim().isEmpty()) {
            client()
                .load(false)
                .addOnSuccessListener(annotatedData -> {
                    SnapshotMetadataBuffer buffer = annotatedData.get();
                    SnapshotMetadata found = null;
                    if (buffer != null) {
                        for (SnapshotMetadata item : buffer.freeze()) {
                            if (snapshotId.equals(item.getSnapshotId())) {
                                found = item;
                                break;
                            }
                        }
                    }
                    if (found == null) {
                        call.reject("Saved game not found: " + snapshotId);
                        return;
                    }
                    client()
                        .open(found, policy)
                        .addOnSuccessListener(result -> handler.handle(result))
                        .addOnFailureListener(e ->
                            call.reject("Failed to open saved game '" + snapshotId + "': " + e.getMessage())
                        );
                })
                .addOnFailureListener(e -> call.reject("Failed to list saved games: " + e.getMessage()));
            return;
        }

        call.reject("Either name or snapshotId is required");
    }

    private void requireSignedIn(PluginCall call, Runnable action) {
        PlayGames
            .getGamesSignInClient(activity)
            .isAuthenticated()
            .addOnSuccessListener(authResult -> {
                if (authResult.isAuthenticated()) {
                    action.run();
                } else {
                    call.reject("Player is not signed in, call signIn() first", "NOT_SIGNED_IN");
                }
            })
            .addOnFailureListener(e -> call.reject("Failed to check the sign-in state: " + e.getMessage()));
    }

    private SnapshotsClient client() {
        return PlayGames.getSnapshotsClient(activity);
    }

    /**
     * Releases an opened snapshot that was only read, so the next open does not
     * fail with "snapshot already open".
     */
    private void closeSnapshot(Snapshot snapshot) {
        SnapshotContents contents = snapshot.getSnapshotContents();
        if (contents == null || contents.isClosed()) {
            return;
        }
        client()
            .discardAndClose(snapshot)
            .addOnFailureListener(e -> Log.w(TAG, "Failed to close snapshot: " + e.getMessage()));
    }

    private void resolveConflictResult(PluginCall call, String name, SnapshotsClient.SnapshotConflict conflict) {
        try {
            call.resolve(conflictToJs(name, conflict));
        } catch (IOException e) {
            call.reject("Failed to read conflicting saved games: " + e.getMessage());
        }
    }

    private static int mapConflictPolicy(String policy) {
        if (policy == null) {
            return SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;
        }
        switch (policy) {
            case "longestPlaytime":
                return SnapshotsClient.RESOLUTION_POLICY_LONGEST_PLAYTIME;
            case "lastKnownGood":
                return SnapshotsClient.RESOLUTION_POLICY_LAST_KNOWN_GOOD;
            case "manual":
                return SnapshotsClient.RESOLUTION_POLICY_MANUAL;
            case "mostRecentlyModified":
            default:
                return SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;
        }
    }

    static JSObject metadataToJs(SnapshotMetadata metadata) {
        JSObject result = new JSObject();
        if (metadata == null) {
            return result;
        }
        if (metadata.getUniqueName() != null) {
            result.put("name", metadata.getUniqueName());
        }
        if (metadata.getSnapshotId() != null) {
            result.put("snapshotId", metadata.getSnapshotId());
        }
        if (metadata.getDescription() != null) {
            result.put("description", metadata.getDescription());
        }
        if (metadata.getCoverImageUri() != null) {
            result.put("coverImageUri", metadata.getCoverImageUri().toString());
        }
        result.put("playedTimeMillis", metadata.getPlayedTime());
        result.put("lastModifiedMillis", metadata.getLastModifiedTimestamp());
        return result;
    }

    static JSObject snapshotToJs(String name, Snapshot snapshot, boolean conflict) throws IOException {
        JSObject result = metadataToJs(snapshot.getMetadata());
        if (name != null) {
            result.put("name", name);
        }
        result.put("data", readSnapshotData(snapshot));
        result.put("conflict", conflict);
        return result;
    }

    private static JSObject conflictToJs(String name, SnapshotsClient.SnapshotConflict conflict) throws IOException {
        JSObject result = new JSObject();
        if (name != null) {
            result.put("name", name);
        }
        result.put("conflict", true);
        result.put("conflictId", conflict.getConflictId());
        result.put("server", versionToJs(conflict.getSnapshot()));
        result.put("local", versionToJs(conflict.getConflictingSnapshot()));
        return result;
    }

    private static JSObject versionToJs(Snapshot snapshot) throws IOException {
        JSObject result = metadataToJs(snapshot.getMetadata());
        result.put("data", readSnapshotData(snapshot));
        return result;
    }

    private static String readSnapshotData(Snapshot snapshot) throws IOException {
        SnapshotContents contents = snapshot.getSnapshotContents();
        if (contents == null || contents.isClosed()) {
            return "";
        }
        byte[] bytes = contents.readFully();
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }
}
