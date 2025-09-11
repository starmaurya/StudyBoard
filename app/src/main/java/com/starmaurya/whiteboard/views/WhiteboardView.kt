package com.starmaurya.whiteboard.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.core.graphics.createBitmap
import com.google.gson.Gson
import com.google.gson.GsonBuilder

// Simple container for a committed stroke
data class Stroke(val path: Path, val paint: Paint)

// Serializable model for JSON
data class SerializablePoint(val x: Float, val y: Float)
data class SerializableStroke(
    val points: List<SerializablePoint>,
    val color: Int,
    val strokeWidth: Float
)

data class BoardPayload(
    val strokes: List<SerializableStroke>,
    val width: Int,
    val height: Int
)

class WhiteboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val drawPaint = Paint().apply {
        color = 0xFF000000.toInt()   // default black pen
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private var isEraserMode = false

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

    // Add this function inside your WhiteboardView class
    fun exportDrawingAsJsonString(pretty: Boolean = true, stepPx: Float = 5f): String? {
        return try {
            // helper: sample Path into points at approx stepPx distance
            fun samplePathPoints(path: Path, step: Float = stepPx): List<SerializablePoint> {
                val result = ArrayList<SerializablePoint>()
                val pm = PathMeasure(path, false)
                val pos = FloatArray(2)
                do {
                    val len = pm.length
                    if (len > 0f) {
                        var distance = 0f
                        while (distance <= len) {
                            pm.getPosTan(distance, pos, null)
                            result.add(SerializablePoint(pos[0], pos[1]))
                            distance += step
                        }
                    }
                } while (pm.nextContour())
                return result
            }

            // map your internal strokes -> serializable strokes
            val serialStrokes = strokes.map { s ->
                val pts = samplePathPoints(s.path, stepPx)
                SerializableStroke(points = pts, color = s.paint.color, strokeWidth = s.paint.strokeWidth)
            }

            val payload = BoardPayload(
                strokes = serialStrokes,
                width = measuredWidth.coerceAtLeast(1),
                height = measuredHeight.coerceAtLeast(1)
            )

            val gson: Gson = if (pretty) GsonBuilder().setPrettyPrinting().create() else Gson()
            gson.toJson(payload)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    // 👉 New: toggle eraser mode
    fun setEraser(enabled: Boolean) {
        isEraserMode = enabled
        if (enabled) {
            drawPaint.color = Color.WHITE           // background color = white
            drawPaint.strokeWidth = 30f             // eraser thicker
        } else {
            drawPaint.color = Color.BLACK           // back to black pen
            drawPaint.strokeWidth = 8f
        }
    }
}

//<a href="https://www.flaticon.com/free-icons/undo" title="undo icons">Undo icons created by Freepik - Flaticon</a>
//<a href="https://www.flaticon.com/free-icons/previous" title="previous icons">Previous icons created by Any Icon - Flaticon</a>
//<a href="https://www.flaticon.com/free-icons/save" title="save icons">Save icons created by Bharat Icons - Flaticon</a>
//<a href="https://www.flaticon.com/free-icons/eraser" title="eraser icons">Eraser icons created by Freepik - Flaticon</a>
