package com.ardex.dex.dextop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Main Activity ArkaDEX Initializes freeform window manager sets DEX root window. */
class MainActivity : AppCompatActivity() {
    lateinit var windowManager: WindowManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize window manager
        windowManager = WindowManager(this)
        // Set theme
        Theme.of(this).apply { loadTheme(this) }

        // Create main dextop window
        val dextop = Dextop(this)
        dextop.show()

        // Optionally open sample window
        windowManager.openWindow("Welcome", 100, 100, 500, 700)
    }

    private fun loadTheme(context: Context) {
        // Load ClayMorphism theme from theme.yaml
        // Implementation depends on how themes are loaded in the project
    }
}