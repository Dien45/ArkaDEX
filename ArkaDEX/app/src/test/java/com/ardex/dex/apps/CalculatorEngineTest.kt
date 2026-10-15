package com.ardex.dex.apps

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculatorEngineTest {

    private fun result(expression: String): String =
        CalculatorEngine().apply {
            expression.forEach { press(it.toString()) }
            press("=")
        }.display

    @Test
    fun `arithmetic with precedence`() {
        assertEquals("7", result("1+2*3"))
        assertEquals("9", result("(1+2)*3"))
        assertEquals("2", result("10/5"))
    }

    @Test
    fun `parentheses nest`() {
        assertEquals("7", result("2*(3+(4-2))"))
    }

    @Test
    fun `division by zero reports an error instead of hanging`() {
        assertEquals("Error", result("1/0"))
    }

    @Test
    fun `trailing garbage is an error, not a silent result`() {
        assertEquals("Error", result("1++"))
    }

    @Test
    fun `backspace and clear reset the display`() {
        val e = CalculatorEngine()
        "1234".forEach { e.press(it.toString()) }
        e.press("⌫")
        assertEquals("123", e.display)
        e.press("C")
        assertEquals("0", e.display)
    }

    /**
     * Guards against a regression in the way results are rendered.
     *
     * `Double.toString()` is defined by the JVM as locale-independent, so the
     * calculator keeps producing a '.' even under a comma-decimal locale. A
     * review flagged this as broken; it is not, but the guarantee is easy to
     * replace by accident with `String.format("%.2f", v)`, which DOES honour
     * the default locale and would leave the display unparseable for a user
     * in de-DE.
     */
    @Test
    fun `display is locale independent`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("0.3333333333333333", result("1/3"))
            assertEquals("2.5", result("5/2"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `empty expression resolves to zero`() {
        assertEquals("0", result(""))
    }
}
