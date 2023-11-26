package com.simplecityapps.shuttle.ui.common.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simplecityapps.shuttle.R
import com.squareup.phrase.Phrase

fun ShowDeleteDialog(context: Context, itemName: String?, onDelete: () -> Unit): AlertDialog {
    return MaterialAlertDialogBuilder(context)
        .setTitle(context.getString(R.string.dialog_delete_title))
        .setMessage(
            Phrase.from(context, R.string.dialog_delete_message)
                .put("item", itemName ?: context.getString(com.simplecityapps.core.R.string.unknown))
                .format()
        )
        .setPositiveButton(context.getString(R.string.dialog_delete_button)) { _, _ ->
            onDelete()
        }
        .setNegativeButton(context.getString(R.string.dialog_button_cancel), null)
        .show()
}
