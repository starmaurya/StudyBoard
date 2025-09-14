package com.starmaurya.whiteboard.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.starmaurya.whiteboard.R
import com.starmaurya.whiteboard.views.WhiteboardView
import kotlinx.coroutines.Dispatchers
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

        // toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.whiteboard_toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)

        // undo/redo
        view.findViewById<ImageButton>(R.id.action_undo_centered).setOnClickListener {
            if (whiteboardView?.hasUndo() == true) {
                whiteboardView?.undo()
            } else {
                Toast.makeText(requireContext(), "No drawings to undo", Toast.LENGTH_SHORT).show()
            }
        }
        view.findViewById<ImageButton>(R.id.action_redo_centered).setOnClickListener {
            if (whiteboardView?.hasRedo() == true) {
                whiteboardView?.redo()
            } else {
                Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show()
            }
        }

        // bottom tools
        btnPen = view.findViewById(R.id.btn_pen)
        btnBrush = view.findViewById(R.id.btn_brush)
        btnEraser = view.findViewById(R.id.btn_eraser)
        btnShapes = view.findViewById(R.id.btn_shapes)
        btnText = view.findViewById(R.id.btn_text)

        // default mode = PEN (and highlight)
        whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
        highlightTool(btnPen)

        btnPen.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.PEN)
            highlightTool(btnPen)
        }

        btnBrush.setOnClickListener {
            showToast()
        }

        btnEraser.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.ERASER)
            highlightTool(btnEraser)
        }

        btnShapes.setOnClickListener {
            // keep highlight as current tool (don't switch)
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.SHAPE)
            highlightTool(btnBrush)
        }

        btnText.setOnClickListener {
            whiteboardView?.setToolMode(WhiteboardView.ToolMode.TEXT)
            highlightTool(btnText)
            onTextButtonClicked()
        }

        setHasOptionsMenu(true)
    }

    /** Toggle a green border using state_selected (background selector) */
    private fun highlightTool(selected: ImageButton) {
        val all = listOf(btnPen, btnBrush, btnEraser, btnShapes, btnText)
        all.forEach { it.isSelected = false }
        selected.isSelected = true
    }

    private fun showToast(msg: String = "Coming Soon") {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun toggleMenu(menu: View, mainFab: FloatingActionButton) {
        if (menuOpen) collapseMenu(menu, mainFab) else expandMenu(menu, mainFab)
    }

    private fun expandMenu(menu: View, mainFab: FloatingActionButton) {
        menu.visibility = View.VISIBLE
        menu.scaleX = 0f; menu.scaleY = 0f
        menu.animate().scaleX(1f).scaleY(1f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
        mainFab.animate().rotation(45f).setDuration(200).start()
        menuOpen = true
    }

    private fun collapseMenu(menu: View, mainFab: FloatingActionButton) {
        menu.animate().scaleX(0f).scaleY(0f).setDuration(180)
            .setInterpolator(DecelerateInterpolator()).withEndAction { menu.visibility = View.GONE }.start()
        mainFab.animate().rotation(0f).setDuration(180).start()
        menuOpen = false
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

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToFile(filename: String, isPng: Boolean) {
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            if (isPng) {
                val bitmap = withContext(Dispatchers.IO) {
                    createBitmapFromView(requireView().findViewById(R.id.whiteboardView))
                }
                val uri = saveBitmapToGalleryPng(bitmap, filename)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Saved PNG to Pictures/StudyBoard", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "PNG save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                val json = whiteboardView?.exportDrawingAsJsonString(pretty = true, stepPx = 6f)
                if (json == null) {
                    Toast.makeText(requireContext(), "Failed to create JSON", Toast.LENGTH_SHORT).show()
                    return@launchWhenStarted
                }
                val uri = saveJsonToDownloads(filename, json)
                withContext(Dispatchers.Main) {
                    if (uri != null) {
                        Toast.makeText(requireContext(), "Saved JSON to Downloads/StudyBoard", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "JSON save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun createBitmapFromView(view: View): Bitmap {
        val w = view.width.takeIf { it > 0 } ?: 1000
        val h = view.height.takeIf { it > 0 } ?: 1000
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        view.draw(canvas)
        return bmp
    }

    private suspend fun saveBitmapToGalleryPng(bitmap: Bitmap, displayName: String): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StudyBoard")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return@withContext null
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw Exception("Unable to open output stream")
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun saveJsonToDownloads(filename: String, jsonString: String): android.net.Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = requireContext().contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StudyBoard")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = resolver.insert(collection, values) ?: return@withContext null
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw Exception("Unable to open output stream for $uri")
                    out.write(jsonString.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // text add dialog
    private fun onTextButtonClicked() {
        val edit = EditText(requireContext()).apply {
            hint = "Type text to add"
            isSingleLine = false
            minLines = 1
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add text")
            .setView(edit)
            .setPositiveButton("OK") { dlg, _ ->
                val typed = edit.text.toString().trim()
                if (typed.isNotEmpty()) {
                    whiteboardView?.addText(typed, null, null)
                }
                dlg.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        whiteboardView = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = WhiteboardFragment().apply { arguments = Bundle() }
    }
}
