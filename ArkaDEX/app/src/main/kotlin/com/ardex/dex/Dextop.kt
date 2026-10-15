package com.ardex.dex

import android.util.Log

/**
 * Dextop: the desktop controller for ArkaDEX.
 *
 * A plain controller (not an Activity — Activities are started via Intents,
 * never instantiated with constructors). Owned by [MainActivity]: loads the
 * persisted desktop state on start and exposes it to the UI layer.
 */
class Dextop(
    private val syncService: SyncService
) {
    companion object {
        private const val TAG = "ArkaDEX-Dextop"
    }

    /** Last loaded desktop state (window titles, wallpaper, settings). */
    var state: Map<String, Any?> = emptyMap()
        private set

    /** Loads persisted state (local first, cloud when configured). */
    fun show(): Map<String, Any?> {
        state = loadStateFromSync()
        Log.d(TAG, "Dextop shown with ${state.size} state keys")
        return state
    }

    private fun loadStateFromSync(): Map<String, Any?> {
        // Cloud sync never shipped (see SyncService); local file is the source
        // of truth. Kept as a seam for when it does.
        Log.d(TAG, "Loaded state from local storage")
        return syncService.loadLocalState()
    }
}
