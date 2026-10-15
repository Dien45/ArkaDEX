package com.ardex.dex.apps

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/** Built-in Browser: minimal WebView with a URL bar. */
class BrowserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(createView(this))
    }

    companion object {
        private const val HOME = "https://www.google.com"

        /**
         * Stops and destroys the WebView held by a window built with
         * [createView]. Closing the window only removes the view from the
         * WindowManager, which does NOT end the renderer process: an undestroyed
         * WebView keeps it alive and leaks one per browser window opened.
         */
        fun releaseView(node: View) {
            if (node is ViewGroup) {
                for (i in 0 until node.childCount) releaseView(node.getChildAt(i))
            }
            if (node is WebView) {
                node.stopLoading()
                (node.parent as? ViewGroup)?.removeView(node)
                node.destroy()
            }
        }

        /** Builds the browser UI; reusable inside overlay windows. */
        @SuppressLint("SetJavaScriptEnabled")
        fun createView(context: Context): View {
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val bar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val urlBox = EditText(context).apply {
                hint = "Search or URL"
                setText(HOME)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val web = WebView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
            fun navigate(raw: String) {
                val trimmed = raw.trim()
                val url = when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    "." in trimmed && " " !in trimmed -> "https://$trimmed"
                    else -> "https://www.google.com/search?q=" + android.net.Uri.encode(trimmed)
                }
                web.loadUrl(url)
            }
            val go = Button(context).apply {
                text = "Go"
                setOnClickListener { navigate(urlBox.text.toString()) }
            }
            urlBox.setOnEditorActionListener { _, _, _ ->
                navigate(urlBox.text.toString())
                true
            }
            bar.addView(urlBox)
            bar.addView(go)
            root.addView(bar)
            root.addView(web)
            web.loadUrl(HOME)
            return root
        }
    }
}
