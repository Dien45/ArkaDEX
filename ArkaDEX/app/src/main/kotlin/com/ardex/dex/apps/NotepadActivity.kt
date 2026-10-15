package com.ardex.dex.apps

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Built-in Notepad: plain-text notes stored in app-private storage. */
class NotepadActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView(this))
    }

    companion object {
        private const val FILE = "notes.txt"

        fun noteFile(context: Context): File = File(context.filesDir, FILE)

        /** Builds the Notepad UI; reusable inside overlay windows. */
        fun createView(context: Context): View {
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val editor = EditText(context).apply {
                hint = "Type notes here…"
                setLines(12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            try {
                val file = noteFile(context)
                if (file.exists()) editor.setText(file.readText())
            } catch (e: Exception) {
                // Start with a blank note when nothing is saved yet.
            }
            val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val save = Button(context).apply {
                text = "Save"
                setOnClickListener {
                    try {
                        noteFile(context).writeText(editor.text.toString())
                        Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val clear = Button(context).apply {
                text = "Clear"
                setOnClickListener { editor.setText("") }
            }
            bar.addView(save)
            bar.addView(clear)
            root.addView(editor)
            root.addView(bar)
            return root
        }
    }
}
