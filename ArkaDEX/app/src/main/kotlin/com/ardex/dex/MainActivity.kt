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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
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
            if (ok) recreate()
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
        // Persist open-window titles so the desktop restores across restarts.
        val titles = windowManager.openWindows().map { it.title }
        syncService.saveLocalState(syncService.getLocalState(mapOf("openWindows" to titles)))
        super.onPause()
    }

    override fun onDestroy() {
        clockHandler.removeCallbacks(clockTick)
        super.onDestroy()
    }

    // ----- menu: export / import -----

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_EXPORT, Menu.NONE, "Export desktop")
        menu.add(Menu.NONE, MENU_IMPORT, Menu.NONE, "Import desktop")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_EXPORT -> {
                exportLauncher.launch("arkadex-desktop.json")
                true
            }
            MENU_IMPORT -> {
                importLauncher.launch(arrayOf("application/json"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
        val apps = mutableListOf(
            DesktopApp("Notepad", ComponentName(this, NotepadActivity::class.java), null, safeIcon { pm.getActivityIcon(NotepadActivity::class.java) }),
            DesktopApp("Calculator", ComponentName(this, CalculatorActivity::class.java), null, safeIcon { pm.getActivityIcon(CalculatorActivity::class.java) }),
            DesktopApp("Files", ComponentName(this, FileManagerActivity::class.java), null, safeIcon { pm.getActivityIcon(FileManagerActivity::class.java) }),
            DesktopApp("Browser", ComponentName(this, BrowserActivity::class.java), null, safeIcon { pm.getActivityIcon(BrowserActivity::class.java) })
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
        appGrid.adapter = object : ArrayAdapter<DesktopApp>(
            this, R.layout.item_app_icon, apps
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
        val content: View = when (target.component?.className) {
            NotepadActivity::class.java.name -> NotepadActivity.createView(this)
            CalculatorActivity::class.java.name -> CalculatorActivity.createView(this)
            FileManagerActivity::class.java.name -> FileManagerActivity.createView(this)
            BrowserActivity::class.java.name -> BrowserActivity.createView(this)
            else -> Panel(this).apply { addTitle(title) }
        }
        windowManager.openWindow(title, 60, 80, 520, 720, content)
            ?: ensureOverlayPermission()
    }

    private fun openWelcomeWindow() {
        if (windowManager.openWindows().isEmpty()) {
            val panel = Panel(this).apply { addTitle(getString(R.string.welcome_title)) }
            windowManager.openWindow(getString(R.string.welcome_title), 100, 100, 500, 700, panel)
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
        private const val MENU_EXPORT = 1
        private const val MENU_IMPORT = 2
    }
}
