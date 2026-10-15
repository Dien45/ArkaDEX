package com.ardex.dex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Reusable panel component for ArkaDEX.
 * A [FrameLayout] (so children like titles can be added) with freeform
 * placement, resizing and ClayMorphism styling.
 */
class Panel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var panelWidth: Int = 400,
    var panelHeight: Int = 600
) : FrameLayout(context, attrs) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // ClayMorphism colors (defaults mirror assets/theme.yaml).
    var surfaceColor = Color.parseColor("#F0F4FC")
    var shadowLightColor = Color.parseColor("#FFFFFF")
    var shadowDarkColor = Color.parseColor("#A3BFFA")
    var cornerRadius = 20f

    init {
        setWillNotDraw(false)
        // Software layer so the offset shadow rects render crisply.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> minOf(panelWidth, widthSize)
            else -> panelWidth
        }
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(panelHeight, heightSize)
            else -> panelHeight
        }
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val rect = RectF(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())
        val shadowOffset = 2f
        // Light shadow (bottom-right).
        paint.color = shadowLightColor
        canvas.drawRoundRect(
            rect.left + shadowOffset, rect.top + shadowOffset,
            rect.right + shadowOffset, rect.bottom + shadowOffset,
            cornerRadius, cornerRadius, paint
        )
        // Dark shadow (top-left).
        paint.color = shadowDarkColor
        canvas.drawRoundRect(
            rect.left - shadowOffset, rect.top - shadowOffset,
            rect.right - shadowOffset, rect.bottom - shadowOffset,
            cornerRadius, cornerRadius, paint
        )
        // Main surface.
        paint.color = surfaceColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        super.onDraw(canvas)
    }

    /** Adds a title label on top of the panel surface. */
    fun addTitle(text: String) {
        val clean = text.trim().replace("\\s+".toRegex(), " ").ifBlank { "" }
        val titleTextView = TextView(context).apply {
            this.text = clean
            textSize = 16f
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#2D3436"))
        }
        addView(titleTextView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
