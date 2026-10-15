package com.ardex.dex.apps

/**
 * Tiny expression engine for the built-in calculator.
 *
 * Recursive-descent parser handling + - * / and parentheses, no dependencies.
 * Deliberately free of any Android import: it lives outside [CalculatorActivity]
 * so it can be unit-tested on a plain JVM, which the nested companion class
 * could not be.
 *
 * Number formatting here relies on `Double.toString()`, which the JVM defines
 * as locale-independent (unlike `String.format("%f", ...)`). That is what keeps
 * the display parseable in locales using a comma decimal separator, and
 * CalculatorEngineTest pins it.
 */
class CalculatorEngine {
    var display = "0"
        private set
    private val buffer = StringBuilder()

    fun press(key: String) {
        when (key) {
            "C" -> {
                buffer.clear()
                display = "0"
            }
            "⌫" -> {
                if (buffer.isNotEmpty()) buffer.deleteCharAt(buffer.length - 1)
                display = buffer.ifEmpty { "0" }.toString()
            }
            "=" -> {
                display = try {
                    format(evaluate(buffer.toString()))
                } catch (e: Exception) {
                    "Error"
                }
                buffer.clear()
            }
            else -> {
                buffer.append(key)
                display = buffer.toString()
            }
        }
    }

    private fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("bad result")
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }

    private var pos = 0
    private lateinit var input: String

    fun evaluate(expression: String): Double {
        input = expression.replace(" ", "")
        pos = 0
        if (input.isEmpty()) return 0.0
        val result = parseSum()
        if (pos != input.length) throw IllegalArgumentException("trailing input")
        return result
    }

    private fun parseSum(): Double {
        var value = parseProduct()
        while (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
            val op = input[pos++]
            val rhs = parseProduct()
            value = if (op == '+') value + rhs else value - rhs
        }
        return value
    }

    private fun parseProduct(): Double {
        var value = parseAtom()
        while (pos < input.length && (input[pos] == '*' || input[pos] == '/')) {
            val op = input[pos++]
            val rhs = parseAtom()
            value = if (op == '*') value * rhs else value / rhs
        }
        return value
    }

    private fun parseAtom(): Double {
        if (pos < input.length && input[pos] == '(') {
            pos++
            val value = parseSum()
            check(pos < input.length && input[pos] == ')') { "missing )" }
            pos++
            return value
        }
        val start = pos
        if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        check(start != pos) { "expected number" }
        return input.substring(start, pos).toDouble()
    }
}
