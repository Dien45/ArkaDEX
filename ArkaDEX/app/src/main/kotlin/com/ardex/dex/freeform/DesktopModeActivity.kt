package com.ardex.dex.freeform

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Readiness screen for the freeform-desktop direction.
 *
 * Its only job is to answer one question honestly: does THIS device honour
 * Android's native freeform windowing? Everything else in the "desktop on any
 * phone" plan is built on top of that answer, so this screen reports it as a
 * measurement rather than an assumption, including the raw shell log for any
 * device that says no.
 */
class DesktopModeActivity : Activity() {

    private lateinit var controller: FreeformController
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnProbe: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = FreeformController(this)
        setContentView(buildUi())
        render()
    }

    override fun onResume() {
        super.onResume()
        controller.connect()
        render()
    }

    override fun onDestroy() {
        // The privileged shell service is a real process on the other side of
        // a binder; leaving it bound leaks it for the session.
        controller.release()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(0xFFFAF9FF.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Desktop mode"
            textSize = 22f
            setTextColor(0xFF2D3436.toInt())
        })
        root.addView(TextView(this).apply {
            text = "ArkaDEX can drive Android's own freeform windowing so third-party " +
                "apps open in real draggable windows. Whether that works depends on " +
                "this device, not on ArkaDEX. Probe it below."
            textSize = 14f
            setTextColor(0xFF5B7D8B.toInt())
            setPadding(0, dp(12), 0, dp(16))
        })

        status = TextView(this).apply { textSize = 15f }
        root.addView(status)

        btnPermission = Button(this).apply {
            setOnClickListener { requestShizuku() }
        }
        root.addView(btnPermission, lp())

        btnProbe = Button(this).apply {
            text = "Enable freeform and probe"
            setOnClickListener { runProbe() }
        }
        root.addView(btnProbe, lp())

        logView = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF2D3436.toInt())
            gravity = Gravity.START
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(logView)
        })

        return root
    }

    private fun render() {
        val state = when {
            !controller.isRunning() ->
                "Shizuku: not running.\n\nInstall Shizuku, start its server over wireless " +
                    "debugging (or root), then come back. No root is needed."
            !controller.connect() ->
                "Shizuku: running, but ArkaDEX is not granted permission."
            else ->
                "Shizuku: ready (${if (controller.isAdbIdentity) "ADB shell, uid 2000" else "root, uid 0"})"
        }
        status.text = state
        status.setTextColor(if (controller.connect()) 0xFF2E7D32.toInt() else 0xFFB23A48.toInt())

        btnPermission.text = when {
            !controller.isRunning() -> "Open Shizuku"
            !controller.connect() -> "Grant Shizuku permission"
            else -> "Shizuku granted"
        }
        btnPermission.isEnabled = !controller.connect()
        btnProbe.isEnabled = controller.connect()
    }

    private fun requestShizuku() {
        if (controller.isRunning()) controller.requestPermission() else controller.openShizukuApp()
        render()
    }

    private fun runProbe() {
        btnProbe.isEnabled = false
        status.text = "Probing..."
        status.setTextColor(0xFF5B7D8B.toInt())
        controller.enableAndProbe { report ->
            val viable = report.isViable
            status.text = (if (viable) "FREEFORM WORKS\n" else "NOT VIABLE\n") + report.detail
            status.setTextColor(if (viable) 0xFF2E7D32.toInt() else 0xFFB23A48.toInt())
            val geometry = listOfNotNull(
                report.probeBounds?.let { "probe bounds: $it" },
                report.displayBounds?.takeIf { it.width() > 0 }?.let { "display:    $it" }
            )
            logView.text = (report.log + geometry).joinToString("\n")
            btnProbe.isEnabled = true
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun lp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
}
