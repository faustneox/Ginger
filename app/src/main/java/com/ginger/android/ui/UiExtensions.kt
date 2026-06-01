package com.ginger.android.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ginger.android.ui.requests.MainActivity

fun AppCompatActivity.showErrorBanner(
    textView: TextView,
    message: String,
    autoHideMillis: Long = 0L,
    showToast: Boolean = false,
    onHide: (() -> Unit)? = null
) {
    textView.text = message
    textView.alpha = 0f
    textView.visibility = View.VISIBLE
    textView.animate()
        .alpha(1f)
        .setDuration(180)
        .start()

    if (showToast) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    if (autoHideMillis > 0) {
        textView.postDelayed({
            hideErrorBanner(textView, onHide)
        }, autoHideMillis)
    }
}

fun AppCompatActivity.hideErrorBanner(
    textView: TextView,
    onHide: (() -> Unit)? = null
) {
    if (textView.visibility == View.VISIBLE) {
        textView.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                textView.visibility = View.GONE
                textView.alpha = 1f
                onHide?.invoke()
            }
            .start()
    }
}

fun AppCompatActivity.hideKeyboard() {
    val view = this.currentFocus
    if (view != null) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}

fun AppCompatActivity.openMainAndFinish() {
    startActivity(Intent(this, MainActivity::class.java))
    finish()
}

fun AppCompatActivity.setViewsEnabled(enabled: Boolean, vararg views: View) {
    views.forEach { it.isEnabled = enabled }
}

fun AppCompatActivity.applyLoadingState(
    isLoading: Boolean,
    progressView: View,
    hideWhileLoading: Collection<View>,
    controlViews: Collection<View>
) {
    progressView.visibility = if (isLoading) View.VISIBLE else View.GONE
    hideWhileLoading.forEach { it.visibility = if (isLoading) View.GONE else View.VISIBLE }
    setViewsEnabled(!isLoading, *controlViews.toTypedArray())
}
