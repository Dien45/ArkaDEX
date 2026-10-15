package com.ardex.dex

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ardex.dex.apps.BrowserActivity
import com.ardex.dex.apps.CalculatorActivity
import com.ardex.dex.apps.FileManagerActivity
import com.ardex.dex.apps.NotepadActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Activity of ArkaDEX: the desktop launcher.
 *
 * Shows installed + built-in apps as desktop icons, a taskbar with one
 * button per open window, and a clock. Tapping an icon opens the app:
 * built-ins open in overlay windows, third-party apps launch with the
 * freeform windowing mode (API 28+) when the device supports it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var windowManager: FreeformWindowManager
    private lateinit var syncService: SyncService
    private lateinit var dextop: Dextop
    private lateinit var theme: Theme

    private lateinit var appGrid: GridView
    private lateinit var taskButtons: LinearLayout
    private lateinit var clockView: TextView
    private lateinit var desktopRoot: View

    /** Fallback glyph when a package cannot supply its own icon. */
    private val defaultAppIcon: Drawable? by lazy {
        runCatching { packageManager.getDefaultActivityIcon() }.getOrNull()
    }

    /**
     * Set right after a successful import. `recreate()` runs
     * onPause -> onDestroy -> onCreate, so onPause would otherwise persist the
     * outgoing window list straight over the freshly imported state and undo
     * the import before anything could read it.
     */
    private var skipNextPersist = false

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (!::clockView.isInitialized) return
            clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            clockHandler.postDelayed(this, 30_000)
        }
    }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (windowManager.canDrawOverlays()) openWelcomeWindow()
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val ok = syncService.exportState(uri)
            toast(if (ok) "Desktop state exported" else "Export failed")
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val ok = syncService.importState(uri)
            toast(if (ok) "Desktop state imported, restarting desktop" else "Import failed")
            if (ok) {
                skipNextPersist = true
                recreate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        theme = Theme.load(this)
        windowManager = FreeformWindowManager(this)
        syncService = SyncService(this)
        dextop = Dextop(syncService)

        desktopRoot = findViewById(R.id.desktop_root)
        appGrid = findViewById(R.id.app_grid)
        taskButtons = findViewById(R.id.task_buttons)
        clockView = findViewById(R.id.clock)

        applyWallpaper()

        // Restore persisted desktop state (local first, cloud when configured).
        dextop.show()

        windowManager.onWindowsChanged = { refreshTaskbar(it) }
        refreshAppGrid()

        // Rebuild the previous session's windows. Must run after
        // onWindowsChanged is wired, or the taskbar stays empty while the
        // windows reopen. openWelcomeWindow() below then sees a non-empty
        // window list and skips the welcome panel.
        restoreWindows()

        findViewById<Button>(R.id.btn_menu).setOnClickListener {
            // "Show desktop": un-minimize every window; otherwise jump back
            // to the top of the icon grid.
            val minimized = windowManager.openWindows().filter { it.minimized }
            if (minimized.isNotEmpty()) {
                minimized.forEach { windowManager.restoreWindow(it.view) }
            } else {
                appGrid.smoothScrollToPosition(0)
                appGrid.requestFocus()
            }
        }

        findViewById<Button>(R.id.btn_overflow).setOnClickListener { anchor ->
            openOverflowMenu(anchor)
        }

        clockHandler.post(clockTick)
        ensureOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshAppGrid()
    }

    /**
     * Sets the desktop background: the device's system wallpaper when the
     * launcher can read it, otherwise the bundled ClayMorphism gradient.
     * Never throws — a wallpaper failure must not blank the home screen.
     */
    private fun applyWallpaper() {
        val system = runCatching {
            WallpaperManager.getInstance(this).drawable
        }.getOrNull()
        if (system != null) {
            desktopRoot.background = system
        } else {
            desktopRoot.setBackgroundResource(R.drawable.desktop_wallpaper)
        }
    }

    override fun onPause() {
        // Persist the open windows so the desktop is rebuilt on next launch.
        // Skipped without overlay permission: no window can be open in that
        // state, so writing an empty snapshot here would silently erase a
        // layout saved earlier (e.g. the user revoked the permission).
        if (skipNextPersist) {
            skipNextPersist = false
        } else if (windowManager.canDrawOverlays()) {
            persistWindows()
        }
        super.onPause()
    }

    /** Saves title + geometry + content key of every open window. */
    private fun persistWindows() {
        val snapshot = windowManager.openWindows().map { w ->
            mapOf(
                "title" to w.title,
                "x" to w.bounds.left,
                "y" to w.bounds.top,
                "width" to w.bounds.width(),
                "height" to w.bounds.height(),
                "contentKey" to w.contentKey
            )
        }
        syncService.saveLocalState(syncService.getLocalState(mapOf("openWindows" to snapshot)))
    }

    /**
     * Reopens the windows written by [persistWindows].
     *
     * Geometry is re-clamped because it round-trips through JSON, and an
     * imported file is arbitrary user input: LayoutParams rejects a
     * non-positive width or height outright.
     */
    @Suppress("UNCHECKED_CAST")
    private fun restoreWindows() {
        val saved = dextop.state["openWindows"] as? List<Map<String, Any?>> ?: return
        val minW = dp(MIN_WINDOW_W_DP)
        val minH = dp(MIN_WINDOW_H_DP)
        val max = dp(MAX_WINDOW_DP)
        for (entry in saved) {
            val title = entry["title"] as? String ?: continue
            val width = ((entry["width"] as? Number)?.toInt() ?: minW).coerceIn(minW, max)
            val height = ((entry["height"] as? Number)?.toInt() ?: minH).coerceIn(minH, max)
            val x = (entry["x"] as? Number)?.toInt() ?: dp(60)
            val y = (entry["y"] as? Number)?.toInt() ?: dp(80)
            val opened = windowManager.openWindow(
                title = title,
                x = x,
                y = y,
                width = width,
                height = height,
                content = contentFor(entry["contentKey"] as? String, title),
                contentKey = entry["contentKey"] as? String
            )
            // Null means the overlay permission is gone; nothing after it can
            // open either. onPause() skips persisting in that state, so the
            // previously saved layout is still intact.
            if (opened == null) return
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        // Views added to the system WindowManager are NOT torn down with the
        // activity. Without this they stay on screen after recreate() (so
        // import produced duplicate windows), survive dark-mode recreation
        // holding a dead Activity context, and leak it. Detach the listener
        // first so closing does not touch the view tree mid-teardown.
        windowManager.onWindowsChanged = null
        windowManager.closeAll()
        super.onDestroy()
    }

    // ----- menu: export / import -----

    /**
     * Export/import menu, anchored on the taskbar overflow button.
     *
     * This used to be `onCreateOptionsMenu`, which the action bar calls. The
     * app theme is `Theme.Material3.DayNight.NoActionBar`, so that callback
     * never fired and the whole feature was unreachable in the shipped UI.
     */
    private fun openOverflowMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            inflate(R.menu.desktop_menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_export -> {
                        exportLauncher.launch("arkadex-desktop.json")
                        true
                    }
                    R.id.action_import -> {
                        importLauncher.launch(arrayOf("application/json"))
                        true
                    }
                    else -> false
                }
            }
        }.show()
    }

    // ----- desktop icons -----

    private data class DesktopApp(
        val label: String,
        val component: ComponentName?,
        val launch: Intent?,
        val icon: Drawable?
    )

    private fun collectApps(): List<DesktopApp> {
        val pm = packageManager
        // PackageManager.getActivityIcon takes a ComponentName (not a Class),
        // so build the component once and reuse it for both the field and
        // the icon lookup.
        fun builtin(label: String, cls: Class<*>): DesktopApp {
            val component = ComponentName(this, cls)
            return DesktopApp(label, component, null, safeIcon { pm.getActivityIcon(component) })
        }
        val apps = mutableListOf(
            builtin("Notepad", NotepadActivity::class.java),
            builtin("Calculator", CalculatorActivity::class.java),
            builtin("Files", FileManagerActivity::class.java),
            builtin("Browser", BrowserActivity::class.java)
        )
        val main = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(main, 0)
        }
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue // skip self (built-ins already listed)
            val label = info.loadLabel(pm).toString()
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            apps.add(DesktopApp(label, null, intent, runCatching { info.loadIcon(pm) }.getOrNull()))
        }
        return apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    /** Icon lookup that never crashes the desktop when a package misbehaves. */
    private fun safeIcon(block: () -> Drawable): Drawable? =
        runCatching(block).getOrNull()

    private fun refreshAppGrid() {
        val apps = collectApps()
        // R.id.app_label is mandatory here, not cosmetic: ArrayAdapter casts
        // the ENTIRE item root to TextView when no field id is passed
        // (mFieldId == 0), and item_app_icon's root is a LinearLayout. That
        // ClassCastException is rethrown as IllegalStateException and crashes
        // the launcher on the first GridView layout.
        appGrid.adapter = object : ArrayAdapter<DesktopApp>(
            this, R.layout.item_app_icon, R.id.app_label, apps
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val app = getItem(position)!!
                val icon = view.findViewById<ImageView>(R.id.app_icon)
                val label = view.findViewById<TextView>(R.id.app_label)
                icon.setImageDrawable(app.icon ?: defaultAppIcon)
                label.text = app.label
                label.setTextColor(theme.color("text", 0xFF2D3436.toInt()))
                return view
            }
        }
        appGrid.setOnItemClickListener { _, _, position, _ -> launchApp(apps[position]) }
    }

    private fun launchApp(app: DesktopApp) {
        if (app.component != null) {
            // Built-in DEX app: open inside an overlay desktop window.
            if (!windowManager.canDrawOverlays()) {
                ensureOverlayPermission()
                return
            }
            val target = Intent().setComponent(app.component)
            openBuiltinWindow(app.label, target)
            return
        }
        val intent = app.launch ?: return
        // Third-party app: request a freeform-sized window (API 24+) via
        // launch bounds. Freeform-capable devices honor the bounds; others
        // simply open the app fullscreen. Harmless either way.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val metrics = resources.displayMetrics
                val w = (metrics.widthPixels * 0.85).toInt()
                val h = (metrics.heightPixels * 0.8).toInt()
                val left = (metrics.widthPixels - w) / 2
                val top = (metrics.heightPixels - h) / 3
                val options = ActivityOptions.makeBasic()
                options.launchBounds = Rect(left, top, left + w, top + h)
                startActivity(intent, options.toBundle())
                return
            } catch (e: Exception) {
                // Fall through to a normal launch.
            }
        }
        startActivity(intent)
    }

    /** Hosts a built-in app's UI inside an overlay window via its view factory. */
    private fun openBuiltinWindow(title: String, target: Intent) {
        val key = target.component?.className
        windowManager.openWindow(
            title = title,
            x = dp(60),
            y = dp(80),
            width = dp(520),
            height = dp(720),
            content = contentFor(key, title),
            contentKey = key
        ) ?: ensureOverlayPermission()
    }

    /**
     * Content view for a built-in DEX app, keyed by activity class name so a
     * restored window rebuilds the same app. Falls back to a titled panel.
     */
    private fun contentFor(className: String?, title: String): View = when (className) {
        NotepadActivity::class.java.name -> NotepadActivity.createView(this)
        CalculatorActivity::class.java.name -> CalculatorActivity.createView(this)
        FileManagerActivity::class.java.name -> FileManagerActivity.createView(this)
        BrowserActivity::class.java.name -> BrowserActivity.createView(this)
        else -> Panel(this).apply { addTitle(title) }
    }

    private fun openWelcomeWindow() {
        if (windowManager.openWindows().isEmpty()) {
            val panel = Panel(this).apply { addTitle(getString(R.string.welcome_title)) }
            // dp, not px: these were raw pixels, which is a postage stamp on a
            // 3x-density display and inconsistent with every other size here.
            windowManager.openWindow(
                getString(R.string.welcome_title),
                dp(100), dp(100), dp(500), dp(700), panel
            )
        }
    }

    // ----- taskbar -----

    private fun refreshTaskbar(windows: List<FreeformWindowManager.ManagedWindow>) {
        taskButtons.removeAllViews()
        for (w in windows) {
            val button = Button(this).apply {
                text = if (w.minimized) "[–] ${w.title}" else w.title
                minWidth = 0
                // Reflect state for screen readers, not only the bracket glyph.
                contentDescription = if (w.minimized) {
                    "${w.title}, minimized, activate to restore"
                } else {
                    "${w.title}, open, activate to minimize"
                }
                setOnClickListener { windowManager.toggleMinimize(w.view) }
            }
            taskButtons.addView(button)
        }
    }

    // ----- overlay permission -----

    private fun ensureOverlayPermission() {
        if (windowManager.canDrawOverlays()) {
            openWelcomeWindow()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.overlay_permission_title)
            .setMessage(R.string.overlay_permission_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                overlayPermissionLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        // Mirror the clamps in FreeformWindowManager's resize grip so a
        // restored window can never come back smaller than a dragged one.
        private const val MIN_WINDOW_W_DP = 200
        private const val MIN_WINDOW_H_DP = 160
        private const val MAX_WINDOW_DP = 2000
    }
}
