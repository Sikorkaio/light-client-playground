package io.sikorka.android.ui.dialogs.fileselection

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import io.sikorka.android.R
import io.sikorka.android.ui.coloredSpan
import io.sikorka.android.ui.dialogs.fileselection.Selection.DIRECTORY
import io.sikorka.android.ui.dialogs.fileselection.Selection.FILE
import java.io.File

class FileSelectionDialog : DialogFragment() {

  private lateinit var fm: FragmentManager

  private lateinit var rootDirectory: File
  private lateinit var fileList: RecyclerView
  private lateinit var upButton: ImageButton
  private lateinit var currentDirectory: TextView
  private lateinit var emptyGroup: Group

  private lateinit var currentFile: File
  private lateinit var onSelection: (File) -> Unit

  private val fileSelectionAdapter: FileSelectionAdapter by lazy { FileSelectionAdapter() }

  private var showFiles: Boolean = false
  private var selectedFile: File? = null

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val context = requireContext()
    val view = createView(context)

    val titleResId = if (showFiles) {
      R.string.file_selection__select_file
    } else {
      R.string.file_selection__select_folder
    }

    view.findViewById<View>(R.id.file_selection__folder_hint).isVisible = !showFiles

    val builder = AlertDialog.Builder(context)
      .setTitle(coloredSpan(titleResId))
      .setView(view)
      .setPositiveButton(coloredSpan(android.R.string.ok)) { dialog, _ ->
        selectedFile?.let {
          onSelection(it)
          dialog.dismiss()
        }
      }
      .setNegativeButton(coloredSpan(android.R.string.cancel)) { dialog, _ -> dialog.dismiss() }

    val alertDialog = builder.create()
    alertDialog.setOnShowListener { _ ->
      navigateTo(rootDirectory)

      fileSelectionAdapter.setOnFileSelected { file, navigate ->
        if (navigate) {
          !navigateTo(file)
        } else {
          selectedFile = file
          true
        }
      }
      upButton.setOnClickListener {
        if (currentFile == rootDirectory) {
          return@setOnClickListener
        }

        navigateTo(currentFile.parentFile)
      }
    }
    fileSelectionAdapter.setSelectionMode(if (showFiles) FILE else DIRECTORY)
    return alertDialog
  }

  private fun navigateTo(file: File): Boolean {
    currentFile = file

    val contents = listContents(file)
    val emptyDirectory = contents.isEmpty()

    return if (!showFiles && emptyDirectory) {
      selectedFile = file
      false
    } else {
      emptyGroup.isVisible = emptyDirectory
      currentDirectory.text = file.absolutePath.removePrefix(rootDirectory.absolutePath)
      fileSelectionAdapter.submitList(contents)
      true
    }
  }

  fun show(onSelection: (File) -> Unit) {
    this.onSelection = onSelection
    show(fm, TAG)
  }

  private fun listContents(file: File): List<File> {
    val listFiles = file.listFiles()
    return if (showFiles) {
      listFiles.sortedWith(compareBy({ it.isFile }, { it.name })).toList()
    } else {
      listFiles.filter { it.isDirectory }.toList()
    }
  }

  @SuppressLint("InflateParams")
  private fun createView(context: Context): View {
    val inflater = LayoutInflater.from(context)
    return inflater.inflate(R.layout.dialog__file_selection, null, false).also {
      fileList = it.findViewById(R.id.file_selection__file_list)
      fileList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
      fileList.adapter = fileSelectionAdapter
      emptyGroup = it.findViewById(R.id.file_selection__empty_group)
      upButton = it.findViewById(R.id.file_selection__navigate_up)
      currentDirectory = it.findViewById(R.id.file_selection__current_path)
    }
  }

  companion object {
    private const val TAG = "io.sikorka.android.ui.dialogs.fileselection.FILE_SELECTION"
    fun create(fm: FragmentManager, rootDirectory: File, showFiles: Boolean): FileSelectionDialog {
      return FileSelectionDialog().apply {
        this.rootDirectory = rootDirectory
        this.showFiles = showFiles
        this.fm = fm
      }
    }
  }
}