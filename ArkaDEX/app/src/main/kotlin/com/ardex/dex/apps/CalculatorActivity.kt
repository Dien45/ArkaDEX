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
            val engine = CalculatorEngine()
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
    }
}
