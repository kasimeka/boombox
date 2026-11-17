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

package org.akanework.gramophone.logic.ui

import kotlin.math.abs

/** Shared by every horizontal swipe, so queue rows and the full player behave the same. */
object SwipeRules {
    /** Fraction of the width a swipe must pass to commit. */
    const val THRESHOLD_FRACTION = 0.3f

    /** A fling this fast (dp per second) commits regardless of distance. */
    const val FLING_DP_PER_SEC = 800

    const val SETTLE_MS = 180L

    /** A drag only counts as a horizontal swipe when it is twice as horizontal as it is vertical. */
    fun isHorizontal(dx: Float, dy: Float): Boolean = abs(dx) > 2 * abs(dy)

    /** [distance] and [velocity] are positive in the direction of the swipe being decided. */
    fun isCommitted(distance: Float, width: Float, velocity: Float, flingPx: Float): Boolean =
        distance > width * THRESHOLD_FRACTION || velocity > flingPx
}
