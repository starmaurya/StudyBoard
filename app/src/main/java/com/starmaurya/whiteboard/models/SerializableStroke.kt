package com.starmaurya.whiteboard.models

// Stroke = series of sampled points + paint info
data class SerializableStroke(
    val points: List<SerializablePoint>,
    val color: Int,
    val strokeWidth: Float
)
