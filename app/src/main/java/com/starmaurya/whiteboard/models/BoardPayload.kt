package com.starmaurya.whiteboard.models

// Payload = complete snapshot of board state at a given time
data class BoardPayload(
    val strokes: List<SerializableStroke>,
    val texts: List<SerializableText>,
    val shapes: List<SerializableShape>,
    val width: Int,   // canvas width at capture
    val height: Int   // canvas height at capture
)