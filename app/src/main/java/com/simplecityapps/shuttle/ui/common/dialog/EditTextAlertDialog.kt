package com.simplecityapps.shuttle.ui.common.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.withArgs

open class EditTextAlertDialog : DialogFragment() {

    interface Listener {
        fun onSave(text: String?)
        fun validate(string: String?): Boolean {
            return !string.isNullOrEmpty()
        }
    }

    private lateinit var editText: EditText

    private var hint: String? = null
    private var initialText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hint = arguments?.getString(ARG_HINT)
        initialText = arguments?.getString(ARG_INITIAL_TEXT)
    }

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val view = LayoutInflater.from(context!!).inflate(R.layout.fragment_dialog_edit_text, null)

        val textInputLayout: TextInputLayout = view.findViewById(R.id.inputLayout)
        hint?.let { textInputLayout.hint = hint }

        editText = view.findViewById(R.id.editText)
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validate()
            }
        })
        initialText?.let { editText.setText(initialText) }

        return AlertDialog.Builder(context!!)
            .setView(view)
            .setNegativeButton("Close", null)
            .setPositiveButton("Save") { _, _ -> onSave(editText.text.toString()) }
            .create()
    }

    override fun onResume() {
        super.onResume()

        validate()

        editText.post {
            editText.requestFocus()
            val inputMethodManager = context!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(editText, 0)
        }
    }

    open fun onSave(string: String) {
        (parentFragment as? Listener)?.onSave(string)
    }

    private fun validate() {
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = isValid(editText.text.toString())
    }

    open fun isValid(string: String?): Boolean {
        return (parentFragment as? Listener)?.validate(string) ?: !string.isNullOrEmpty()
    }

    fun show(manager: FragmentManager) {
        super.show(manager, TAG)
    }


    companion object {
        const val TAG = "EditTextAlertDialog"

        const val ARG_HINT = "hint"
        const val ARG_INITIAL_TEXT = "initial_text"

        fun newInstance(
            hint: String? = null,
            initialText: String? = null
        ): EditTextAlertDialog = EditTextAlertDialog().withArgs {
            putString(ARG_HINT, hint)
            putString(ARG_INITIAL_TEXT, initialText)
        }
    }
}