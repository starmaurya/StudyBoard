package com.starmaurya.whiteboard.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.starmaurya.whiteboard.R
import com.starmaurya.whiteboard.views.WhiteboardView

class WhiteboardFragment : Fragment() {

    private var whiteboardView: WhiteboardView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout that contains the custom WhiteboardView
        val root = inflater.inflate(R.layout.fragment_whiteboard, container, false)
        whiteboardView = root.findViewById(R.id.whiteboardView)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<Toolbar>(R.id.whiteboard_toolbar)
        (activity as? AppCompatActivity)?.setSupportActionBar(toolbar)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayShowTitleEnabled(false)
        // The homeAsUpIndicator is now set in the XML layout (app:navigationIcon)

        val undoButton = view.findViewById<ImageButton>(R.id.action_undo_centered)
        undoButton.setOnClickListener {
            whiteboardView?.undo()
        }

        val redoButton = view.findViewById<ImageButton>(R.id.action_redo_centered)
        redoButton.setOnClickListener {
            whiteboardView?.redo()
        }

        setHasOptionsMenu(true) // For the 'save' menu item
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.whiteboard_toolbar_menu, menu) // This menu now only contains 'save'
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle back button press
                activity?.onBackPressedDispatcher?.onBackPressed()
                true
            }
            R.id.action_save -> {
                // TODO: Handle Save action
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        whiteboardView = null
    }

    companion object {
        @JvmStatic
        fun newInstance() =
            WhiteboardFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}