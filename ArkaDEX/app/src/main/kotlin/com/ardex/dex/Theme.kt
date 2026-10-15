package com.ardex.dex

import android.content.Context
import android.graphics.Color
import android.util.Log

/**
 * ClayMorphism theme loader for ArkaDEX.
 *
 * Reads `assets/theme.yaml` (flat `section:` groups with indented
 * `key: value` pairs, `#` comments, single-quoted scalars) into a
 * [Theme]. Colors exposed as Android color ints with safe fallbacks so a
 * malformed theme file can never crash the desktop.
 */
data class Theme(
    val colors: Map<String, Int> = emptyMap(),
    val fonts: Map<String, String> = emptyMap(),
    val windowManager: Map<String, String> = emptyMap(),
    val effects: Map<String, String> = emptyMap()
) {
    fun color(name: String, fallback: Int): Int = colors[name] ?: fallback
    fun font(name: String, fallback: String): String = fonts[name] ?: fallback

    companion object {
        private const val TAG = "ArkaDEX-Theme"
        private const val ASSET = "theme.yaml"

        private val FALLBACK = Theme(
            colors = mapOf(
                "primary" to Color.parseColor("#4A90E2"),
                "secondary" to Color.parseColor("#5B7D8B"),
                "accent" to Color.parseColor("#88C0D0"),
                "background" to Color.parseColor("#FAF9FF"),
                "surface" to Color.parseColor("#F0F4FC"),
                "border" to Color.parseColor("#D4D9E2"),
                "text" to Color.parseColor("#2D3436")
            )
        )

        /** Loads the theme, falling back to built-in defaults on any error. */
        fun load(context: Context): Theme {
            return try {
                context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
            } catch (e: Exception) {
                Log.w(TAG, "Using fallback theme (could not load $ASSET)", e)
                FALLBACK
            }
        }

        /** Minimal YAML-subset parser: sections + `key: value`, `#` comments. */
        fun parse(yaml: String): Theme {
            val sections = mutableMapOf<String, MutableMap<String, String>>()
            var current: MutableMap<String, String>? = null
            for (raw in yaml.lines()) {
                val trimmed = raw.trim()
                // Full-line comment or blank.
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                if (!raw.startsWith(" ") && !raw.startsWith("\t") && trimmed.endsWith(":")) {
                    current = sections.getOrPut(trimmed.dropLast(1).trim()) { mutableMapOf() }
                } else {
                    if (!trimmed.contains(":")) continue
                    val key = trimmed.substringBefore(":").trim()
                    val rest = trimmed.substringAfter(":").trim()
                    if (key.isEmpty() || rest.isEmpty()) continue
                    // A value may itself start with '#' (hex colors like
                    // `primary: #4A90E2`); only a '#' after whitespace
                    // outside quotes starts a trailing comment.
                    val value = if (rest.startsWith("#")) {
                        rest.split("\\s+".toRegex(), limit = 2)[0]
                    } else {
                        cutTrailingComment(rest)
                    }.trim().trim('\'', '"')
                    (current ?: sections.getOrPut("") { mutableMapOf() })[key] = value
                }
            }
            val base = FALLBACK.colors.toMutableMap()
            for ((k, v) in sections["colors"].orEmpty()) {
                try {
                    base[k] = Color.parseColor(if (v.startsWith("#")) v else "#$v")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Ignoring invalid color colors.$k=$v")
                }
            }
            return Theme(
                colors = base,
                fonts = sections["fonts"].orEmpty().toMap(),
                windowManager = sections["window_manager"].orEmpty().toMap(),
                effects = sections["effects"].orEmpty().toMap()
            )
        }

        /**
         * Cuts a trailing ` #comment` (hash after whitespace, outside
         * quotes). Hashes inside quotes or at value start (hex colors)
         * are kept.
         */
        private fun cutTrailingComment(value: String): String {
            var quote: Char? = null
            for (i in value.indices) {
                val c = value[i]
                if (quote != null) {
                    if (c == quote) quote = null
                } else if (c == '\'' || c == '"') {
                    quote = c
                } else if (c == '#' && i > 0 && value[i - 1].isWhitespace()) {
                    return value.substring(0, i)
                }
            }
            return value
        }
    }
}
