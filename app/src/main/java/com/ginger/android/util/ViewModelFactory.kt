package com.ginger.android.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Общая фабрика ViewModel для создания ViewModel с пользовательскими зависимостями.
 */
class ViewModelFactory<T : ViewModel>(
    private val modelClass: Class<T>,
    private val creator: () -> T
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T2 : ViewModel> create(modelClass: Class<T2>): T2 {
        if (!this.modelClass.isAssignableFrom(modelClass)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return creator() as T2
    }
}
