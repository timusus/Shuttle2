package com.simplecityapps.shuttle.ui.common.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.Group
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.shuttle.model.Song
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.autoCleared
import com.simplecityapps.shuttle.ui.common.closeKeyboard
import com.simplecityapps.shuttle.ui.common.utils.withArgs
import com.simplecityapps.shuttle.ui.common.view.CircularLoadingView
import com.squareup.phrase.Phrase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TagEditorAlertDialog : DialogFragment(),
    TagEditorContract.View {

    private var songs: List<com.simplecityapps.shuttle.model.Song> = listOf()

    private lateinit var _data: TagEditorContract.Data

    @Inject
    lateinit var presenter: TagEditorPresenter

    private var uiGroup: Group by autoCleared()
    private var loadingView: CircularLoadingView by autoCleared()

    private var subtitleText: TextView by autoCleared()

    private var titleInputLayout: TextInputLayout by autoCleared()
    private var titleEditText: EditText by autoCleared()

    private var artistInputLayout: TextInputLayout by autoCleared()
    private var artistEditText: EditText by autoCleared()

    private var albumInputLayout: TextInputLayout by autoCleared()
    private var albumEditText: EditText by autoCleared()

    private var albumArtistInputLayout: TextInputLayout by autoCleared()
    private var albumArtistEditText: EditText by autoCleared()

    private var yearInputLayout: TextInputLayout by autoCleared()
    private var yearEditText: EditText by autoCleared()

    private var trackInputLayout: TextInputLayout by autoCleared()
    private var trackEditText: EditText by autoCleared()

    private var trackTotalInputLayout: TextInputLayout by autoCleared()
    private var trackTotalEditText: EditText by autoCleared()

    private var discInputLayout: TextInputLayout by autoCleared()
    private var discEditText: EditText by autoCleared()

    private var discTotalInputLayout: TextInputLayout by autoCleared()
    private var discTotalEditText: EditText by autoCleared()

    private var genreInputLayout: TextInputLayout by autoCleared()
    private var genreEditText: EditText by autoCleared()

    private var lyricsInputLayout: TextInputLayout by autoCleared()
    private var lyricsEditText: EditText by autoCleared()


    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        songs = arguments?.getParcelableArrayList<com.simplecityapps.shuttle.model.Song>(ARG_SONGS) as List<com.simplecityapps.shuttle.model.Song>
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = layoutInflater.inflate(R.layout.fragment_dialog_edit_tags, null)

        uiGroup = view.findViewById(R.id.uiGroup)
        loadingView = view.findViewById(R.id.loadingView)

        subtitleText = view.findViewById(R.id.subtitleText)
        subtitleText.text = Phrase.fromPlural(resources, R.plurals.edit_tags_editing_count_songs, songs.size)
            .put("count", songs.size)
            .format()

        titleInputLayout = view.findViewById(R.id.titleInputLayout)
        titleEditText = view.findViewById(R.id.titleEditText)
        titleInputLayout.isEndIconVisible = false

        artistInputLayout = view.findViewById(R.id.artistInputLayout)
        artistEditText = view.findViewById(R.id.artistEditText)
        artistInputLayout.isEndIconVisible = false

        albumInputLayout = view.findViewById(R.id.albumInputLayout)
        albumEditText = view.findViewById(R.id.albumEditText)
        albumInputLayout.isEndIconVisible = false

        albumArtistInputLayout = view.findViewById(R.id.albumArtistInputLayout)
        albumArtistEditText = view.findViewById(R.id.albumArtistEditText)
        albumArtistInputLayout.isEndIconVisible = false

        yearInputLayout = view.findViewById(R.id.yearInputLayout)
        yearEditText = view.findViewById(R.id.yearEditText)
        yearInputLayout.isEndIconVisible = false

        trackInputLayout = view.findViewById(R.id.trackInputLayout)
        trackEditText = view.findViewById(R.id.trackEditText)
        trackInputLayout.isEndIconVisible = false

        trackTotalInputLayout = view.findViewById(R.id.trackTotalInputLayout)
        trackTotalEditText = view.findViewById(R.id.trackTotalEditText)
        trackTotalInputLayout.isEndIconVisible = false

        discInputLayout = view.findViewById(R.id.discInputLayout)
        discEditText = view.findViewById(R.id.discEditText)
        discInputLayout.isEndIconVisible = false

        discTotalInputLayout = view.findViewById(R.id.discTotalInputLayout)
        discTotalEditText = view.findViewById(R.id.discTotalEditText)
        discTotalInputLayout.isEndIconVisible = false

        genreInputLayout = view.findViewById(R.id.genreInputLayout)
        genreEditText = view.findViewById(R.id.genreEditText)
        genreInputLayout.isEndIconVisible = false

        lyricsInputLayout = view.findViewById(R.id.lyricsInputLayout)
        lyricsEditText = view.findViewById(R.id.lyricsEditText)
        lyricsInputLayout.isEndIconVisible = false

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_tags_action))
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .setPositiveButton(getString(R.string.dialog_button_save)) { _, _ -> lyricsInputLayout.closeKeyboard() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setOnClickListener {
                    presenter.save(_data)
                }
            }
        }

        presenter.bindView(this)
        presenter.load(songs)

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        presenter.unbindView()
        super.onDismiss(dialog)
    }

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }


    // Private

    private fun updateSaveButton(data: TagEditorContract.Data) {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = data.all.any { field -> field.hasChanged }
        }
    }

    private fun setupResetButton(inputLayout: TextInputLayout, editText: EditText, field: TagEditorContract.Field, onTextChange: () -> Unit) {
        inputLayout.isEndIconVisible = false
        editText.addTextChangedListener(afterTextChanged = { editable ->
            field.currentValue = editable.toString()
            inputLayout.isEndIconVisible = field.hasChanged
            onTextChange()
        })
        inputLayout.setEndIconOnClickListener {
            field.reset()
            editText.setText(field.currentValue)
            editText.setSelection(editText.length())
            field.reset() // setText triggers the text change listener, and sets the field to ''. Need to reset again to set it to null.
            inputLayout.isEndIconVisible = field.hasChanged
            onTextChange()
        }
    }


    // TagEditorContract.View Implementation

    override fun setData(data: TagEditorContract.Data) {
        _data = data

        updateSaveButton(data)
        val textChangeListener: () -> Unit = {
            updateSaveButton(data)
        }

        data.titleField.apply {
            titleEditText.setText(initialValue)
            titleInputLayout.isVisible = visible
            if (hasMultipleValues) {
                titleInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(titleInputLayout, titleEditText, _data.titleField, textChangeListener)

        data.albumArtistField.apply {
            albumArtistEditText.setText(initialValue)
            albumArtistInputLayout.isVisible = visible
            if (hasMultipleValues) {
                albumArtistInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(albumArtistInputLayout, albumArtistEditText, _data.albumArtistField, textChangeListener)

        data.artistField.apply {
            artistEditText.setText(initialValue)
            artistInputLayout.isVisible = visible
            if (hasMultipleValues) {
                artistInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(artistInputLayout, artistEditText, _data.artistField, textChangeListener)

        data.albumField.apply {
            albumInputLayout.isVisible = visible
            if (hasMultipleValues) {
                albumInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
            albumEditText.setText(initialValue)
        }
        setupResetButton(albumInputLayout, albumEditText, _data.albumField, textChangeListener)

        data.dateField.apply {
            yearEditText.setText(initialValue)
            yearInputLayout.isVisible = visible
            if (hasMultipleValues) {
                yearInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(yearInputLayout, yearEditText, _data.dateField, textChangeListener)

        data.trackField.apply {
            trackEditText.setText(initialValue)
            trackInputLayout.isVisible = visible
            if (hasMultipleValues) {
                trackInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(trackInputLayout, trackEditText, _data.trackField, textChangeListener)

        data.trackTotalField.apply {
            trackTotalEditText.setText(initialValue)
            trackTotalInputLayout.isVisible = visible
            if (hasMultipleValues) {
                trackTotalInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(trackTotalInputLayout, trackTotalEditText, _data.trackTotalField, textChangeListener)

        data.discField.apply {
            discEditText.setText(initialValue)
            discInputLayout.isVisible = visible
            if (hasMultipleValues) {
                discInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(discInputLayout, discEditText, _data.discField, textChangeListener)

        data.discTotalField.apply {
            discTotalEditText.setText(initialValue)
            discTotalInputLayout.isVisible = visible
            if (hasMultipleValues) {
                discTotalInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }
        setupResetButton(discTotalInputLayout, discTotalEditText, _data.discTotalField, textChangeListener)

        data.genreField.apply {
            genreEditText.setText(initialValue)
            genreInputLayout.isVisible = visible
            if (hasMultipleValues) {
                genreInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }

        setupResetButton(genreInputLayout, genreEditText, _data.genreField, textChangeListener)

        data.lyricsField.apply {
            lyricsEditText.setText(initialValue)
            lyricsInputLayout.isVisible = visible
            if (hasMultipleValues) {
                lyricsInputLayout.helperText = getString(R.string.edit_tags_multiple_values)
            }
        }

        setupResetButton(lyricsInputLayout, lyricsEditText, _data.lyricsField, textChangeListener)
    }

    override fun setLoading(loadingState: TagEditorContract.LoadingState) {
        val loading = loadingState != TagEditorContract.LoadingState.None

        uiGroup.isVisible = !loading

        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isVisible = !loading
        }
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            isVisible = !loading
        }

        when (loadingState) {
            is TagEditorContract.LoadingState.None -> {
                loadingView.setState(CircularLoadingView.State.None)
            }
            is TagEditorContract.LoadingState.ReadingTags -> {
                loadingView.setState(
                    CircularLoadingView.State.Loading(
                        Phrase.from(requireContext(), R.string.edit_tags_reading_tags)
                            .put("progress", loadingState.progress.toString())
                            .put("total", loadingState.total.toString())
                            .format()
                            .toString()
                    )
                )
            }
            is TagEditorContract.LoadingState.WritingTags -> {
                loadingView.setState(
                    CircularLoadingView.State.Loading(
                        Phrase.from(requireContext(), R.string.edit_tags_writing_tags)
                            .put("progress", loadingState.progress)
                            .put("total", loadingState.total)
                            .format()
                            .toString()
                    )
                )
            }
        }
    }

    override fun closeWithToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        dismiss()
    }


    // Static

    companion object {
        const val TAG = "EditTagsAlertDialog"

        const val ARG_SONGS = "songs"

        fun newInstance(
            songs: List<com.simplecityapps.shuttle.model.Song>? = emptyList()
        ): TagEditorAlertDialog = TagEditorAlertDialog().withArgs {
            putParcelableArrayList(ARG_SONGS, ArrayList(songs))
        }
    }
}