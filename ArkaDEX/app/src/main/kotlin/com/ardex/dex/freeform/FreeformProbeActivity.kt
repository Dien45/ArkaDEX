package com.ardex.dex.freeform

import android.app.Activity
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * Measures its own window and reports the bounds back, then finishes.
 *
 * Launched by [FreeformController] with `am start --windowingMode 5`. If the
 * device honours freeform, this activity arrives in a small floating window;
 * if the OEM ignored the setting, it arrives fullscreen. The bounds alone do
 * not prove freeform (split-screen looks similar), which is why the controller
 * also asks `dumpsys` for the task's windowing mode -- this measurement is the
 * corroborating half.
 *
 * Translucent and excluded from recents so it cannot leave anything behind
 * during the couple of hundred milliseconds it exists.
 */
class FreeformProbeActivity : Activity() {

    companion object {
        @Volatile var onResult: ((Result) -> Unit)? = null
        private const val REPORT_DELAY_MS = 250L
    }

    data class Result(val bounds: Rect, val display: Rect)

    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let a frame pass so the window has been placed before we measure.
        main.postDelayed({ report() }, REPORT_DELAY_MS)
    }

    private fun report() {
        val bounds = Rect()
        val display = Rect()
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                wm.currentWindowMetrics.let { bounds.set(it.bounds) }
                wm.maximumWindowMetrics.let { display.set(it.bounds) }
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.let {
                    it.getRectSize(bounds)
                    it.getRectSize(display)
                }
            }
            onResult?.invoke(Result(bounds, display))
        } catch (e: Exception) {
            Log.w("ArkaDEX-Probe", "could not measure own window", e)
        } finally {
            onResult = null
            finish()
        }
    }
}
