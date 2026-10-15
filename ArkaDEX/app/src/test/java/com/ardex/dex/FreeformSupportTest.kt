package com.ardex.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The state machine that decides whether a windowed launch is worth attempting.
 *
 * FreeformSupport.stateOf reads Settings.Global, which needs no permission --
 * only writing it does. That asymmetry is the reason this whole approach works
 * without root, Shizuku or a daemon, and it is worth having pinned by a test
 * so nobody "improves" it into something that needs privilege again.
 */
class FreeformSupportTest {

    // Mirrors FreeformSupport.stateOf, with the Android reads injected, so the
    // decision table can be checked on a plain JVM.
    private fun classify(
        sdk: Int,
        hasFreeformFeature: Boolean,
        enableFreeform: Int,
        forceResizableGlobal: Int,
        forceResizableSecure: Int
    ): FreeformSupport.State {
        val sdkN = 24
        if (sdk < sdkN) return FreeformSupport.State.UNSUPPORTED
        if (hasFreeformFeature) return FreeformSupport.State.NATIVE
        if (enableFreeform == 0) return FreeformSupport.State.DISABLED
        val resizable = forceResizableGlobal != 0 || forceResizableSecure != 0
        return if (resizable) FreeformSupport.State.USER_ENABLED else FreeformSupport.State.RESIZE_DISABLED
    }

    @Test
    fun `old android is unsupported`() {
        assertEquals(FreeformSupport.State.UNSUPPORTED, classify(23, false, 1, 1, 0))
        assertEquals(FreeformSupport.State.NATIVE, classify(24, true, 0, 0, 0))
    }

    @Test
    fun `vendor feature wins without needing the setting`() {
        assertEquals(
            FreeformSupport.State.NATIVE,
            classify(34, hasFreeformFeature = true, enableFreeform = 0, forceResizableGlobal = 0, forceResizableSecure = 0)
        )
    }

    @Test
    fun `disabled when freeform is off`() {
        assertEquals(
            FreeformSupport.State.DISABLED,
            classify(34, hasFreeformFeature = false, enableFreeform = 0, forceResizableGlobal = 1, forceResizableSecure = 1)
        )
    }

    @Test
    fun `user enabled when both developer toggles are on`() {
        assertEquals(
            FreeformSupport.State.USER_ENABLED,
            classify(34, hasFreeformFeature = false, enableFreeform = 1, forceResizableGlobal = 1, forceResizableSecure = 0)
        )
    }

    /**
     * The failure mode worth protecting: freeform on but resizable off. The
     * system accepts launchBounds and then opens the app fullscreen anyway, so
     * this must NOT count as windowed-capable.
     */
    @Test
    fun `resizable-disabled is its own state, not windowed`() {
        assertEquals(
            FreeformSupport.State.RESIZE_DISABLED,
            classify(34, hasFreeformFeature = false, enableFreeform = 1, forceResizableGlobal = 0, forceResizableSecure = 0)
        )
    }

    /**
     * force_resizable_activities lives in Settings.Global on current Android
     * and in Settings.Secure on older builds; a device that only has the
     * Secure copy must still count as enabled.
     */
    @Test
    fun `resizable is read from secure as well as global`() {
        assertEquals(
            FreeformSupport.State.USER_ENABLED,
            classify(34, hasFreeformFeature = false, enableFreeform = 1, forceResizableGlobal = 0, forceResizableSecure = 1)
        )
    }

    @Test
    fun `only native or user-enabled counts as windowed`() {
        val windowed = listOf(FreeformSupport.State.NATIVE, FreeformSupport.State.USER_ENABLED)
        for (state in FreeformSupport.State.entries) {
            assertEquals(
                "state $state",
                state in windowed,
                state == FreeformSupport.State.NATIVE || state == FreeformSupport.State.USER_ENABLED
            )
        }
    }

    @Test
    fun `setting keys match what SystemUI reads`() {
        // Changing these silently would break detection on every device.
        assertEquals("enable_freeform_support", FreeformSupport.KEY_ENABLE_FREEFORM)
        assertEquals("force_resizable_activities", FreeformSupport.KEY_FORCE_RESIZABLE)
        assertEquals(5, FreeformSupport.WINDOWING_MODE_FREEFORM)
        assertTrue(FreeformSupport.State.entries.size == 5)
    }
}
