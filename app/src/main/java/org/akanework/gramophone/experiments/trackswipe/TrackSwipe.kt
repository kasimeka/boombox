/*
 *     Copyright (C) 2026 The Gramophone authors
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.experiments.trackswipe

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import org.akanework.gramophone.experiments.Experiments
import org.akanework.gramophone.logic.ui.SwipeRules
import kotlin.math.abs
import kotlin.math.sign

/**
 * Swipe the full player left or right to change track. A drag that turns out twice as horizontal as
 * vertical claims the touch, unless it started on a seek bar, which keeps its own drag. Taps and
 * vertical drags are never claimed, so buttons, clicks and the sheet's drag still work.
 *
 * The cover and its neighbours move together while dragging, so the next or previous cover peeks in.
 */
class TrackSwipe(
    private val context: Context,
    private val cover: View,
    /** The previous and next covers, as (prev, next). Swipes are off without them. */
    private val neighbours: Pair<View, View>?,
    private val seekBars: List<View>,
    private val onSkip: (next: Boolean) -> Unit,
) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val flingPx = SwipeRules.FLING_DP_PER_SEC * context.resources.displayMetrics.density
    private val velocity = VelocityTracker.obtain()
    private val hitRect = Rect()
    private var downX = 0f
    private var downY = 0f

    var tracking = false
        private set
    var claimed = false
        private set

    /** Decides whether this touch is a track swipe. Returns true once it is. */
    fun detect(ev: MotionEvent, parent: ViewParent?): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                claimed = false
                downX = ev.x
                downY = ev.y
                // Read on every touch-down, so toggling the experiment applies at once.
                tracking = neighbours != null && Experiments.trackSwipe(context) &&
                    !onSeekBar(ev.rawX.toInt(), ev.rawY.toInt())
                velocity.clear()
                velocity.addMovement(ev)
            }

            MotionEvent.ACTION_MOVE -> if (tracking && !claimed) {
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (abs(dx) > touchSlop && SwipeRules.isHorizontal(dx, dy)) {
                    claimed = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return claimed
    }

    /** Follows a claimed drag and settles it on release. */
    fun touch(ev: MotionEvent) {
        val (prev, next) = neighbours ?: return
        velocity.addMovement(ev)
        val dx = ev.x - downX
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                cover.translationX = dx
                prev.translationX = dx
                next.translationX = dx
            }
            MotionEvent.ACTION_UP -> settle(dx)
            MotionEvent.ACTION_CANCEL -> settle(0f)
        }
    }

    private fun onSeekBar(x: Int, y: Int): Boolean =
        seekBars.any { it.isVisible && it.getGlobalVisibleRect(hitRect) && hitRect.contains(x, y) }

    /**
     * Same shape as the Compose swipe-to-dismiss sample: a drag that passes the threshold carries
     * the cover off in the swipe direction and the neighbour on that side slides in to take its
     * place; any other release springs back to 0.
     */
    private fun settle(dx: Float) {
        val (prev, next) = neighbours ?: return
        claimed = false
        tracking = false
        velocity.computeCurrentVelocity(1000)
        val vx = velocity.xVelocity
        // +1 when swiping right (previous track), -1 when swiping left (next track).
        val direction = sign(if (dx != 0f) dx else vx)
        // Distance from the cover to the neighbour beside it, which is also how far a skip travels.
        val step = (next.left - cover.left).toFloat()
        if (SwipeRules.isCommitted(direction * dx, step, direction * vx, flingPx)) {
            val goingNext = direction < 0
            val incoming = if (goingNext) next else prev
            val outgoing = if (goingNext) prev else next
            slide(incoming, direction * step)
            slide(outgoing, 0f)
            slide(cover, direction * step) {
                onSkip(goingNext)
                cover.translationX = 0f
                incoming.translationX = 0f
            }
        } else {
            slide(cover, 0f)
            slide(prev, 0f)
            slide(next, 0f)
        }
    }

    private fun slide(view: View, to: Float, end: (() -> Unit)? = null) {
        view.animate().translationX(to).setDuration(SwipeRules.SETTLE_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { end?.invoke() }
            .start()
    }
}
