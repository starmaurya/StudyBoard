package com.starmaurya.whiteboard.views


import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class WhiteboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val drawPaint = Paint().apply {
        color = 0xFF000000.toInt()   // Black color
        style = Paint.Style.STROKE   // Stroke style (no fill)
        strokeWidth = 8f             // Line thickness
        isAntiAlias = true           // Smooth edges
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the current path on the canvas
        canvas.drawPath(path, drawPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)   // Start point
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(x, y)   // Draw line to new point
            }
        }
        invalidate() // Redraw view
        return true
    }
}
