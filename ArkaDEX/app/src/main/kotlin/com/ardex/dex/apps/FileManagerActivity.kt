package com.ardex.dex.apps

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Built-in File Manager: browses app-private dirs (no storage permission needed). */
class FileManagerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView(this))
    }

    companion object {
        /** Builds the file-browser UI; reusable inside overlay windows. */
        fun createView(context: Context): View {
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val pathView = TextView(context).apply {
                textSize = 12f
                setPadding(8, 8, 8, 8)
            }
            val list = ListView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            }
            val roots = listOfNotNull(
                context.filesDir,
                context.getExternalFilesDir(null)
            ).distinctBy { it.absolutePath }

            var current: File = roots.firstOrNull() ?: context.filesDir

            fun entriesOf(dir: File): List<File> {
                val entries = mutableListOf<File>()
                if (dir.parentFile != null && roots.none { it.absolutePath == dir.absolutePath }) {
                    entries.add(dir.parentFile!!)
                }
                dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?.let { entries.addAll(it) }
                return entries
            }

            fun refresh() {
                pathView.text = current.absolutePath
                val entries = entriesOf(current)
                list.adapter = object : ArrayAdapter<File>(
                    context, android.R.layout.simple_list_item_1, entries
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        val file = getItem(position)!!
                        val isParent = file == current.parentFile
                        val suffix = when {
                            isParent -> ""
                            file.isDirectory -> "/"
                            else -> " (${file.length()} B)"
                        }
                        view.text = (if (isParent) "… " else "") + file.name + suffix
                        return view
                    }
                }
                list.setOnItemClickListener { _, _, position, _ ->
                    val picked = entries[position]
                    if (picked.isDirectory) {
                        current = picked
                        refresh()
                    }
                }
            }

            val up = Button(context).apply {
                text = "Up"
                setOnClickListener {
                    current.parentFile?.let {
                        if (current.absolutePath.length > roots.minOf { r -> r.absolutePath.length }) {
                            current = it
                        } else {
                            current = roots.first()
                        }
                        refresh()
                    }
                }
            }
            root.addView(pathView)
            root.addView(list)
            root.addView(up)
            refresh()
            return root
        }
    }
}
