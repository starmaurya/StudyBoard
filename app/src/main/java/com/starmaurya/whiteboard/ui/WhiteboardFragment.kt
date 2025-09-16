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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_whiteboard, container, false)
        whiteboardView = root.findViewById(R.id.whiteboardView)

        // IMPORTANT: set a lightweight listener that forwards committed items to the ViewModel.
        // ViewModel instance will be created in onViewCreated — listener will safely call vm if available.
        whiteboardView?.setBoardActionListener(object : WhiteboardView.BoardActionListener {
            override fun onStrokeCommitted(stroke: SerializableStroke) {
                whiteboardViewModel?.addStroke(stroke)
            }

            override fun onShapeCommitted(shape: SerializableShape) {
                whiteboardViewModel?.addShape(shape)
            }

            override fun onTextCommitted(text: SerializableText) {
                whiteboardViewModel?.addText(text)
            }
        })

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.whiteboard_toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)

        // find buttons
        btnPen = view.findViewById(R.id.btn_pen)
        btnBrush = view.findViewById(R.id.btn_brush)
        btnEraser = view.findViewById(R.id.btn_eraser)
        btnShapes = view.findViewById(R.id.btn_shapes)
        btnText = view.findViewById(R.id.btn_text)

        // create VM (phase 2 repository wiring)
        val repo = MediaStoreFileRepository(requireContext().applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WhiteboardViewModel(repo) as T
            }
        }
        val vm = ViewModelProvider(this, factory).get(WhiteboardViewModel::class.java)
        whiteboardViewModel = vm

        // Observe board state -> render in view
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                vm.boardState.collect { payload ->
                    whiteboardView?.renderBoard(payload)
                }
            }
        }

        // Observe save events
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
                            Toast.makeText(requireContext(), "Save failed: ${ev.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // DEFAULT TOOL & UI wiring
        whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
        highlightTool(btnPen)

        btnPen.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
            highlightTool(btnPen)
        }
        btnBrush.setOnClickListener {
            Toast.makeText(requireContext(), "coming soon", Toast.LENGTH_SHORT).show()
        }
        btnEraser.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.ERASER)
            highlightTool(btnEraser)
        }
        btnShapes.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.SHAPE)
            highlightTool(btnShapes)
        }
        btnText.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.TEXT)
            highlightTool(btnText)
            onTextButtonClicked()
        }

        // Undo / Redo via ViewModel
        val undoBtn = view.findViewById<ImageButton>(R.id.action_undo_centered)
        val redoBtn = view.findViewById<ImageButton>(R.id.action_redo_centered)
        undoBtn.setOnClickListener {
            if (vm.hasUndo()) vm.undo() else Toast.makeText(requireContext(), "No drawings to undo", Toast.LENGTH_SHORT).show()
        }
        redoBtn.setOnClickListener {
            if (vm.hasRedo()) vm.redo() else Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
        }

        setHasOptionsMenu(true)
    }

    // selection highlight (uses background selector with stroke)
    private fun highlightTool(selected: ImageButton) {
        val all = listOf(btnPen, btnBrush, btnEraser, btnShapes, btnText)
        all.forEach { it.isSelected = false }
        selected.isSelected = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.whiteboard_toolbar_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity?.onBackPressedDispatcher?.onBackPressed()
                true
            }
            R.id.action_save -> {
                // don't open save dialog if board empty
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
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun showSaveChoiceDialog() {
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
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun promptFileNameAndSave(isPng: Boolean) {
        val edit = EditText(requireContext()).apply {
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            setText(if (isPng) "studyboard_$time.png" else "studyboard_$time.json")
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isPng) "Save as PNG" else "Save as JSON")
            .setView(edit)
            .setPositiveButton("Save") { dlg, _ ->
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
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Delegates saving to ViewModel (which uses repository).
     * For PNG we snapshot the view into a bitmap.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToFile(filename: String, isPng: Boolean) {
        val vm = whiteboardViewModel ?: return

        lifecycleScope.launch {
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
                    Toast.makeText(requireContext(), "Failed to create JSON", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                vm.saveJson(filename, json)
            }
        }
    }

    // text add dialog -> adds SerializableText to VM
    private fun onTextButtonClicked() {
        val edit = EditText(requireContext()).apply { hint = "Type text to add"; isSingleLine = false; minLines = 1 }
        AlertDialog.Builder(requireContext())
            .setTitle("Add text")
            .setView(edit)
            .setPositiveButton("OK") { dlg, _ ->
                val typed = edit.text.toString().trim()
                if (typed.isNotEmpty()) {
                    val view = view ?: return@setPositiveButton
                    val centerX = (whiteboardView?.width ?: view.width) / 2f
                    val centerY = (whiteboardView?.height ?: view.height) / 2f + 48f
                    val t = SerializableText(centerX, centerY, typed, color = 0xFF000000.toInt(), textSize = 48f)
                    whiteboardViewModel?.addText(t)
                }
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // snapshot the view into a bitmap (background thread)
    private fun createBitmapFromView(view: View): Bitmap {
        val w = view.width.takeIf { it > 0 } ?: 1000
        val h = view.height.takeIf { it > 0 } ?: 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        return bmp
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // clean up
        whiteboardView?.setBoardActionListener(null)
        whiteboardViewModel = null
        whiteboardView = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = WhiteboardFragment().apply { arguments = Bundle() }
    }
}
