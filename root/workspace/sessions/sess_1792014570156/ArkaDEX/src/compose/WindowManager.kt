package com.ardex.dex.dextop

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat

/** Freeform Window Manager Allows full control over windows: position, size, movement, resizing, minimization */
class WindowManager(context: Context) {

    companion object {
        private const val TAG = "ArkaDEX-WindowManager"
        const val WINDOW_MENU_ID = 100
    }

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Opens new window at specified coordinates with given title. */
    fun openWindow(title: String, x: Int = 0, y: Int = 0, width: Int = 400, height: Int = 600): View {
        val windowDecor = View(context).apply {
            // Set background or layout params as needed
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_light))
        }
        val layoutParams = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            x = x
            y = y
            gravity = Gravity.TOP or Gravity.START
        }
        windowManager.addView(windowDecor, layoutParams)
        return windowDecor
    }

    /** Moves existing window to new position. */
    fun moveWindow(window: View, x: Int, y: Int) {
        val params = window.layoutParams as WindowManager.LayoutParams
        params.x = x
        params.y = y
        windowManager.updateViewLayout(window, params)
    }

    /** Resizes window adjusting height (vertical expansion). */
    fun resizeWindow(window: View, height: Int) {
        val params = window.layoutParams as WindowManager.LayoutParams
        params.height = height
        windowManager.updateViewLayout(window, params)
    }

    /** Minimizes window. */
    fun minimizeWindow(window: View) {
        // Set size to 0,0 effectively hiding
        val params = window.layoutParams as WindowManager.LayoutParams
        params.width = 0
        params.height = 0
        windowManager.updateViewLayout(window, params)
    }

    /** Maximizes window (inside its parent bounds). */
    fun maximizeWindow(window: View) {
        // Simple implementation: expand within available space
        val display = windowManager.currentDisplay
        val rect = Rect()
        display.getRectSize(rect)
        val params = window.layoutParams as WindowManager.LayoutParams
        params.width = rect.width()
        params.height = rect.height()
        params.x = 0
        params.y = 0
        windowManager.updateViewLayout(window, params)
    }

    inner class Window(
        private val context: Context,
        private val title: String?,
        private val contentView: View
    ) : View(context) {
        private var isFocused = false

        init {
            title?.let { this.title = it }
        }

        override fun onResume() {
            super.onResume()
            if (isFocused) {
                show()
            }
        }

        override fun onTrimMemory(level: Int) {
            super.onTrimMemory(level)
            if (isFocused) {
                // Keep focused window expanded
                show()
                // Ensures visibility
            }
        }

        fun focus(activity: android.app.Activity) {
            isFocused = true
            activity.showDialog(this) // Placeholder: actual implementation depends on how windows are shown
        }
    }
}