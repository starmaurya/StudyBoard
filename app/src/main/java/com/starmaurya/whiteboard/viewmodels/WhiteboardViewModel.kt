package com.starmaurya.whiteboard.viewmodels


import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import com.starmaurya.whiteboard.repository.FileRepository

sealed class SaveResult {
    data class PngSaved(val uri: Uri) : SaveResult()
    data class JsonSaved(val uri: Uri) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class WhiteboardViewModel(private val repo: FileRepository) : ViewModel() {

    // one-time events for UI (save success/failure)
    private val _events = MutableSharedFlow<SaveResult>(replay = 0)
    val events: SharedFlow<SaveResult> = _events

    // --- Save PNG (call from Fragment) ---
    fun savePng(displayName: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val uri = repo.savePng(displayName, bitmap)
                if (uri != null) {
                    _events.emit(SaveResult.PngSaved(uri))
                } else {
                    _events.emit(SaveResult.Error("Failed to save PNG"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SaveResult.Error("PNG save error: ${e.localizedMessage}"))
            }
        }
    }

    // --- Save JSON (call from Fragment) ---
    fun saveJson(displayName: String, json: String) {
        viewModelScope.launch {
            try {
                val uri = repo.saveJson(displayName, json)
                if (uri != null) {
                    _events.emit(SaveResult.JsonSaved(uri))
                } else {
                    _events.emit(SaveResult.Error("Failed to save JSON"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SaveResult.Error("JSON save error: ${e.localizedMessage}"))
            }
        }
    }
}
