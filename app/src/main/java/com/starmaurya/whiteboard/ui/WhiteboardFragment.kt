package com.starmaurya.whiteboard.ui

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.starmaurya.whiteboard.R
import com.starmaurya.whiteboard.models.SerializableShape
import com.starmaurya.whiteboard.models.SerializableStroke
import com.starmaurya.whiteboard.models.SerializableText
import com.starmaurya.whiteboard.repository.MediaStoreFileRepository
import com.starmaurya.whiteboard.utils.AppLogger // Added import
import com.starmaurya.whiteboard.viewmodels.SaveResult
import com.starmaurya.whiteboard.viewmodels.WhiteboardViewModel
import com.starmaurya.whiteboard.views.WhiteboardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WhiteboardFragment : Fragment() {

    private var whiteboardView: WhiteboardView? = null
    private var whiteboardViewModel: WhiteboardViewModel? = null

    private lateinit var btnPen: ImageButton
    private lateinit var btnBrush: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnShapes: ImageButton
    private lateinit var btnText: ImageButton

    // Added TAG for AppLogger
    companion object {
        private const val TAG = "WhiteboardFragment"

        @JvmStatic
        fun newInstance() = WhiteboardFragment().apply { arguments = Bundle() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_whiteboard, container, false)
        try {
            whiteboardView = root.findViewById(R.id.whiteboardView)

            whiteboardView?.setBoardActionListener(object : WhiteboardView.BoardActionListener {
                override fun onStrokeCommitted(stroke: SerializableStroke) {
                    try {
                        whiteboardViewModel?.addStroke(stroke)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, Exception("Error in onStrokeCommitted: ${e.localizedMessage} Exception -> $e"))
                    }
                }

                override fun onShapeCommitted(shape: SerializableShape) {
                    try {
                        whiteboardViewModel?.addShape(shape)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, Exception("Error in onShapeCommitted: ${e.localizedMessage} Exception -> $e"))
                    }
                }

                override fun onTextCommitted(text: SerializableText) {
                    try {
                        whiteboardViewModel?.addText(text)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, Exception("Error in onTextCommitted: ${e.localizedMessage} Exception -> $e"))
                    }
                }
            })
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            val toolbar = view.findViewById<Toolbar>(R.id.whiteboard_toolbar)
            (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
            (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
            (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)

            btnPen = view.findViewById(R.id.btn_pen)
            btnBrush = view.findViewById(R.id.btn_brush)
            btnEraser = view.findViewById(R.id.btn_eraser)
            btnShapes = view.findViewById(R.id.btn_shapes)
            btnText = view.findViewById(R.id.btn_text)

            val repo = MediaStoreFileRepository(requireContext().applicationContext)
            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return WhiteboardViewModel(repo) as T
                }
            }
            val vm = ViewModelProvider(this, factory)[WhiteboardViewModel::class.java]
            whiteboardViewModel = vm

            val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Check if the board has any content
                    val hasChanges = vm.boardState.value.let { payload ->
                        payload.strokes.isNotEmpty() || payload.texts.isNotEmpty() || payload.shapes.isNotEmpty()
                    }

                    if (hasChanges) {
                        // If there are changes, show a confirmation dialog
                        AlertDialog.Builder(requireContext())
                            .setTitle("Exit Whiteboard?")
                            .setMessage("Your drawing is not saved. Are you sure you want to exit?")
                            .setPositiveButton("Exit") { _, _ ->
                                // User confirmed. Disable this callback and proceed with the back press.
                                isEnabled = false
                                activity?.onBackPressedDispatcher?.onBackPressed()
                            }
                            .setNegativeButton("Cancel", null) // Do nothing, just dismiss the dialog
                            .show()
                    } else {
                        // No changes, so just exit without a dialog.
                        isEnabled = false
                        activity?.onBackPressedDispatcher?.onBackPressed()
                    }
                }
            }
            // Add the callback to the dispatcher, tied to the fragment's view lifecycle
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)


            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    vm.boardState.collect { payload ->
                        try {
                            whiteboardView?.renderBoard(payload)
                        } catch (e: Exception) {
                            AppLogger.e(TAG, Exception("Error rendering board: ${e.localizedMessage}  Exception -> $e", e))
                        }
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    vm.events.collect { ev ->
                        when (ev) {
                            is SaveResult.PngSaved -> {
                                Toast.makeText(requireContext(), "Saved PNG: ${ev.uri}", Toast.LENGTH_LONG).show()
                            }
                            is SaveResult.JsonSaved -> {
                                Toast.makeText(requireContext(), "Saved JSON: ${ev.uri}", Toast.LENGTH_LONG).show()
                            }
                            is SaveResult.Error -> {
                                val errorMessage = "Save failed: ${ev.message}"
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
                                AppLogger.e(TAG, Exception(ev.message)) // Pass original cause if available
                            }
                        }
                    }
                }
            }

            whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
            highlightTool(btnPen)

            btnPen.setOnClickListener {
                try {
                    whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
                    highlightTool(btnPen)
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }
            btnBrush.setOnClickListener {
                Toast.makeText(requireContext(), "coming soon", Toast.LENGTH_SHORT).show()
            }
            btnEraser.setOnClickListener {
                try {
                    whiteboardView?.setToolMode(WhiteboardView.ToolMode.ERASER)
                    highlightTool(btnEraser)
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }
            btnShapes.setOnClickListener {
                try {
                    whiteboardView?.setToolMode(WhiteboardView.ToolMode.SHAPE)
                    highlightTool(btnShapes)
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }
            btnText.setOnClickListener {
                try {
                    whiteboardView?.setToolMode(WhiteboardView.ToolMode.TEXT)
                    highlightTool(btnText)
                    onTextButtonClicked()
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }

            val undoBtn = view.findViewById<ImageButton>(R.id.action_undo_centered)
            val redoBtn = view.findViewById<ImageButton>(R.id.action_redo_centered)
            undoBtn.setOnClickListener {
                try {
                    if (vm.hasUndo()) vm.undo() else Toast.makeText(requireContext(), "No drawings to undo", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }
            redoBtn.setOnClickListener {
                try {
                    if (vm.hasRedo()) vm.redo() else Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    AppLogger.e(TAG, e)
                }
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
        setHasOptionsMenu(true)
    }

    private fun highlightTool(selected: ImageButton) {
        try {
            val all = listOf(btnPen, btnBrush, btnEraser, btnShapes, btnText)
            all.forEach { it.isSelected = false }
            selected.isSelected = true
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.whiteboard_toolbar_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return try {
            when (item.itemId) {
                android.R.id.home -> {
                    activity?.onBackPressedDispatcher?.onBackPressed()
                    true
                }
                R.id.action_save -> {
                    if (whiteboardViewModel?.boardState?.value?.let { payload ->
                            payload.strokes.isNotEmpty() || payload.texts.isNotEmpty() || payload.shapes.isNotEmpty()
                        } == true
                    ) {
                        showSaveChoiceDialog()
                    } else {
                        Toast.makeText(requireContext(), "Nothing to save", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> super.onOptionsItemSelected(item)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
            super.onOptionsItemSelected(item) // Still call super if error before our handling
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showSaveChoiceDialog() {
        try {
            val options = arrayOf("Save as PNG", "Save as JSON")
            AlertDialog.Builder(requireContext())
                .setTitle("Save drawing")
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> promptFileNameAndSave(isPng = true)
                        1 -> promptFileNameAndSave(isPng = false)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun promptFileNameAndSave(isPng: Boolean) {
        try {
            val edit = EditText(requireContext()).apply {
                val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                setText(if (isPng) "studyboard_$time.png" else "studyboard_$time.json")
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            AlertDialog.Builder(requireContext())
                .setTitle(if (isPng) "Save as PNG" else "Save as JSON")
                .setView(edit)
                .setPositiveButton("Save") { dlg, _ ->
                    try {
                        val filenameInput = edit.text.toString().trim()
                        if (filenameInput.isEmpty()) {
                            Toast.makeText(requireContext(), "Please enter a file name", Toast.LENGTH_SHORT).show()
                        } else {
                            val finalName = if (isPng) {
                                if (filenameInput.endsWith(".png", ignoreCase = true)) filenameInput else "$filenameInput.png"
                            } else {
                                if (filenameInput.endsWith(".json", ignoreCase = true)) filenameInput else "$filenameInput.json"
                            }
                            saveToFile(finalName, isPng)
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, e)
                    } finally {
                        dlg.dismiss()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToFile(filename: String, isPng: Boolean) {
        val vm = whiteboardViewModel
        if (vm == null) {
            AppLogger.e(TAG, Exception("ViewModel not available for saving. Exception"))
            return
        }

        lifecycleScope.launch {
            try {
                if (isPng) {
                    val bitmap = withContext(Dispatchers.Default) {
                        createBitmapFromView(requireView().findViewById(R.id.whiteboardView))
                    }
                    vm.savePng(filename, bitmap)
                } else {
                    val json = withContext(Dispatchers.Default) {
                        vm.exportDrawingAsJsonString(pretty = true)
                    }
                    if (json == null) {
                        val errorMsg = "Failed to create JSON for saving."
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                        AppLogger.e(TAG, Exception(errorMsg))
                        return@launch
                    }
                    vm.saveJson(filename, json)
                }
            } catch (e: Exception) {
                val errorMsg = "Saving file failed: ${e.localizedMessage}"
                AppLogger.e(TAG, e)
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onTextButtonClicked() {
        try {
            val edit = EditText(requireContext()).apply { hint = "Type text to add"; isSingleLine = false; minLines = 1 }
            AlertDialog.Builder(requireContext())
                .setTitle("Add text")
                .setView(edit)
                .setPositiveButton("OK") { dlg, _ ->
                    try {
                        val typed = edit.text.toString().trim()
                        if (typed.isNotEmpty()) {
                            val view = view ?: return@setPositiveButton
                            val centerX = (whiteboardView?.width ?: view.width) / 2f
                            val centerY = (whiteboardView?.height ?: view.height) / 2f + 48f // Adjusted for potential keyboard
                            val t = SerializableText(centerX, centerY, typed, color = 0xFF000000.toInt(), textSize = 48f)
                            whiteboardViewModel?.addText(t)
                        }
                    } catch (e: Exception) {
                         AppLogger.e(TAG, e)
                    } finally {
                        dlg.dismiss()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        try {
            val w = view.width.takeIf { it > 0 } ?: 1000
            val h = view.height.takeIf { it > 0 } ?: 1000
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            view.draw(canvas)
            return bmp
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
            // Consider rethrowing or returning a default/error bitmap if appropriate for your app flow
            throw Exception("Failed to create bitmap from view", e) // Rethrow to signal failure
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            whiteboardView?.setBoardActionListener(null)
            whiteboardViewModel = null
            whiteboardView = null
        } catch (e: Exception) {
            AppLogger.e(TAG, e)
        }
    }
}
