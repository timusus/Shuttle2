package com.simplecityapps.shuttle.ui.common.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.squareup.phrase.Phrase

fun ShowExcludeDialog(context: Context, itemName: String?, onExclude: () -> Unit): AlertDialog {
    return MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.dialog_exclude_title))
        .setMessage(
            Phrase.from(context, R.string.dialog_exclude_message)
                .put("item", itemName ?: context.getString(R.string.unknown))
                .format()
        )
        .setPositiveButton(context.getString(R.string.dialog_exclude_button)) { _, _ ->
            onExclude()
        }
        .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
        .show()
}
