package com.ardex.dex

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Detects whether this device will honour Android's native freeform windowing.
 *
 * The important asymmetry: **reading** `Settings.Global` needs no permission at
 * all, only **writing** it does. So ArkaDEX can answer "is freeform on?" with
 * no privileged component at all -- no root, no Shizuku, no pairing, no
 * daemon, no AIDL. An earlier design tried to *write* the setting through a
 * Shizuku UserService to save the user a tap; that cost several hundred lines,
 * two dependencies, a privileged service and a manifest provider, and bought
 * only the ability to flip a switch the user can flip themselves in Developer
 * Options. It was the wrong trade.
 *
 * The setting the system actually reads is documented in the platform as
 * `DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT`, and SystemUI accepts either
 * that value or the vendor feature flag:
 *
 *     Settings.Global.getInt(cr, "enable_freeform_support", 0) != 0
 *
 * Devices that ship freeform properly also declare
 * [PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT], which is the better
 * signal where it is present because it costs nothing to read.
 */
object FreeformSupport {

    private const val TAG = "ArkaDEX-Freeform"

    /** AOSP: Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT */
    const val KEY_ENABLE_FREEFORM = "enable_freeform_support"

    /** AOSP: Settings.Global.FORCE_RESIZABLE_ACTIVITIES (the Developer Options toggle) */
    const val KEY_FORCE_RESIZABLE = "force_resizable_activities"

    /** AOSP WindowConfiguration.WINDOWING_MODE_FREEFORM. */
    const val WINDOWING_MODE_FREEFORM = 5

    enum class State {
        /** The platform declares freeform support outright. */
        NATIVE,

        /** Not declared, but the user has enabled freeform in Developer Options. */
        USER_ENABLED,

        /** Developer Options says freeform is on but resizable activities is off,
         *  which is the combination that silently opens apps fullscreen. */
        RESIZE_DISABLED,

        /** Freeform is off; the user can enable it themselves, no root needed. */
        DISABLED,

        /** Android is too old to have freeform. */
        UNSUPPORTED
    }

    fun stateOf(context: Context): State {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return State.UNSUPPORTED

        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
            return State.NATIVE
        }
        val freeformOn = readGlobalInt(context, KEY_ENABLE_FREEFORM) != 0
        if (!freeformOn) return State.DISABLED

        // force_resizable_activities moved from Secure to Global over time; a
        // device could have either, so treat "set in either place" as enabled.
        val resizable = readGlobalInt(context, KEY_FORCE_RESIZABLE) != 0 ||
            readSecureInt(context, KEY_FORCE_RESIZABLE) != 0
        return if (resizable) State.USER_ENABLED else State.RESIZE_DISABLED
    }

    /** True when launching with bounds has a real chance of producing a window. */
    fun canLaunchWindowed(context: Context): Boolean =
        stateOf(context).let { it == State.NATIVE || it == State.USER_ENABLED }

    /**
     * Plain-language explanation of the current state, or null when nothing
     * needs saying.
     */
    fun explain(context: Context): String? = when (stateOf(context)) {
        State.NATIVE, State.USER_ENABLED -> null
        State.UNSUPPORTED ->
            "This Android version is too old for windowed apps (needs Android 7+)."
        State.DISABLED ->
            "Windowed apps are off. Turn them on in Developer Options — no root, " +
                "no extra app needed: Enable freeform windows, and also Force " +
                "activities to be resizable."
        State.RESIZE_DISABLED ->
            "Freeform is on but \"Force activities to be resizable\" is off, so apps " +
                "still open fullscreen. Turn that on in Developer Options too."
    }

    /** Deep link to the Developer Options page, where both toggles live. */
    fun developerOptionsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun readGlobalInt(context: Context, key: String): Int = runCatching {
        Settings.Global.getInt(context.contentResolver, key, 0)
    }.getOrElse { e ->
        // A malformed value throws rather than returning the default.
        Log.w(TAG, "could not read global $key", e)
        0
    }

    private fun readSecureInt(context: Context, key: String): Int = runCatching {
        @Suppress("DEPRECATION")
        Settings.Secure.getInt(context.contentResolver, key, 0)
    }.getOrDefault(0)
}
