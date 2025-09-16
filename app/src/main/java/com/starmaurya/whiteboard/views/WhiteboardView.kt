package com.starmaurya.whiteboard.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.starmaurya.whiteboard.models.BoardPayload
import com.starmaurya.whiteboard.models.SerializablePoint
import com.starmaurya.whiteboard.models.SerializableShape
import com.starmaurya.whiteboard.models.SerializableStroke
import com.starmaurya.whiteboard.models.SerializableText
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * WhiteboardView
 *
 * - Pen (freehand)
 * - Eraser (implemented as white thick stroke)
 * - Shapes: square (tap => default size, drag => sized)
 * - Text: added via addText(...)
 *
 * Behaviour:
 * - If a BoardActionListener is set, commits (strokes/shapes/texts) are forwarded to it.
 * - Otherwise the view keeps local lists (so it can run standalone).
 *
 * This keeps drawing logic stable for Phase 2 MVVM integration (no erase-as-deletion here).
 */
class WhiteboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // base paint for pen
    private val drawPaint = Paint().apply {
        color = 0xFF000000.toInt()   // default black pen
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // backup for restoring pen after eraser
    private var backupPaint: Paint = Paint(drawPaint)

    // Tool modes
    enum class ToolMode { PEN, ERASER, SHAPE, TEXT }
    private var currentTool: ToolMode = ToolMode.PEN

    // --- state containers (authoritative when ViewModel not present) ---
    private var strokes = ArrayList<SerializableStroke>()
    private var texts = ArrayList<SerializableText>()
    private var shapes = ArrayList<SerializableShape>()

    // transient preview state
    private var currentPath = Path()
    private var currentShapeItem: SerializableShape? = null
    private var isShapeDragging: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    private var enforcePerfectSquare = true
    private var defaultSquareSizeDp = 120f
    private var defaultTextSizePx = 48f

    // Listener to forward commits to ViewModel (if set)
    interface BoardActionListener {
        fun onStrokeCommitted(stroke: SerializableStroke)
        fun onShapeCommitted(shape: SerializableShape)
        fun onTextCommitted(text: SerializableText)
    }
    private var actionListener: BoardActionListener? = null
    fun setBoardActionListener(l: BoardActionListener?) { actionListener = l }

    // ----------------- drawing -----------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw shapes first
        for (s in shapes) {
            val p = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = s.color
                strokeWidth = s.strokeWidth
            }
            canvas.drawRect(s.left, s.top, s.right, s.bottom, p)
        }

        // draw strokes
        for (st in strokes) {
            val path = pathFromPoints(st.points)
            val p = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = st.color
                strokeWidth = st.strokeWidth
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawPath(path, p)
        }

        // draw texts
        for (t in texts) {
            val p = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = t.color
                textSize = t.textSize
            }
            canvas.drawText(t.text, t.x, t.y, p)
        }

        // in-progress stroke preview (pen or eraser)
        canvas.drawPath(currentPath, drawPaint)

        // in-progress shape preview
        currentShapeItem?.let { s ->
            val p = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = s.color
                strokeWidth = s.strokeWidth
            }
            canvas.drawRect(s.left, s.top, s.right, s.bottom, p)
        }
    }

    // helper: build Path from points
    private fun pathFromPoints(points: List<SerializablePoint>): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            var i = 1
            while (i < points.size) {
                path.lineTo(points[i].x, points[i].y)
                i++
            }
        }
        return path
    }

    // ----------------- touch handling -----------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; isShapeDragging = false

                if (currentTool == ToolMode.SHAPE) {
                    // start a transient square
                    currentShapeItem = SerializableShape(
                        type = "SQUARE",
                        left = x, top = y, right = x, bottom = y,
                        color = drawPaint.color, strokeWidth = drawPaint.strokeWidth
                    )
                    invalidate()
                    return true
                }

                if (currentTool == ToolMode.TEXT) {
                    // text insertion handled by fragment/dialog; consume touch optionally
                    return true
                }

                // PEN or ERASER start
                currentPath = Path()
                currentPath.moveTo(x, y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (currentTool == ToolMode.SHAPE && currentShapeItem != null) {
                    val dx = x - downX; val dy = y - downY
                    if (!isShapeDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) isShapeDragging = true
                    currentShapeItem?.let { s ->
                        if (enforcePerfectSquare) {
                            val side = max(abs(dx), abs(dy))
                            val sx = if (dx < 0) -side else side
                            val sy = if (dy < 0) -side else side
                            s.right = s.left + sx
                            s.bottom = s.top + sy
                        } else {
                            s.right = x; s.bottom = y
                        }
                    }
                    invalidate()
                    return true
                }

                if (currentTool == ToolMode.PEN || currentTool == ToolMode.ERASER) {
                    currentPath.lineTo(x, y)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // commit shape
                if (currentTool == ToolMode.SHAPE && currentShapeItem != null) {
                    val s = currentShapeItem!!
                    if (!isShapeDragging) {
                        val side = dpToPx(defaultSquareSizeDp)
                        val half = side / 2f
                        s.left = downX - half; s.top = downY - half
                        s.right = downX + half; s.bottom = downY + half
                    } else {
                        val l = min(s.left, s.right); val r = max(s.left, s.right)
                        val t = min(s.top, s.bottom); val b = max(s.top, s.bottom)
                        s.left = l; s.top = t; s.right = r; s.bottom = b
                    }

                    // forward or store locally
                    val serialShape = SerializableShape(
                        type = s.type,
                        left = s.left, top = s.top, right = s.right, bottom = s.bottom,
                        color = s.color, strokeWidth = s.strokeWidth
                    )
                    actionListener?.onShapeCommitted(serialShape) ?: run { shapes.add(serialShape) }

                    currentShapeItem = null
                    isShapeDragging = false
                    invalidate()
                    return true
                }

                // commit PEN or ERASER as a stroke (eraser is visualized as a white thick stroke)
                if (currentTool == ToolMode.PEN || currentTool == ToolMode.ERASER) {
                    val sampled = samplePathPoints(currentPath, stepPx = 5f)
                    if (sampled.isNotEmpty()) {
                        val stroke = SerializableStroke(points = sampled, color = drawPaint.color, strokeWidth = drawPaint.strokeWidth)
                        actionListener?.onStrokeCommitted(stroke) ?: run { strokes.add(stroke) }
                    }
                    currentPath.reset()
                    invalidate()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    // sampling helper (samples path into points along length)
    private fun samplePathPoints(path: Path, stepPx: Float = 5f): List<SerializablePoint> {
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
                    distance += stepPx
                }
            }
        } while (pm.nextContour())
        return result
    }

    // render from ViewModel/BoardPayload (sets internal lists)
    fun renderBoard(payload: BoardPayload) {
        // If payload has different width/height, caller may scale beforehand.
        strokes = ArrayList(payload.strokes)
        texts = ArrayList(payload.texts)
        shapes = ArrayList(payload.shapes)
        invalidate()
    }

    // external helpers
    fun addText(text: String, x: Float? = null, y: Float? = null) {
        if (text.isBlank()) return
        val cx = x ?: (width / 2f)
        val cy = y ?: (height / 2f) + defaultTextSizePx / 2f
        val t = SerializableText(x = cx, y = cy, text = text, color = drawPaint.color, textSize = defaultTextSizePx)
        actionListener?.onTextCommitted(t) ?: run { texts.add(t) }
        invalidate()
    }

    fun clearBoard() {
        strokes.clear()
        texts.clear()
        shapes.clear()
        currentPath.reset()
        currentShapeItem = null
        invalidate()
    }

    // set tool mode and manage eraser backup/restore
    fun setToolMode(mode: ToolMode) {
        if (currentTool == mode) return

        // restore pen if leaving eraser
        if (currentTool == ToolMode.ERASER && mode != ToolMode.ERASER) {
            drawPaint.color = backupPaint.color
            drawPaint.strokeWidth = backupPaint.strokeWidth
            drawPaint.style = backupPaint.style
            drawPaint.isAntiAlias = backupPaint.isAntiAlias
            drawPaint.strokeJoin = backupPaint.strokeJoin
            drawPaint.strokeCap = backupPaint.strokeCap
        }

        // entering eraser -> backup and set eraser paint (visual only)
        if (mode == ToolMode.ERASER) {
            backupPaint = Paint(drawPaint)
            drawPaint.color = Color.WHITE
            drawPaint.strokeWidth = (backupPaint.strokeWidth * 2f).coerceAtLeast(24f)
            drawPaint.style = Paint.Style.STROKE
        }

        currentTool = mode

        // clear transient preview state to avoid stale commits
        currentPath.reset()
        currentShapeItem = null
        isShapeDragging = false

        invalidate()
    }

    // convenience checks (for Fragment UI)
    fun hasContent(): Boolean {
        return strokes.isNotEmpty() || texts.isNotEmpty() || shapes.isNotEmpty()
    }

    fun strokeCount(): Int = strokes.size

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
}
