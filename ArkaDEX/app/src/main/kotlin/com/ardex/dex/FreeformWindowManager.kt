package com.ardex.dex

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager as SystemWindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Freeform window manager for ArkaDEX.
 *
 * Opens app content in overlay windows ([SystemWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY])
 * that can be moved, resized, minimized (with restore), maximized and closed.
 * Requires [Settings.canDrawOverlays]; callers must request it first.
 */
class FreeformWindowManager(private val context: Context) {

    data class ManagedWindow(
        val id: Int,
        val title: String,
        val view: View,
        var bounds: Rect,
        var minimized: Boolean = false,
        var savedBounds: Rect? = null,
        var maximized: Boolean = false,
        /** Caller-defined identity of the content (e.g. an activity class
         *  name), used to rebuild the window from a persisted session. */
        val contentKey: String? = null,
        /** Teardown for resources the content owns (e.g. a WebView). */
        val onClose: (() -> Unit)? = null
    )

    companion object {
        private const val TAG = "ArkaDEX-Windows"
        private const val TITLE_BAR_HEIGHT_DP = 40

        /** Off-screen parking spot for minimized windows (see [minimizeWindow]). */
        private const val PARKED = -10_000
    }

    private val systemWindowManager: SystemWindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as SystemWindowManager

    private val windows = LinkedHashMap<Int, ManagedWindow>()
    private var nextId = 1

    /** Listener feeds the taskbar: (open windows snapshot). */
    var onWindowsChanged: ((List<ManagedWindow>) -> Unit)? = null

    fun openWindows(): List<ManagedWindow> = windows.values.toList()

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private fun notifyChanged() = onWindowsChanged?.invoke(openWindows())

    private fun displayRect(): Rect {
        val rect = Rect()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.getRectSize(rect)
        }
        if (rect.isEmpty) {
            @Suppress("DEPRECATION")
            systemWindowManager.defaultDisplay.getRectSize(rect)
        }
        return rect
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /**
     * Opens a new overlay window. Returns null when the overlay permission
     * is missing (caller should send the user to Settings first).
     */
    fun openWindow(
        title: String,
        x: Int = 0,
        y: Int = 0,
        width: Int = 400,
        height: Int = 600,
        content: View? = null,
        contentKey: String? = null,
        onClose: (() -> Unit)? = null
    ): ManagedWindow? {
        if (!canDrawOverlays()) {
            Log.w(TAG, "Overlay permission missing, cannot open window: $title")
            return null
        }
        val id = nextId++
        val container = FrameLayout(context)

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFE3EAFB.toInt())
        }
        val titleView = TextView(context).apply {
            text = title
            setTextColor(0xFF2D3436.toInt())
            textSize = 14f
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        fun controlButton(label: String, action: () -> Unit): Button =
            Button(context).apply {
                text = label
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }
        titleBar.addView(titleView)
        // Lambdas run on click (after `container` exists), so capturing it is safe.
        titleBar.addView(controlButton("_") { minimizeWindow(container) })
        titleBar.addView(controlButton("[]") { maximizeWindow(container) })
        titleBar.addView(controlButton("X") { closeWindow(container) })
        titleBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(TITLE_BAR_HEIGHT_DP)
        )

        val body = content ?: Panel(context)
        body.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(TITLE_BAR_HEIGHT_DP) }

        container.addView(titleBar)
        container.addView(body)

        // Resize grip in the bottom-right corner of the window.
        val resizeHandle = View(context).apply {
            // Added last, so it draws above the app content. Kept small and
            // faint: a large opaque corner would permanently cover the
            // bottom-right of every window (list rows, scroll position).
            setBackgroundColor(0x334A90E2)
            contentDescription = "Resize window"
        }
        container.addView(
            resizeHandle,
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.BOTTOM or Gravity.END)
        )

        // Drag the window by its title bar. Buttons consume their own
        // touches, so this listener only fires for empty/title-area drags.
        var dragRawX = 0f
        var dragRawY = 0f
        var dragOriginX = 0
        var dragOriginY = 0
        titleBar.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = paramsOf(container)
                    dragRawX = event.rawX
                    dragRawY = event.rawY
                    dragOriginX = p.x
                    dragOriginY = p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragRawX).toInt()
                    val dy = (event.rawY - dragRawY).toInt()
                    moveWindow(container, dragOriginX + dx, dragOriginY + dy)
                    true
                }
                else -> true
            }
        }

        // Resize the window by dragging the corner grip.
        var resizeRawX = 0f
        var resizeRawY = 0f
        var resizeOriginW = 0
        var resizeOriginH = 0
        resizeHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val p = paramsOf(container)
                    resizeRawX = event.rawX
                    resizeRawY = event.rawY
                    resizeOriginW = p.width
                    resizeOriginH = p.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val w = (resizeOriginW + (event.rawX - resizeRawX)).toInt()
                    val h = (resizeOriginH + (event.rawY - resizeRawY)).toInt()
                    resizeWindow(container, w.coerceAtLeast(dp(200)), h.coerceAtLeast(dp(160)))
                    true
                }
                else -> true
            }
        }

        val bounds = Rect(x, y, x + width, y + height)
        val params = SystemWindowManager.LayoutParams(
            width,
            height,
            SystemWindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            SystemWindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = bounds.left
            this.y = bounds.top
        }
        systemWindowManager.addView(container, params)
        val managed = ManagedWindow(
            id, title, container, Rect(bounds), contentKey = contentKey, onClose = onClose
        )
        windows[id] = managed
        notifyChanged()
        Log.d(TAG, "Opened window #$id '$title' at $bounds")
        return managed
    }

    private fun paramsOf(window: View): SystemWindowManager.LayoutParams =
        window.layoutParams as SystemWindowManager.LayoutParams

    fun findWindow(view: View): ManagedWindow? = windows.values.firstOrNull { it.view == view }

    fun moveWindow(window: View, x: Int, y: Int) {
        val managed = findWindow(window) ?: return
        val params = paramsOf(window)
        params.x = x
        params.y = y
        systemWindowManager.updateViewLayout(window, params)
        managed.bounds.offsetTo(x, y)
    }

    fun resizeWindow(window: View, width: Int, height: Int) {
        val managed = findWindow(window) ?: return
        if (managed.minimized) return
        val params = paramsOf(window)
        params.width = width
        params.height = height
        systemWindowManager.updateViewLayout(window, params)
        managed.bounds.right = managed.bounds.left + width
        managed.bounds.bottom = managed.bounds.top + height
    }

    fun minimizeWindow(window: View) {
        val managed = findWindow(window) ?: return
        if (managed.minimized) return
        managed.savedBounds = Rect(managed.bounds)
        managed.maximized = false
        val params = paramsOf(window)
        params.width = 1
        params.height = 1
        // Park it off-screen. Leaving the 1x1 window at its old coordinates
        // kept FLAG_NOT_TOUCH_MODAL, so an invisible sliver still sat on top
        // of whatever was there and swallowed the next tap.
        params.x = PARKED
        params.y = PARKED
        systemWindowManager.updateViewLayout(window, params)
        managed.minimized = true
        notifyChanged()
    }

    fun restoreWindow(window: View) {
        val managed = findWindow(window) ?: return
        if (!managed.minimized) return
        val restored = managed.savedBounds ?: managed.bounds
        val params = paramsOf(window)
        params.width = restored.width()
        params.height = restored.height()
        params.x = restored.left
        params.y = restored.top
        systemWindowManager.updateViewLayout(window, params)
        managed.bounds = Rect(restored)
        managed.minimized = false
        managed.savedBounds = null
        notifyChanged()
    }

    fun toggleMinimize(window: View) {
        val managed = findWindow(window) ?: return
        if (managed.minimized) restoreWindow(window) else minimizeWindow(window)
    }

    /** Toggles between the display size and the pre-maximize geometry. */
    fun maximizeWindow(window: View) {
        val managed = findWindow(window) ?: return
        val params = paramsOf(window)
        if (managed.maximized) {
            val back = managed.savedBounds ?: return
            params.x = back.left
            params.y = back.top
            params.width = back.width()
            params.height = back.height()
            managed.bounds = Rect(back)
            managed.maximized = false
            managed.savedBounds = null
        } else {
            managed.savedBounds = Rect(managed.bounds)
            val rect = displayRect()
            params.x = 0
            params.y = 0
            params.width = rect.width()
            params.height = rect.height()
            managed.bounds = Rect(0, 0, rect.width(), rect.height())
            managed.maximized = true
        }
        systemWindowManager.updateViewLayout(window, params)
        notifyChanged()
    }

    fun closeWindow(window: View) {
        val entry = windows.entries.firstOrNull { it.value.view == window } ?: return
        try {
            systemWindowManager.removeView(window)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Window view already removed", e)
        }
        // The content may own resources that outlive the view: a WebView keeps
        // a renderer process alive unless it is explicitly destroyed.
        try {
            entry.value.onClose?.invoke()
        } catch (e: Exception) {
            Log.w(TAG, "Window onClose failed", e)
        }
        windows.remove(entry.key)
        notifyChanged()
    }

    fun closeAll() {
        windows.values.toList().forEach { closeWindow(it.view) }
    }
}
