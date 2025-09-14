package com.starmaurya.whiteboard.repository


import android.graphics.Bitmap
import android.net.Uri

interface FileRepository {
    /**
     * Save a PNG bitmap with displayName under Pictures/StudyBoard (or app-specific folder).
     * Returns content Uri on success or null on failure.
     */
    suspend fun savePng(displayName: String, bitmap: Bitmap): Uri?

    /**
     * Save JSON string under Downloads/StudyBoard.
     * Returns content Uri on success or null on failure.
     */
    suspend fun saveJson(displayName: String, json: String): Uri?
}
