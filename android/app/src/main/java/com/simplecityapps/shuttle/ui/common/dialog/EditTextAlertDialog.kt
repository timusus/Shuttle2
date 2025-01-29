package com.simplecityapps.shuttle.ui.common.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.withArgs

open class EditTextAlertDialog : DialogFragment() {
    interface Listener {
        fun onSave(
            text: String?,
            extra: Parcelable? = null
        )

        fun validate(
            string: String?,
            extra: Parcelable? = null
        ): Boolean = !string.isNullOrEmpty()
    }

    private var editText: EditText? = null

    private var title: String? = null
    private var hint: String? = null
    private var initialText: String? = null
    private var extra: Parcelable? = null
    private var inputType: Int = InputType.TYPE_TEXT_FLAG_CAP_WORDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = arguments?.getString(ARG_TITLE)
        hint = arguments?.getString(ARG_HINT)
        initialText = arguments?.getString(ARG_INITIAL_TEXT)
        extra = arguments?.getParcelable(ARG_EXTRA)
        inputType = arguments?.getInt(ARG_INPUT_TYPE, InputType.TYPE_TEXT_FLAG_CAP_WORDS) ?: InputType.TYPE_TEXT_FLAG_CAP_WORDS
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = layoutInflater.inflate(R.layout.fragment_dialog_edit_text, null)

        val textInputLayout: TextInputLayout = view.findViewById(R.id.inputLayout)
        hint?.let { textInputLayout.hint = hint }

        editText = view.findViewById(R.id.editText)
        editText?.addTextChangedListener(
            object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {}

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    validate()
                }
            }
        )
        editText?.inputType = inputType
        initialText?.let {
            editText!!.setText(initialText)
            editText!!.setSelection(editText!!.length())
        }

        return MaterialAlertDialogBuilder(requireContext())
            .apply { title?.let { setTitle(title) } }
            .setView(view)
            .setNegativeButton(getString(R.string.dialog_button_close), null)
            .setPositiveButton(getString(R.string.dialog_button_save)) { _, _ -> onSave(editText!!.text.toString()) }
            .create()
    }

    override fun onResume() {
        super.onResume()

        validate()

        editText?.postDelayed({
            editText?.let { editText ->
                editText.requestFocus()
                val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        }, 100)
    }

    override fun onDestroyView() {
        editText = null
        super.onDestroyView()
    }

    open fun onSave(string: String) {
        (parentFragment as? Listener)?.onSave(string, extra)
    }

    private fun validate() {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid(editText?.text?.toString())
    }

    open fun isValid(string: String?): Boolean = (parentFragment as? Listener)?.validate(string, extra) ?: !string.isNullOrEmpty()

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }

    companion object {
        const val TAG = "EditTextAlertDialog"

        const val ARG_TITLE = "title"
        const val ARG_HINT = "hint"
        const val ARG_INITIAL_TEXT = "initial_text"
        const val ARG_EXTRA = "extra"
        const val ARG_INPUT_TYPE = "input_type"

        fun newInstance(
            title: String? = null,
            hint: String? = null,
            initialText: String? = null,
            extra: Parcelable? = null,
            inputType: Int = InputType.TYPE_TEXT_FLAG_CAP_WORDS
        ): EditTextAlertDialog = EditTextAlertDialog().withArgs {
            putString(ARG_TITLE, title)
            putString(ARG_HINT, hint)
            putString(ARG_INITIAL_TEXT, initialText)
            putParcelable(ARG_EXTRA, extra)
            putInt(ARG_INPUT_TYPE, inputType)
        }
    }
}
