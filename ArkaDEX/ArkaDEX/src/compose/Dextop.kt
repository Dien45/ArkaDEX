package com.ardex.dex.dextop

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class Dextop : AppCompatActivity() {
    companion object {
        private const val TAG = "ArkaDEX-Dextop"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dextop)
        loadStateFromSync()
    }

    private fun loadStateFromSync() {
        // Cloud sync integration
        Log.d(TAG, "Loading state cloud sync...")
        // TODO: implement actual sync
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Dextop resumed ready interaction")
    }
}