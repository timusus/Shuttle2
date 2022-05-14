package com.simplecityapps.shuttle.ui.screens.trial

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.utils.withArgs

class PromoCodeDialogFragment : DialogFragment() {

    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val promoCode = requireArguments().getString(ARG_PROMO_CODE)

        val view = layoutInflater.inflate(R.layout.dialog_promo_code, null)
        val promoCodeInputLayout: TextInputLayout = view.findViewById(R.id.promoCodeInputLayout)
        promoCodeInputLayout.editText!!.setText(promoCode)

        promoCodeInputLayout.setEndIconOnClickListener {
            val clipboardManager: ClipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboardManager.setPrimaryClip(ClipData.newPlainText("Promo code", promoCode))
            Toast.makeText(context, "Promo code copied to clipboard", Toast.LENGTH_LONG).show()
        }

        val descriptionTextView: TextView = view.findViewById(R.id.description)
        descriptionTextView.text =
            "Open the Play Store app, and navigate to 'notifications and offers', then choose the 'offers' tab, and enter your promo code.\n\nAlternatively, visit https://play.google.com/store/redeem?code=$promoCode in a web browser."

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setNegativeButton("Close") { _, _ -> dismiss() }
            .show()
    }

    fun show(fragmentManager: FragmentManager) {
        show(fragmentManager, TAG)
    }

    companion object {
        private const val TAG = "PromoCodeDialog"
        const val ARG_PROMO_CODE = "promo_code"
        fun newInstance(promoCode: String): PromoCodeDialogFragment = PromoCodeDialogFragment().withArgs {
            putString(ARG_PROMO_CODE, promoCode)
        }
    }
}
