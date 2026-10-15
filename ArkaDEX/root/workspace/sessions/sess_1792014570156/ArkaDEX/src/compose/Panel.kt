package com.ardex.dex.dextop

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat

/**
 * Reusable panel component ArkaDEX
 * Supports freeform placement, resizing, claymorphism styling
 */
class Panel @JvmOverloads constructor(
    @Nullable context: Context?,
    @Nullable attrs: AttributeSet? = null,
    var width: Int = 400,
    var height: Int = 600
) : View(context, attrs) {

    private val tag = "ArkaDEX-Panel"
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    // Claymorphism colors (from theme.yaml)
    private val surfaceColor = Color.parseColor("#F0F4FC") // light blue-ish background
    private val shadowLightColor = Color.parseColor("#FFFFFF") // white for light shadow
    private val shadowDarkColor = Color.parseColor("#A3BFFa") // soft blue for dark shadow
    private val cornerRadius = 20f
    private val blurRadius = 8f

    init {
        // Log creation (commented out to avoid spam)
        // android.util.Log.d(tag, "Panel created: $width $height")
        setWillNotDraw(false)
        // Enable layer for shadow
        layerType = LAYER_TYPE_SOFTWARE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = width
        val desiredHeight = height
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val measuredWidth = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> Math.min(desiredWidth, widthSize)
            MeasureSpec.UNSPECIFIED -> desiredWidth
            else -> desiredWidth
        }

        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> Math.min(desiredHeight, heightSize)
            MeasureSpec.UNSPECIFIED -> desiredHeight
            else -> desiredHeight
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat())

        // Draw claymorphism background with shadows
        // Light shadow (top-left)
        paint.color = shadowLightColor
        val shadowOffset = 2f
        canvas.drawRoundRect(
            rect.left + shadowOffset,
            rect.top + shadowOffset,
            rect.right + shadowOffset,
            rect.bottom + shadowOffset,
            cornerRadius,
            cornerRadius,
            paint
        )
        // Dark shadow (bottom-right)
        paint.color = shadowDarkColor
        canvas.drawRoundRect(
            rect.left - shadowOffset,
            rect.top - shadowOffset,
            rect.right - shadowOffset,
            rect.bottom - shadowOffset,
            cornerRadius,
            cornerRadius,
            paint
        )
        // Main surface
        paint.color = surfaceColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
    }

    /** Resizes panel */
    fun resize(width: Int, height: Int) {
        this.width = width
        this.height = height
        requestLayout()
        invalidate()
    }

    /** Moves panel within parent */
    fun move(x: Float, y: Float) {
        this.setX(x.toInt())
        this.setY(y.toInt())
    }

    /** Adds title label */
    fun addTitle(text: String) {
        val titleTextView = android.widget.TextView(context).apply {
            this.text = text.trim().replaceAll("\\s+", " ").takeIf { it.isNotBlank() } ?: ""
            setTextSize(16f)
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#2D3436")) // dark gray text from theme
        }
        addView(titleTextView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}