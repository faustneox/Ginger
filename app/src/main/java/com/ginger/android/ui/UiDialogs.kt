package com.ginger.android.ui

import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.ginger.android.R

fun AppCompatActivity.showConfirmationDialog(
    title: String,
    message: String,
    positiveLabel: String,
    negativeLabel: String? = null,
    fadeView: View? = null,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val negLabel = negativeLabel ?: getString(R.string.button_cancel)

    fadeView?.animate()?.alpha(0.6f)?.setDuration(120)?.start()

    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveLabel) { _, _ ->
            fadeView?.animate()?.alpha(1f)?.setDuration(80)?.start()
            onConfirm()
        }
        .setNegativeButton(negLabel) { _, _ ->
            fadeView?.animate()?.alpha(1f)?.setDuration(80)?.start()
            onCancel?.invoke()
        }
        .show()
}

fun AppCompatActivity.showLastAdminWarningDialog(
    message: String,
    fadeView: View? = null,
    onContinue: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    showConfirmationDialog(
        getString(R.string.dialog_attention_title),
        message,
        getString(R.string.button_continue),
        getString(R.string.button_cancel),
        fadeView,
        onContinue,
        onCancel
    )
}
