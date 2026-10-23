package com.openforge.capacitorgameconnect;

import android.app.Activity;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.snapshot.SnapshotMetadata;

@CapacitorPlugin(name = "CapacitorGameConnect")
public class CapacitorGameConnectPlugin extends Plugin {

    private CapacitorGameConnect implementation;
    private SavedGames savedGames;
    private ActivityResultLauncher<Intent> startActivityIntent;

    @Override
    public void load() {
        PlayGamesSdk.initialize(getContext());
        startActivityIntent =
                getActivity()
                        .registerForActivityResult(
                                new ActivityResultContracts.StartActivityForResult(),
                                new ActivityResultCallback<ActivityResult>() {
                                    @Override
                                    public void onActivityResult(ActivityResult result) {
                                        // Add same code that you want to add in onActivityResult method
                                    }
                                }
                        );
        implementation = new CapacitorGameConnect(getActivity());
        savedGames = new SavedGames(getActivity());
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        implementation.signIn(call, new SignInCallback() {
            @Override
            public void success() {
                implementation.fetchUserInformation(new PlayerResultCallback() {
                    @Override
                    public void success(Player player) {
                        String playerId = player.getPlayerId();
                        String playerName = player.getDisplayName();

                        JSObject ret = new JSObject();
                        ret.put("player_id", playerId);
                        ret.put("player_name", playerName);
                        call.resolve(ret);
                    }

                    @Override
                    public void error(String message) {
                        call.reject(message);
                    }
                });
            }

            @Override
            public void error(String message) {
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        implementation.showLeaderboard(call, this.startActivityIntent);
        call.resolve();
    }

    @PluginMethod
    public void showAllLeaderboards(PluginCall call) {
        implementation.showAllLeaderboards(this.startActivityIntent);
        call.resolve();
    }

    @PluginMethod
    public void submitScore(PluginCall call) {
        implementation.submitScore(call);
        call.resolve();
    }

    @PluginMethod
    public void showAchievements(PluginCall call) {
        implementation.showAchievements(this.startActivityIntent);
        call.resolve();
    }

    @PluginMethod
    public void unlockAchievement(PluginCall call) {
        implementation.unlockAchievement(call);
        call.resolve();
    }

    @PluginMethod
    public void incrementAchievementProgress(PluginCall call) {
        implementation.incrementAchievementProgress(call);
        call.resolve();
    }

    @PluginMethod
    public void getUserTotalScore(PluginCall call) {
        implementation.getUserTotalScore(call);
    }

    @PluginMethod
    public void loadSavedGame(PluginCall call) {
        savedGames.loadSavedGame(call);
    }

    @PluginMethod
    public void saveSavedGame(PluginCall call) {
        savedGames.saveSavedGame(call);
    }

    @PluginMethod
    public void listSavedGames(PluginCall call) {
        savedGames.listSavedGames(call);
    }

    @PluginMethod
    public void deleteSavedGame(PluginCall call) {
        savedGames.deleteSavedGame(call);
    }

    @PluginMethod
    public void resolveSavedGameConflict(PluginCall call) {
        savedGames.resolveSavedGameConflict(call);
    }

    @PluginMethod
    public void showSavedGames(PluginCall call) {
        String title = call.getString("title");
        boolean allowAddButton = !Boolean.FALSE.equals(call.getBoolean("allowAddButton", true));
        boolean allowDelete = !Boolean.FALSE.equals(call.getBoolean("allowDelete", true));
        Integer requestedMaxCount = call.getInt("maxCount", 5);
        int maxCount = requestedMaxCount == null ? 5 : requestedMaxCount;

        savedGames.selectSavedGame(
            call,
            title,
            allowAddButton,
            allowDelete,
            maxCount,
            this::launchSavedGamesIntent
        );
    }

    private void launchSavedGamesIntent(PluginCall call, Intent intent) {
        startActivityForResult(call, intent, "savedGamesResult");
    }

    @ActivityCallback
    private void savedGamesResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }

        JSObject selection = new JSObject();
        selection.put("isNew", false);

        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            call.resolve(selection);
            return;
        }

        if (data.hasExtra(SnapshotsClient.EXTRA_SNAPSHOT_NEW)) {
            JSObject created = new JSObject();
            created.put("isNew", true);
            call.resolve(created);
            return;
        }

        SnapshotMetadata metadata = data.getParcelableExtra(SnapshotsClient.EXTRA_SNAPSHOT_METADATA);
        if (metadata == null) {
            call.resolve(selection);
            return;
        }

        JSObject picked = SavedGames.metadataToJs(metadata);
        picked.put("isNew", false);
        call.resolve(picked);
    }
}