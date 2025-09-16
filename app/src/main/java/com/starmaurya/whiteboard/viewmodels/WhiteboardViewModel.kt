package com.starmaurya.whiteboard.viewmodels


import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.GsonBuilder
import com.starmaurya.whiteboard.models.BoardPayload
import com.starmaurya.whiteboard.models.SerializableShape
import com.starmaurya.whiteboard.models.SerializableStroke
import com.starmaurya.whiteboard.models.SerializableText
import com.starmaurya.whiteboard.repository.FileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SaveResult {
    data class PngSaved(val uri: Uri) : SaveResult()
    data class JsonSaved(val uri: Uri) : SaveResult()
    data class Error(val message: String) : SaveResult()
}

class WhiteboardViewModel(private val repo: FileRepository) : ViewModel() {

    private val _boardState = MutableStateFlow(
        BoardPayload(strokes = emptyList(), texts = emptyList(), shapes = emptyList(), width = 0, height = 0)
    )
    val boardState: StateFlow<BoardPayload> = _boardState

    // simple undo/redo stacks of payload snapshots
    private val undoStack = ArrayList<BoardPayload>()
    private val redoStack = ArrayList<BoardPayload>()

    // events for save results
    private val _events = MutableSharedFlow<SaveResult>()
    val events: SharedFlow<SaveResult> = _events

    private fun pushUndoSnapshot() {
        // snapshot current state
        undoStack.add(_boardState.value.copy())
        redoStack.clear()
    }

    fun addStroke(s: SerializableStroke) {
        pushUndoSnapshot()
        val curr = _boardState.value
        _boardState.value = curr.copy(strokes = curr.strokes + s)
    }

    fun addText(t: SerializableText) {
        pushUndoSnapshot()
        val curr = _boardState.value
        _boardState.value = curr.copy(texts = curr.texts + t)
    }

    fun addShape(sh: SerializableShape) {
        pushUndoSnapshot()
        val curr = _boardState.value
        _boardState.value = curr.copy(shapes = curr.shapes + sh)
    }

    fun clearBoard() {
        pushUndoSnapshot()
        val cur = _boardState.value
        _boardState.value = BoardPayload(emptyList(), emptyList(), emptyList(), cur.width, cur.height)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(_boardState.value.copy())
            _boardState.value = last
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(_boardState.value.copy())
            _boardState.value = next
        }
    }

    fun hasUndo(): Boolean = undoStack.isNotEmpty()
    fun hasRedo(): Boolean = redoStack.isNotEmpty()

    fun updateCanvasSize(width: Int, height: Int) {
        val cur = _boardState.value
        if (cur.width != width || cur.height != height) {
            _boardState.value = cur.copy(width = width, height = height)
        }
    }

    // export JSON string of the board state
    fun exportDrawingAsJsonString(pretty: Boolean = true): String? {
        return try {
            val gson = if (pretty) GsonBuilder().setPrettyPrinting().create() else GsonBuilder().create()
            gson.toJson(_boardState.value)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun savePng(displayName: String, bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                val uri = repo.savePng(displayName, bitmap)
                if (uri != null) _events.emit(SaveResult.PngSaved(uri)) else _events.emit(SaveResult.Error("Fail saving PNG"))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SaveResult.Error("PNG save error: ${e.localizedMessage}"))
            }
        }
    }

    fun saveJson(displayName: String, json: String) {
        viewModelScope.launch {
            try {
                val uri = repo.saveJson(displayName, json)
                if (uri != null) _events.emit(SaveResult.JsonSaved(uri)) else _events.emit(SaveResult.Error("Fail saving JSON"))
            } catch (e: Exception) {
                e.printStackTrace()
                _events.emit(SaveResult.Error("JSON save error: ${e.localizedMessage}"))
            }
        }
    }
}
