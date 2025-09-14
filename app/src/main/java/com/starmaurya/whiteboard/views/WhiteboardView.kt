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
import com.google.gson.GsonBuilder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ---------- Models ----------
data class Stroke(val path: Path, val paint: Paint)
data class TextItem(val x: Float, val y: Float, val text: String, val paint: Paint)
data class ShapeItem(var left: Float, var top: Float, var right: Float, var bottom: Float, val paint: Paint)

// Serializable models for JSON export
data class SerializablePoint(val x: Float, val y: Float)
data class SerializableStroke(val points: List<SerializablePoint>, val color: Int, val strokeWidth: Float)
data class SerializableText(val x: Float, val y: Float, val text: String, val color: Int, val textSize: Float)
data class SerializableShape(val type: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val strokeWidth: Float)
data class BoardPayload(
    val strokes: List<SerializableStroke>,
    val texts: List<SerializableText>,
    val shapes: List<SerializableShape>,
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

    // backup for restoring pen after eraser
    private var backupPaint: Paint = Paint(drawPaint)

    // tool modes
    enum class ToolMode { PEN, ERASER, SHAPE, TEXT }
    private var currentTool: ToolMode = ToolMode.PEN

    // --- state containers ---
    private val strokes = ArrayList<Stroke>()
    private val undoneStrokes = ArrayList<Stroke>()

    private val textItems = ArrayList<TextItem>()
    private val undoneTextItems = ArrayList<TextItem>()

    private val shapeItems = ArrayList<ShapeItem>()
    private val undoneShapeItems = ArrayList<ShapeItem>()

    // current in-progress path (for pen/eraser)
    private var currentPath = Path()

    // transient in-progress shape (for drag-to-size)
    private var currentShapeItem: ShapeItem? = null
    private var isShapeDragging: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f
    private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop

    // defaults for shapes & text
    private var defaultSquareSizeDp = 120f
    private var defaultTextSizePx = 48f
    private var enforcePerfectSquare = true

    // ----------------- drawing -----------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1) draw committed shapes first (so strokes/eraser can overlay them)
        for (shape in shapeItems) {
            canvas.drawRect(shape.left, shape.top, shape.right, shape.bottom, shape.paint)
        }

        // 2) draw all committed strokes (pen + erase strokes). Erase strokes will hide shapes now if drawn on top.
        for (s in strokes) {
            canvas.drawPath(s.path, s.paint)
        }

        // 3) draw committed text items (text on top of strokes/shapes)
        for (t in textItems) {
            canvas.drawText(t.text, t.x, t.y, t.paint)
        }

        // 4) draw the in-progress path on top (pen / eraser)
        canvas.drawPath(currentPath, drawPaint)

        // 5) draw current in-progress shape (preview) on top of everything
        currentShapeItem?.let { s ->
            canvas.drawRect(s.left, s.top, s.right, s.bottom, s.paint)
        }
    }


    // ----------------- touch handling -----------------
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                isShapeDragging = false

                // If shape tool: start a transient shape (not committed yet)
                if (currentTool == ToolMode.SHAPE) {
                    val paintCopy = Paint(drawPaint).apply { style = Paint.Style.STROKE }
                    currentShapeItem = ShapeItem(left = x, top = y, right = x, bottom = y, paint = paintCopy)
                    // don't commit now, wait for ACTION_MOVE/ACTION_UP
                    undoneShapeItems.clear()
                    invalidate()
                    return true
                }

                // If text tool: no automatic placement here — addText should be called from dialog
                if (currentTool == ToolMode.TEXT) {
                    // consume touch if you want (or return false to let fragment handle)
                    return true
                }

                // PEN or ERASER: start path
                if (currentTool == ToolMode.PEN || currentTool == ToolMode.ERASER) {
                    currentPath = Path()
                    currentPath.moveTo(x, y)
                    undoneStrokes.clear()
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                // shape live update (if started)
                if (currentTool == ToolMode.SHAPE && currentShapeItem != null) {
                    val dx = x - downX
                    val dy = y - downY
                    if (!isShapeDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isShapeDragging = true
                    }

                    currentShapeItem?.let { s ->
                        if (enforcePerfectSquare) {
                            // compute side with sign preserved
                            val side = max(abs(dx), abs(dy))
                            val sx = if (dx < 0) -side else side
                            val sy = if (dy < 0) -side else side
                            s.right = s.left + sx
                            s.bottom = s.top + sy
                        } else {
                            s.right = x
                            s.bottom = y
                        }
                    }
                    invalidate()
                    return true
                }

                // PEN/ERASER drawing
                if (currentTool == ToolMode.PEN || currentTool == ToolMode.ERASER) {
                    currentPath.lineTo(x, y)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // commit shape on UP (either tap => default size, or drag => sized)
                if (currentTool == ToolMode.SHAPE && currentShapeItem != null) {
                    val s = currentShapeItem!!
                    if (!isShapeDragging) {
                        // treat as tap — place default-size square centered at downX/downY
                        val sidePx = dpToPx(defaultSquareSizeDp)
                        val half = sidePx / 2f
                        val left = downX - half
                        val top = downY - half
                        val right = downX + half
                        val bottom = downY + half
                        s.left = left; s.top = top; s.right = right; s.bottom = bottom
                    } else {
                        // normalize coords (left <= right, top <= bottom)
                        val l = min(s.left, s.right)
                        val r = max(s.left, s.right)
                        val t = min(s.top, s.bottom)
                        val b = max(s.top, s.bottom)
                        s.left = l; s.top = t; s.right = r; s.bottom = b
                    }

                    // commit the shape and clear transient
                    shapeItems.add(s)
                    currentShapeItem = null
                    isShapeDragging = false
                    // clear redo stack for shapes
                    undoneShapeItems.clear()
                    invalidate()
                    return true
                }

                // PEN/ERASER commit path on UP
                if (currentTool == ToolMode.PEN || currentTool == ToolMode.ERASER) {
                    val committedPath = Path(currentPath)
                    val paintCopy = Paint(drawPaint)
                    strokes.add(Stroke(committedPath, paintCopy))
                    currentPath.reset()
                    invalidate()
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }

    // ---------- JSON export (strokes + texts + shapes) ----------
    /**
     * Export current board as a JSON string.
     * Includes sampled stroke points and text + shape items.
     */
    fun exportDrawingAsJsonString(pretty: Boolean = true, stepPx: Float = 5f): String? {
        return try {
            // helper: sample a Path into points
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

            val serialStrokes = strokes.map { s ->
                val pts = samplePathPoints(s.path, stepPx)
                SerializableStroke(points = pts, color = s.paint.color, strokeWidth = s.paint.strokeWidth)
            }

            val serialTexts = textItems.map { t ->
                SerializableText(x = t.x, y = t.y, text = t.text, color = t.paint.color, textSize = t.paint.textSize)
            }

            val serialShapes = shapeItems.map { s ->
                SerializableShape(
                    type = "SQUARE",
                    left = s.left, top = s.top, right = s.right, bottom = s.bottom,
                    color = s.paint.color, strokeWidth = s.paint.strokeWidth
                )
            }

            val payload = BoardPayload(
                strokes = serialStrokes,
                texts = serialTexts,
                shapes = serialShapes,
                width = measuredWidth.coerceAtLeast(1),
                height = measuredHeight.coerceAtLeast(1)
            )

            val gson = if (pretty) GsonBuilder().setPrettyPrinting().create() else GsonBuilder().create()
            gson.toJson(payload)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Adds text to the canvas.
     * If x/y are null, text will be added approximately centered.
     */
    fun addText(text: String, x: Float? = null, y: Float? = null) {
        if (text.isBlank()) return

        val cx = x ?: (width / 2f)
        val cy = y ?: (height / 2f) + defaultTextSizePx / 2f

        val p = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = drawPaint.color
            textSize = defaultTextSizePx
        }

        val t = TextItem(cx, cy, text, p)
        textItems.add(t)
        undoneTextItems.clear()
        invalidate()
    }

    // ----------------- undo / redo (strokes -> text -> shapes) -----------------
    fun undo() {
        if (strokes.isNotEmpty()) {
            val s = strokes.removeAt(strokes.lastIndex)
            undoneStrokes.add(s)
            invalidate()
            return
        }
        if (textItems.isNotEmpty()) {
            val t = textItems.removeAt(textItems.lastIndex)
            undoneTextItems.add(t)
            invalidate()
            return
        }
        if (shapeItems.isNotEmpty()) {
            val sh = shapeItems.removeAt(shapeItems.lastIndex)
            undoneShapeItems.add(sh)
            invalidate()
            return
        }
    }

    fun redo() {
        if (undoneTextItems.isNotEmpty()) {
            val t = undoneTextItems.removeAt(undoneTextItems.lastIndex)
            textItems.add(t)
            invalidate()
            return
        }
        if (undoneStrokes.isNotEmpty()) {
            val s = undoneStrokes.removeAt(undoneStrokes.lastIndex)
            strokes.add(s)
            invalidate()
            return
        }
        if (undoneShapeItems.isNotEmpty()) {
            val sh = undoneShapeItems.removeAt(undoneShapeItems.lastIndex)
            shapeItems.add(sh)
            invalidate()
            return
        }
    }

    fun clearBoard() {
        strokes.clear()
        undoneStrokes.clear()
        textItems.clear()
        undoneTextItems.clear()
        shapeItems.clear()
        undoneShapeItems.clear()
        currentPath.reset()
        currentShapeItem = null
        isShapeDragging = false
        invalidate()
    }

    /**
     * Switch tool centrally. This preserves/ restores pen settings when entering/exiting ERASER.
     * Use this from Fragment to change modes.
     */
    fun setToolMode(mode: ToolMode) {
        if (currentTool == mode) return

        // leaving eraser -> restore backup
        if (currentTool == ToolMode.ERASER && mode != ToolMode.ERASER) {
            drawPaint.color = backupPaint.color
            drawPaint.strokeWidth = backupPaint.strokeWidth
            drawPaint.style = backupPaint.style
            drawPaint.isAntiAlias = backupPaint.isAntiAlias
            drawPaint.strokeJoin = backupPaint.strokeJoin
            drawPaint.strokeCap = backupPaint.strokeCap
        }

        // entering eraser -> backup current pen and set eraser paint
        if (mode == ToolMode.ERASER) {
            backupPaint = Paint(drawPaint)
            drawPaint.color = Color.WHITE
            drawPaint.strokeWidth = (backupPaint.strokeWidth * 3f).coerceAtLeast(24f)
            drawPaint.style = Paint.Style.STROKE
        }

        currentTool = mode

        // clear transient drawing state (prevents stale paths/previews)
        currentPath.reset()
        currentShapeItem = null
        isShapeDragging = false

        invalidate()
    }

    fun hasUndo(): Boolean {
        return strokes.isNotEmpty() || textItems.isNotEmpty() || shapeItems.isNotEmpty()
    }

    fun hasRedo(): Boolean {
        return undoneStrokes.isNotEmpty() || undoneTextItems.isNotEmpty() || undoneShapeItems.isNotEmpty()
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }
}

