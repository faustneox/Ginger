package com.ginger.android.ui.requests

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.ginger.android.R
import com.ginger.android.databinding.ActivityMainBinding

class MainTabManager(
    binding: ActivityMainBinding,
    private val onTabSelected: (Int) -> Unit
) {
    private val tabContainers: Array<View>
    private val tabLabels: Array<TextView>
    private val tabIndicators: Array<View>

    private val accentColor: Int
    private val secondaryColor: Int

    init {
        val context: Context = binding.root.context

        tabContainers = arrayOf(
            binding.tabAll,
            binding.tabNew,
            binding.tabInProgress,
            binding.tabClosed
        )

        tabLabels = arrayOf(
            binding.tabLabelAll,
            binding.tabLabelNew,
            binding.tabLabelInProgress,
            binding.tabLabelClosed
        )

        tabIndicators = arrayOf(
            binding.tabIndicatorAll,
            binding.tabIndicatorNew,
            binding.tabIndicatorInProgress,
            binding.tabIndicatorClosed
        )

        accentColor = ContextCompat.getColor(context, R.color.accent_purple)
        secondaryColor = ContextCompat.getColor(context, R.color.text_secondary)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        tabContainers.forEachIndexed { index, container ->
            container.setOnClickListener {
                onTabSelected(index)
            }
        }
    }

    fun updateSelection(selectedTab: Int) {
        tabLabels.forEachIndexed { index, label ->
            val selected = index == selectedTab
            label.setTextColor(if (selected) accentColor else secondaryColor)
            tabIndicators[index].visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }
    }
}
