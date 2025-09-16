package com.starmaurya.whiteboard.models

// Text item (absolute coordinates on canvas)
data class SerializableText(
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val textSize: Float
)
