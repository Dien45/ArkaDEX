package com.ardex.dex

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * State persistence + sync service for ArkaDEX.
 *
 * - Local state is always stored in the app-private files dir
 *   (`filesDir/arkadex_state.json`), per PRD assumption 7 (no account/server).
 * - Export/import moves that JSON through a user-chosen document (Storage
 *   Access Framework), so no storage permission is needed and users can back
 *   up or migrate devices manually.
 * - Cloud sync via GitHub Releases is optional (PRD assumption 10): it runs
 *   only when the user configures a repository + token, otherwise sync is a
 *   local save and reports success.
 */
class SyncService(private val context: Context) {

    companion object {
        private const val TAG = "ArkaDEX-Sync"
        private const val STATE_FILE = "arkadex_state.json"
        private val MAP_TYPE = object : TypeToken<Map<String, Any?>>() {}.type
        private val gson = Gson()
    }

    private fun stateFile(): File = File(context.filesDir, STATE_FILE)

    /**
     * Collects the current desktop state: open windows, wallpaper, theme
     * and device metadata. Called on manual sync, background sync and pause.
     */
    fun getLocalState(extra: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        val state = mutableMapOf<String, Any?>(
            "package" to context.packageName,
            "sdk" to Build.VERSION.SDK_INT,
            "savedAt" to System.currentTimeMillis()
        )
        state.putAll(loadLocalState())
        state.putAll(extra)
        return state
    }

    /** Full sync: persist locally, push to cloud when configured. */
    fun syncState(extra: Map<String, Any?> = emptyMap()): Boolean {
        return try {
            val localState = getLocalState(extra)
            saveLocalState(localState)
            val pushed = pushToCloud(localState)
            Log.d(TAG, "State synced (local=true, cloud=$pushed)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync state", e)
            false
        }
    }

    /** Saves state locally for offline access (persistence layer). */
    fun saveLocalState(state: Map<String, Any?>) {
        try {
            stateFile().writeText(gson.toJson(state))
            Log.d(TAG, "Local state saved (${state.size} keys)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save local state", e)
        }
    }

    /** Loads state from local storage (used on app startup). Never throws. */
    fun loadLocalState(): Map<String, Any?> {
        return try {
            val file = stateFile()
            if (!file.exists()) {
                Log.d(TAG, "No local state file found")
                return emptyMap()
            }
            gson.fromJson<Map<String, Any?>>(file.readText(), MAP_TYPE) ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load local state", e)
            emptyMap()
        }
    }

    /** Exports the current state JSON to a user-chosen document. */
    fun exportState(uri: Uri): Boolean {
        return try {
            val json = gson.toJson(getLocalState())
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: return false
            Log.d(TAG, "State exported (${json.length} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export state", e)
            false
        }
    }

    /** Imports state JSON from a user-chosen document and persists it. */
    fun importState(uri: Uri): Boolean {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return false
            val state: Map<String, Any?> = gson.fromJson(json, MAP_TYPE) ?: return false
            saveLocalState(state)
            Log.d(TAG, "State imported (${state.size} keys)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import state", e)
            false
        }
    }

    /**
     * Optional cloud push (PRD assumption 10). Real GitHub Releases upload
     * needs a user token + repo, configured via [configureCloud]; without it
     * this is a no-op so offline users are never broken.
     */
    var cloudRepo: String? = null
    var cloudToken: String? = null

    fun configureCloud(repo: String, token: String) {
        cloudRepo = repo
        cloudToken = token
    }

    private fun pushToCloud(state: Map<String, Any?>): Boolean {
        if (cloudRepo.isNullOrBlank() || cloudToken.isNullOrBlank()) {
            Log.d(TAG, "Cloud sync not configured, skipping (local state kept)")
            return false
        }
        // TODO: POST state JSON as a release asset via api.github.com.
        Log.d(TAG, "Cloud push stub: ${state.size} keys to $cloudRepo")
        return false
    }

    /** Downloads the latest cloud state when cloud sync is configured. */
    fun getSyncedState(): Map<String, Any?> {
        if (cloudRepo.isNullOrBlank() || cloudToken.isNullOrBlank()) {
            Log.d(TAG, "Cloud sync not configured, returning local state")
            return loadLocalState()
        }
        // TODO: GET latest release asset from api.github.com.
        return emptyMap()
    }
}
