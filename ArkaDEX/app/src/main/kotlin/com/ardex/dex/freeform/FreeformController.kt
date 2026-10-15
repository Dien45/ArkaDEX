package com.ardex.dex.freeform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
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

        /** AOSP WindowConfiguration.WINDOWING_MODE_FREEFORM. */
        const val WINDOWING_MODE_FREEFORM = 5

        private const val PROBE_COMPONENT = "com.ardex.dex.freeform/.FreeformProbeActivity"
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var isConnected: Boolean = false
        private set

    /** True when the running Shizuku server is the ADB identity, not root. */
    val isAdbIdentity: Boolean
        get() = runCatching { Shizuku.getUid() == 2000 }.getOrDefault(false)

    /**
     * Shizuku.checkSelfPermission() returns an int, not a Boolean -- the same
     * shape as a runtime permission. Comparing it here keeps the three call
     * sites from each guessing wrong about that.
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

        // force_resovable_activities makes the system tolerate a resizable
        // launch even for apps that declare themselves non-resizable, which
        // freeform windows require.
        for (cmd in listOf(
            "settings put global enable_freeform_support 1",
            "settings put secure force_resizable_activities 1"
        )) {
            val out = sh(cmd)
            log += "$cmd -> ${if (out != null) "ok" else "FAILED"}"
            if (out == null) {
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
        val probeBounds = CompletableResult<Rect>()
        FreeformProbeActivity.onResult = { r ->
            if (r.bounds != null) probeBounds.complete(r.bounds)
        }

        val started = sh("am start --windowingMode $WINDOWING_MODE_FREEFORM -n $PROBE_COMPONENT")
        log += "am start -> ${if (started != null) "ok" else "FAILED"}"
        if (started == null) {
            FreeformProbeActivity.onResult = null
            return Report(Verdict.IGNORED, "Could not launch the probe activity.", log = log)
        }

        val bounds = probeBounds.await(PROBE_TIMEOUT_MS)
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

    private fun sh(command: String): String? {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val out = process.inputStream.bufferedReader().readText()
            val err = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() != 0) {
                Log.w(TAG, "cmd failed ($command): $err")
                null
            } else {
                Log.d(TAG, "cmd ok ($command): ${out.trim()}")
                out
            }
        } catch (e: Exception) {
            Log.w(TAG, "sh threw ($command)", e)
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
}

/** Minimal latch so the probe can answer back across the shell boundary. */
private class CompletableResult<T> {
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
