package com.ardex.dex.apps

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Built-in Calculator: four operations with precedence, no dependencies. */
class CalculatorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView(this))
    }

    companion object {
        /** Builds the calculator UI; reusable inside overlay windows. */
        fun createView(context: Context): View {
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val display = TextView(context).apply {
                text = "0"
                textSize = 32f
                gravity = Gravity.END
                setPadding(16, 16, 16, 16)
            }
            val engine = Engine()
            val grid = GridLayout(context).apply {
                columnCount = 4
                rowCount = 5
            }
            val keys = listOf(
                "C", "(", ")", "/",
                "7", "8", "9", "*",
                "4", "5", "6", "-",
                "1", "2", "3", "+",
                "0", ".", "⌫", "="
            )
            for (key in keys) {
                val button = Button(context).apply {
                    text = key
                    textSize = 18f
                    setOnClickListener {
                        engine.press(key)
                        display.text = engine.display
                    }
                }
                grid.addView(button)
            }
            root.addView(display)
            root.addView(grid)
            return root
        }

        /** Tiny expression engine: recursive-descent parser (+ - * / parens). */
        class Engine {
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
    }
}
