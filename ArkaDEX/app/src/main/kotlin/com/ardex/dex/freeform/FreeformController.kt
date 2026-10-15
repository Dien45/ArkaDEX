package com.ardex.dex.freeform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.Executors
import rikka.shizuku.Shizuku

/**
 * Enables and verifies Android's native freeform windowing.
 *
 * This is the load-bearing piece of the "desktop on any phone" direction.
 * Android has supported freeform since API 24 but ships it disabled, and
 * SystemUI enables it from either the vendor feature flag or this setting:
 *
 *     Settings.Global.getInt(cr, "enable_freeform_support", 0) != 0
 *
 * Writing that setting needs WRITE_SECURE_SETTINGS, which the ADB shell user
 * (uid 2000) has. Shizuku exposes exactly that identity over a binder, so no
 * root and no PC are required -- only that Shizuku itself is running.
 *
 * The catch this class exists to answer: some OEM skins silently ignore the
 * setting. Rather than assume, [probe] launches a self-measuring activity in
 * windowingMode 5 and asks the system what windowing mode that task actually
 * received. See [Verdict].
 */
class FreeformController(private val context: Context) {

    enum class Verdict {
        /** Nothing has been tried yet. */
        NOT_CHECKED,
        /** Shizuku is not installed, or its server is not running. */
        SHIZUKU_UNAVAILABLE,
        /** Shizuku is running but this app has not been granted permission. */
        NOT_GRANTED,
        /** The setting was written AND the system launched a window in freeform. */
        HONORED,
        /** The setting was written but the system ignored it. */
        IGNORED,
        /** Commands ran but the probe never reported back. */
        PROBE_TIMEOUT
    }

    data class Report(
        val verdict: Verdict,
        val detail: String,
        /** windowingMode the system reported for the probe task, if read. */
        val windowingMode: Int? = null,
        /** Bounds the probe reported for itself. */
        val probeBounds: Rect? = null,
        /** Full display bounds at probe time. */
        val displayBounds: Rect? = null,
        /** Every shell line captured, so a failing device can be diagnosed. */
        val log: List<String> = emptyList()
    ) {
        /** True when the answer means the whole freeform direction is viable. */
        val isViable: Boolean get() = verdict == Verdict.HONORED
    }

    companion object {
        private const val TAG = "ArkaDEX-Freeform"
        private const val PROBE_TIMEOUT_MS = 6_000L
        private const val SERVICE_TIMEOUT_MS = 8_000L

        /** AOSP WindowConfiguration.WINDOWING_MODE_FREEFORM. */
        const val WINDOWING_MODE_FREEFORM = 5

        private const val PROBE_COMPONENT = "com.ardex.dex.freeform/.FreeformProbeActivity"
        private const val SERVICE_VERSION = 1
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var shell: IShellService? = null
    @Volatile private var pendingConnect: Latch<Boolean>? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, ShellUserService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(false)
        .version(SERVICE_VERSION)

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shell = if (binder != null && binder.pingBinder()) {
                IShellService.Stub.asInterface(binder)
            } else {
                null
            }
            Log.d(TAG, "user service connected: shell=${shell != null}")
            pendingConnect?.complete(shell != null)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "user service disconnected")
            shell = null
        }
    }

    var isConnected: Boolean = false
        private set

    /** True when the running Shizuku server is the ADB identity, not root. */
    val isAdbIdentity: Boolean
        get() = runCatching { Shizuku.getUid() == 2000 }.getOrDefault(false)

    /**
     * Shizuku.checkSelfPermission() returns an int, not a Boolean -- the same
     * shape as a runtime permission. Comparing it here keeps the call sites
     * from each guessing wrong about that.
     */
    private fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun connect(): Boolean {
        val pinged = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        isConnected = pinged && hasPermission()
        return isConnected
    }

    /** Whether the user still has to approve ArkaDEX inside Shizuku. */
    fun needsPermission(): Boolean =
        runCatching { Shizuku.pingBinder() && !hasPermission() }.getOrDefault(false)

    fun isRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    /**
     * Writes the two settings that switch on platform freeform, then probes
     * whether the device actually listened. Async; the callback lands on the
     * main thread.
     */
    fun enableAndProbe(onResult: (Report) -> Unit) {
        io.execute {
            val report = runCatching { enableAndProbeBlocking() }.getOrElse { e ->
                Log.e(TAG, "enableAndProbe failed", e)
                Report(Verdict.IGNORED, "Command failed: ${e.message}")
            }
            main.post { onResult(report) }
        }
    }

    private fun enableAndProbeBlocking(): Report {
        val log = mutableListOf<String>()

        if (!isRunning()) {
            return Report(
                Verdict.SHIZUKU_UNAVAILABLE,
                "Shizuku is not running. Install the Shizuku app, start its server " +
                    "(wireless debugging or root), then grant ArkaDEX permission."
            )
        }
        if (!hasPermission()) {
            return Report(Verdict.NOT_GRANTED, "Shizuku permission has not been granted yet.")
        }
        if (!ensureService()) {
            return Report(
                Verdict.SHIZUKU_UNAVAILABLE,
                "Shizuku is running but the privileged shell service did not start. " +
                    "Check that ArkaDEX is still granted in the Shizuku app."
            )
        }
        log += "user service ready (uid ${if (isAdbIdentity) "2000, adb shell" else "0 or unknown"})"

        // force_resizable_activities makes the system tolerate a resizable
        // launch even for apps that declare themselves non-resizable, which
        // freeform windows require.
        for (cmd in listOf(
            "settings put global enable_freeform_support 1",
            "settings put secure force_resizable_activities 1"
        )) {
            val ok = sh(cmd) != null
            log += "$cmd -> ${if (ok) "ok" else "FAILED"}"
            if (!ok) {
                return Report(
                    Verdict.IGNORED,
                    "Could not write settings. The Shizuku server may be running as root " +
                        "(uid 0) rather than the ADB shell user, which lacks " +
                        "WRITE_SECURE_SETTINGS.",
                    log = log
                )
            }
        }

        // Read back what we just wrote, so a no-op write is visible.
        val readBack = sh("settings get global enable_freeform_support")?.trim()
        log += "read back enable_freeform_support = $readBack"

        val display = displayBounds()
        val latch = Latch<Rect>()
        FreeformProbeActivity.onResult = { r -> r.bounds?.let { latch.complete(it) } }

        val started = sh("am start --windowingMode $WINDOWING_MODE_FREEFORM -n $PROBE_COMPONENT")
        log += "am start -> ${if (started != null) "ok" else "FAILED"}"
        if (started == null) {
            FreeformProbeActivity.onResult = null
            return Report(Verdict.IGNORED, "Could not launch the probe activity.", log = log)
        }

        val bounds = latch.await(PROBE_TIMEOUT_MS)
        if (bounds == null) {
            FreeformProbeActivity.onResult = null
            return Report(
                Verdict.PROBE_TIMEOUT,
                "The probe activity never reported its bounds.",
                log = log
            )
        }

        val mode = readProbeWindowingMode(log)
        val honored = mode == WINDOWING_MODE_FREEFORM

        val verdict = if (honored) Verdict.HONORED else Verdict.IGNORED
        val detail = when {
            honored ->
                "Freeform is honoured. The system launched the probe in windowing mode " +
                    "$mode with bounds $bounds."
            mode != null && mode != WINDOWING_MODE_FREEFORM ->
                "The device overrode freeform and launched the probe in windowing mode " +
                    "$mode instead of $WINDOWING_MODE_FREEFORM."
            else ->
                "The setting was written (read back as `$readBack`) but the probe still " +
                    "received fullscreen bounds $bounds. This OEM skin ignores freeform."
        }
        return Report(verdict, detail, mode, bounds, display, log)
    }

    /**
     * Binds the privileged shell service, waiting for the binder. Shizuku
     * starts it under its own identity, which is what carries the permission.
     */
    private fun ensureService(): Boolean {
        val existing = shell
        if (existing != null && runCatching { existing.asBinder().pingBinder() }.getOrDefault(false)) {
            return true
        }
        val latch = Latch<Boolean>()
        pendingConnect = latch
        val bound = runCatching {
            Shizuku.bindUserService(userServiceArgs, userServiceConnection)
        }.onFailure {
            Log.w(TAG, "bindUserService failed", it)
        }.isSuccess
        if (!bound) {
            pendingConnect = null
            return false
        }
        val ok = latch.await(SERVICE_TIMEOUT_MS) == true
        pendingConnect = null
        return ok
    }

    /**
     * Asks the system directly what windowing mode the probe task received.
     * `dumpsys` is authoritative where the bounds heuristic is not: a
     * split-screen or compatibility launch also produces non-fullscreen
     * bounds, but only real freeform reports mode 5.
     */
    private fun readProbeWindowingMode(log: MutableList<String>): Int? {
        val dump = sh("dumpsys activity activities") ?: return null
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.contains("FreeformProbeActivity") }
        if (start < 0) {
            log += "dumpsys: probe task not found"
            return null
        }
        val mode = lines
            .drop(start)
            .take(40)
            .firstNotNullOfOrNull { line ->
                Regex("""windowingMode=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()
            }
        log += "dumpsys: windowingMode=$mode"
        return mode
    }

    private fun displayBounds(): Rect {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { return wm.maximumWindowMetrics.bounds }
        }
        @Suppress("DEPRECATION")
        return runCatching { Rect().also { wm.defaultDisplay.getRectSize(it) } }
            .getOrDefault(Rect())
    }

    /** Runs one command in the privileged shell. Null means it failed. */
    private fun sh(command: String): String? {
        val service = shell ?: run {
            Log.w(TAG, "sh called before the user service was bound")
            return null
        }
        return try {
            val out = service.exec(command)
            Log.d(TAG, "cmd ok ($command): ${out.trim()}")
            out
        } catch (e: RemoteException) {
            Log.w(TAG, "cmd failed ($command)", e)
            null
        }
    }

    /** Sends the user to Shizuku's permission screen for this app. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(42) }
            .onFailure { Log.w(TAG, "requestPermission failed", it) }
    }

    /** Launches Shizuku so the user can start its server. */
    fun openShizukuApp() {
        runCatching {
            context.startActivity(
                Intent().setComponent(
                    ComponentName("moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Log.w(TAG, "could not open Shizuku", it) }
    }

    fun release() {
        runCatching { Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true) }
            .onFailure { Log.w(TAG, "unbindUserService failed", it) }
        shell = null
    }
}

/** Minimal latch so a binder callback can answer back. */
private class Latch<T> {
    @Volatile private var value: T? = null
    private val lock = Object()

    fun complete(v: T) = synchronized(lock) {
        value = v
        lock.notifyAll()
    }

    fun await(timeoutMs: Long): T? = synchronized(lock) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (value == null) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            lock.wait(remaining)
        }
        value
    }
}
