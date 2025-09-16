package com.starmaurya.whiteboard.models

// Shape item (currently only SQUARE, but extensible for RECT, CIRCLE, etc.)
data class SerializableShape(
    val type: String = "SQUARE",
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float,
    val color: Int,
    val strokeWidth: Float
)
