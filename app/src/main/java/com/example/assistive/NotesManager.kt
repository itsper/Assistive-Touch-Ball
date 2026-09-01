package com.example.assistive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotesManager(
    private val service: FloatingBallService,
    private val menuView: View
) {
    private val notesList = mutableListOf<NoteModel>()
    private var currentEditingNote: NoteModel? = null
    private var notesAdapter: NotesAdapter? = null

    // Layout Containers
    private lateinit var layoutNoteContainer: View
    private lateinit var layoutNotesList: View
    private lateinit var layoutNoteEditor: View

    // List View Elements
    private lateinit var btnNotesBack: ImageButton
    private lateinit var btnNotesAdd: TextView
    private lateinit var txtNotesCount: TextView
    private lateinit var recyclerNotes: RecyclerView
    private lateinit var layoutEmptyNotes: View

    // Editor View Elements
    private lateinit var btnEditorBack: ImageButton
    private lateinit var txtEditorHeader: TextView
    private lateinit var btnEditorSave: TextView
    private lateinit var edtNoteTitle: EditText
    private lateinit var edtNoteContent: EditText
    private lateinit var btnEditorCopy: TextView
    private lateinit var btnEditorPaste: TextView
    private lateinit var btnEditorClear: TextView
    private lateinit var btnEditorDelete: TextView

    fun init() {
        layoutNoteContainer = menuView.findViewById(R.id.layout_note_container)
        layoutNotesList = layoutNoteContainer.findViewById(R.id.layout_notes_list)
        layoutNoteEditor = layoutNoteContainer.findViewById(R.id.layout_note_editor)

        // List elements
        btnNotesBack = layoutNoteContainer.findViewById(R.id.btn_notes_back)
        btnNotesAdd = layoutNoteContainer.findViewById(R.id.btn_notes_add)
        txtNotesCount = layoutNoteContainer.findViewById(R.id.txt_notes_count)
        recyclerNotes = layoutNoteContainer.findViewById(R.id.recycler_notes)
        layoutEmptyNotes = layoutNoteContainer.findViewById(R.id.layout_empty_notes)

        // Editor elements
        btnEditorBack = layoutNoteContainer.findViewById(R.id.btn_editor_back)
        txtEditorHeader = layoutNoteContainer.findViewById(R.id.txt_editor_header)
        btnEditorSave = layoutNoteContainer.findViewById(R.id.btn_editor_save)
        edtNoteTitle = layoutNoteContainer.findViewById(R.id.edt_note_title)
        edtNoteContent = layoutNoteContainer.findViewById(R.id.edt_note_content)
        btnEditorCopy = layoutNoteContainer.findViewById(R.id.btn_editor_copy)
        btnEditorPaste = layoutNoteContainer.findViewById(R.id.btn_editor_paste)
        btnEditorClear = layoutNoteContainer.findViewById(R.id.btn_editor_clear)
        btnEditorDelete = layoutNoteContainer.findViewById(R.id.btn_editor_delete)

        setupRecyclerView()
        setupListeners()
        loadSavedNotes()
    }

    private fun setupRecyclerView() {
        notesAdapter = NotesAdapter(
            notes = notesList,
            onNoteClick = { note -> openNoteEditor(note) },
            onCopyClick = { note ->
                val body = if (note.content.isNotBlank()) note.content else note.title
                if (body.isBlank()) {
                    Toast.makeText(service, "Nothing to copy", Toast.LENGTH_SHORT).show()
                } else {
                    copyToClipboard(body, "Note copied to clipboard")
                }
            },
            onDeleteClick = { note -> deleteNote(note) }
        )
        recyclerNotes.layoutManager = LinearLayoutManager(service)
        recyclerNotes.adapter = notesAdapter
    }

    private fun setupListeners() {
        // Back from List to Main floating buttons menu
        btnNotesBack.setOnClickListener {
            hideKeyboard()
            service.setMenuFocusable(false)
            layoutNoteContainer.visibility = View.GONE
            menuView.findViewById<View>(R.id.layout_menu_buttons).visibility = View.VISIBLE
        }

        // Add new note
        btnNotesAdd.setOnClickListener {
            openNoteEditor(null)
        }

        // Back from Editor to Notes List
        btnEditorBack.setOnClickListener {
            openNotesList()
        }

        // Save note
        btnEditorSave.setOnClickListener {
            saveCurrentNote()
        }

        // Editor action: Copy (copies note body only)
        btnEditorCopy.setOnClickListener {
            val content = edtNoteContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(service, "No content to copy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            copyToClipboard(content, "Copied note to clipboard")
        }

        // Editor action: Paste
        btnEditorPaste.setOnClickListener {
            pasteFromClipboard()
        }

        // Editor action: Clear
        btnEditorClear.setOnClickListener {
            edtNoteTitle.setText("")
            edtNoteContent.setText("")
            edtNoteTitle.requestFocus()
        }

        // Editor action: Delete
        btnEditorDelete.setOnClickListener {
            currentEditingNote?.let { note ->
                deleteNote(note)
            }
            openNotesList()
        }
    }

    private fun loadSavedNotes() {
        notesList.clear()
        notesList.addAll(NoteModel.loadNotes(service))
        updateListUI()
    }

    private fun updateListUI() {
        notesAdapter?.notifyDataSetChanged()
        val count = notesList.size
        txtNotesCount.text = when (count) {
            0 -> "0 Notes saved"
            1 -> "1 Note saved"
            else -> "$count Notes saved"
        }
        if (count == 0) {
            layoutEmptyNotes.visibility = View.VISIBLE
            recyclerNotes.visibility = View.GONE
        } else {
            layoutEmptyNotes.visibility = View.GONE
            recyclerNotes.visibility = View.VISIBLE
        }
    }

    fun openNotesList() {
        hideKeyboard()
        service.setMenuFocusable(false)
        layoutNotesList.visibility = View.VISIBLE
        layoutNoteEditor.visibility = View.GONE
        loadSavedNotes()
    }

    private fun openNoteEditor(note: NoteModel?) {
        currentEditingNote = note
        if (note == null) {
            txtEditorHeader.text = "New Note"
            edtNoteTitle.setText("")
            edtNoteContent.setText("")
            btnEditorDelete.visibility = View.GONE
        } else {
            txtEditorHeader.text = "Edit Note"
            edtNoteTitle.setText(note.title)
            edtNoteContent.setText(note.content)
            btnEditorDelete.visibility = View.VISIBLE
        }

        layoutNotesList.visibility = View.GONE
        layoutNoteEditor.visibility = View.VISIBLE

        // Enable focusable flags so the soft keyboard works inside WindowManager overlay
        service.setMenuFocusable(true)

        if (note == null || note.title.isEmpty()) {
            edtNoteTitle.requestFocus()
            showKeyboard(edtNoteTitle)
        } else {
            edtNoteContent.requestFocus()
            edtNoteContent.setSelection(edtNoteContent.text.length)
            showKeyboard(edtNoteContent)
        }
    }

    private fun saveCurrentNote() {
        val title = edtNoteTitle.text.toString().trim()
        val content = edtNoteContent.text.toString().trim()

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(service, "Cannot save empty note", Toast.LENGTH_SHORT).show()
            return
        }

        val effectiveTitle = if (title.isNotEmpty()) {
            title
        } else {
            // Auto-generate title from first line of content
            val firstLine = content.lines().firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled"
            if (firstLine.length > 25) firstLine.substring(0, 25) + "..." else firstLine
        }

        val existing = currentEditingNote
        if (existing == null) {
            val newNote = NoteModel(
                title = effectiveTitle,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            notesList.add(0, newNote)
            Toast.makeText(service, "Note saved", Toast.LENGTH_SHORT).show()
        } else {
            existing.title = effectiveTitle
            existing.content = content
            existing.timestamp = System.currentTimeMillis()
            // Move updated note to top
            notesList.remove(existing)
            notesList.add(0, existing)
            Toast.makeText(service, "Note updated", Toast.LENGTH_SHORT).show()
        }

        NoteModel.saveNotes(service, notesList)
        openNotesList()
    }

    private fun deleteNote(note: NoteModel) {
        notesList.remove(note)
        NoteModel.saveNotes(service, notesList)
        Toast.makeText(service, "Note deleted", Toast.LENGTH_SHORT).show()
        updateListUI()
    }

    private fun copyToClipboard(text: String, message: String) {
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Note", text)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(service, "Failed to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pasteFromClipboard() {
        try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).coerceToText(service).toString()
                if (pasteText.isNotBlank()) {
                    val start = edtNoteContent.selectionStart.coerceAtLeast(0)
                    val end = edtNoteContent.selectionEnd.coerceAtLeast(0)
                    edtNoteContent.text.replace(Math.min(start, end), Math.max(start, end), pasteText)
                    Toast.makeText(service, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(service, "Clipboard text is empty", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(service, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(service, "Failed to paste", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showKeyboard(view: View) {
        view.postDelayed({
            val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideKeyboard() {
        val imm = service.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(menuView.windowToken, 0)
    }

    fun onDestroy() {
        hideKeyboard()
    }

    // --- RecyclerView Adapter ---
    private class NotesAdapter(
        private val notes: List<NoteModel>,
        private val onNoteClick: (NoteModel) -> Unit,
        private val onCopyClick: (NoteModel) -> Unit,
        private val onDeleteClick: (NoteModel) -> Unit
    ) : RecyclerView.Adapter<NoteViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.note_item, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notes[position]
            holder.txtTitle.text = if (note.title.isNotBlank()) note.title else "Untitled Note"
            holder.txtPreview.text = if (note.content.isNotBlank()) note.content else "(Empty note)"
            holder.txtDate.text = note.getFormattedDate()

            holder.itemView.setOnClickListener { onNoteClick(note) }
            holder.btnCopy.setOnClickListener { onCopyClick(note) }
            holder.btnDelete.setOnClickListener { onDeleteClick(note) }
        }

        override fun getItemCount(): Int = notes.size
    }

    private class NoteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txt_item_note_title)
        val txtPreview: TextView = view.findViewById(R.id.txt_item_note_preview)
        val txtDate: TextView = view.findViewById(R.id.txt_item_note_date)
        val btnCopy: ImageButton = view.findViewById(R.id.btn_item_note_copy)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_item_note_delete)
    }
}
