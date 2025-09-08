package com.starmaurya.whiteboard.views



import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

// Simple container for a committed stroke
data class Stroke(val path: Path, val paint: Paint)

class WhiteboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val drawPaint = Paint().apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // list of committed strokes
    private val strokes = ArrayList<Stroke>()
    private val undoneStrokes = ArrayList<Stroke>()  // undone strokes
    // current in-progress path
    private var currentPath = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw all committed strokes
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }

        // draw the in-progress path on top
        canvas.drawPath(currentPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // start a new current path
                currentPath = Path()
                currentPath.moveTo(x, y)
                // new stroke starts → redo stack should be cleared
                undoneStrokes.clear()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentPath.lineTo(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // commit: store a *copy* of the path and a copy of the paint
                val committedPath = Path(currentPath)            // copy path
                val paintCopy = Paint(drawPaint)                 // copy paint settings
                strokes.add(Stroke(committedPath, paintCopy))    // push to list
                currentPath.reset()                              // clear working path
            }
        }

        invalidate()
        return true
    }

    // Undo last committed stroke
    fun undo() {
        if (strokes.isNotEmpty()) {
            val stroke = strokes.removeAt(strokes.lastIndex)
            undoneStrokes.add(stroke)
            invalidate()
        }
    }

    fun redo() {
        if (undoneStrokes.isNotEmpty()) {
            val stroke = undoneStrokes.removeAt(undoneStrokes.lastIndex)
            strokes.add(stroke)
            invalidate()
        }
    }

    // Clear everything
    fun clearBoard() {
        strokes.clear()
        undoneStrokes.clear()
        currentPath.reset()
        invalidate()
    }

    // Optional helpers to change paint from outside (palette)
    fun setStrokeColor(color: Int) {
        drawPaint.color = color
    }

    fun setStrokeWidth(widthPx: Float) {
        drawPaint.strokeWidth = widthPx
    }

    // Optionally expose stroke count
    fun strokeCount(): Int = strokes.size
}

