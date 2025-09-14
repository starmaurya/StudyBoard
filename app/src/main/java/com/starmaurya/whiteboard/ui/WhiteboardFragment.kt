package com.starmaurya.whiteboard.ui


import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.starmaurya.whiteboard.R
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
    private var menuOpen = false

    private lateinit var btnPen: ImageButton
    private lateinit var btnBrush: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnShapes: ImageButton
    private lateinit var btnText: ImageButton

    // ViewModel
    private var whiteboardViewModel: WhiteboardViewModel? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_whiteboard, container, false)
        whiteboardView = root.findViewById(R.id.whiteboardView)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // setup toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.whiteboard_toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)

        // undo / redo
        val undoButton = view.findViewById<ImageButton>(R.id.action_undo_centered)
        val redoButton = view.findViewById<ImageButton>(R.id.action_redo_centered)

        undoButton.setOnClickListener {
            if (whiteboardView?.hasUndo() == true) {
                whiteboardView?.undo()
            } else {
                Toast.makeText(requireContext(), "No drawings to undo", Toast.LENGTH_SHORT).show()
            }
        }

        redoButton.setOnClickListener {
            if (whiteboardView?.hasRedo() == true) {
                whiteboardView?.redo()
            } else {
                Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
        }

        // bottom tool buttons
        btnPen = view.findViewById(R.id.btn_pen)
        btnBrush = view.findViewById(R.id.btn_brush)
        btnEraser = view.findViewById(R.id.btn_eraser)
        btnShapes = view.findViewById(R.id.btn_shapes)
        btnText = view.findViewById(R.id.btn_text)

        // default tool
        whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
        highlightTool(btnPen)

        btnPen.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
            highlightTool(btnPen)
            Toast.makeText(requireContext(), "Pen", Toast.LENGTH_SHORT).show()
        }

        btnBrush.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.SHAPE)
            highlightTool(btnBrush)
            Toast.makeText(requireContext(), "Square (drag to size)", Toast.LENGTH_SHORT).show()
        }

        btnEraser.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.ERASER)
            highlightTool(btnEraser)
            Toast.makeText(requireContext(), "Eraser", Toast.LENGTH_SHORT).show()
        }

        btnShapes.setOnClickListener {
            // placeholder: open shapes picker later
            Toast.makeText(requireContext(), "Shapes panel coming soon", Toast.LENGTH_SHORT).show()
        }

        btnText.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.TEXT)
            highlightTool(btnText)
            onTextButtonClicked()
        }

        // set up ViewModel + repository
        val repo = MediaStoreFileRepository(requireContext().applicationContext)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return WhiteboardViewModel(repo) as T
            }
        }
        val vm = ViewModelProvider(this, factory).get(WhiteboardViewModel::class.java)
        whiteboardViewModel = vm

        // observe one-time save events from ViewModel
        lifecycleScope.launch {
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

        setHasOptionsMenu(true)
    }

    /** Toggle a green border using state_selected (background selector) */
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
                if (whiteboardView?.hasUndo() == true) {
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
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToFile(filename: String, isPng: Boolean) {
        if (whiteboardView?.hasUndo() != true) {
            Toast.makeText(requireContext(), "Nothing to save", Toast.LENGTH_SHORT).show()
            return
        }

        // keep workload off main thread; ViewModel will perform the IO
        lifecycleScope.launch {
            if (isPng) {
                val bitmap = withContext(Dispatchers.Default) {
                    createBitmapFromView(requireView().findViewById(R.id.whiteboardView))
                }
                whiteboardViewModel?.savePng(filename, bitmap)
            } else {
                val json = withContext(Dispatchers.Default) {
                    whiteboardView?.exportDrawingAsJsonString(pretty = true, stepPx = 6f)
                }
                if (json == null) {
                    Toast.makeText(requireContext(), "Failed to create JSON", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                whiteboardViewModel?.saveJson(filename, json)
            }
        }
    }

    // create a bitmap snapshot of the view (call on background thread)
    private fun createBitmapFromView(view: View): Bitmap {
        val w = view.width.takeIf { it > 0 } ?: 1000
        val h = view.height.takeIf { it > 0 } ?: 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // optional: draw white background
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        return bmp
    }

    // when user clicks text button:
    private fun onTextButtonClicked() {
        val edit = EditText(requireContext()).apply {
            hint = "Type text to add"
            isSingleLine = false
            minLines = 1
            setText("") // empty by default
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Add text")
            .setView(edit)
            .setPositiveButton("OK") { dlg, _ ->
                val typed = edit.text.toString().trim()
                if (typed.isNotEmpty()) {
                    // add at center (existing behavior)
                    whiteboardView?.addText(typed, null, null)
                }
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // clear listener/viewmodel ref
        whiteboardViewModel = null
        whiteboardView = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = WhiteboardFragment().apply { arguments = Bundle() }
    }
}

