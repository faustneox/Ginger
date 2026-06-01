package com.ginger.android.ui.common

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

/**
 * Кастомный ItemAnimator с более заметной анимацией удаления элемента.
 * Элемент уменьшается, немного сдвигается влево и исчезает.
 */
class DeleteItemAnimator : DefaultItemAnimator() {

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        val itemView = holder.itemView

        val scaleX = ObjectAnimator.ofFloat(itemView, View.SCALE_X, 1f, 0.75f)
        val scaleY = ObjectAnimator.ofFloat(itemView, View.SCALE_Y, 1f, 0.75f)
        val alpha = ObjectAnimator.ofFloat(itemView, View.ALPHA, 1f, 0f)
        val translationX = ObjectAnimator.ofFloat(itemView, View.TRANSLATION_X, 0f, -80f)

        val duration = 200L
        scaleX.duration = duration
        scaleY.duration = duration
        alpha.duration = duration
        translationX.duration = duration

        val interpolator = AccelerateInterpolator()
        scaleX.interpolator = interpolator
        scaleY.interpolator = interpolator
        alpha.interpolator = interpolator
        translationX.interpolator = interpolator

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha, translationX)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    dispatchRemoveFinished(holder)
                    itemView.apply {
                        this.alpha = 1f
                        this.scaleX = 1f
                        this.scaleY = 1f
                        this.translationX = 0f
                    }
                }
            })
            start()
        }

        return true
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        return super.animateAdd(holder)
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        return super.animateChange(oldHolder, newHolder, fromX, fromY, toX, toY)
    }
}
