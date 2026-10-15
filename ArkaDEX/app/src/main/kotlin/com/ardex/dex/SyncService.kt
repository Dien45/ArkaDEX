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
 * - The PRD's optional GitHub-Releases cloud sync never shipped. Its code was
 *   an unreachable `TODO` behind a `configureCloud()` that no UI ever called,
 *   and `getSyncedState()` returned an empty map once it WAS configured.
 *   Removed rather than left as documentation: a stub that can never run is
 *   worse than no stub, and the README listed it as a real feature.
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

}
