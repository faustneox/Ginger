package com.ginger.android.ui.common

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * Простой декоратор, который добавляет вертикальный отступ между элементами RecyclerView.
 * Также можно задать отступ сверху для первого элемента.
 */
class VerticalSpaceItemDecoration(
    private val verticalSpaceHeight: Int,
    private val topMarginForFirstItem: Int = 0
) : RecyclerView.ItemDecoration() {

    constructor(verticalSpaceHeight: Int) : this(verticalSpaceHeight, 0)

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        if (position == 0) {
            outRect.top = topMarginForFirstItem
        }

        if (position != itemCount - 1) {
            outRect.bottom = verticalSpaceHeight
        }
    }
}
