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

package org.akanework.gramophone.logic.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueOrderEditorTest {
    @Test
    fun removeKeepsRemainingOrder() {
        assertEquals(
            QueueEditResult.Applied(listOf(0, 1, 3, 4)),
            QueueOrderEditor.remove(listOf(0, 1, 2, 3, 4), 2)
        )
    }

    @Test
    fun removeWithInvalidPositionIsNoOp() {
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(listOf(0, 1), -1))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(listOf(0, 1), 2))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.remove(emptyList(), 0))
    }

    @Test
    fun moveKeepsEveryRowAndPutsItAtTheDropPosition() {
        assertEquals(
            QueueEditResult.Applied(listOf(0, 2, 3, 1, 4)),
            QueueOrderEditor.move(listOf(0, 1, 2, 3, 4), 1, 3)
        )
    }

    @Test
    fun moveWithInvalidOrUnchangedPositionsIsNoOp() {
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.move(listOf(0, 1), 0, 0))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.move(listOf(0, 1), -1, 1))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.move(listOf(0, 1), 0, 2))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.move(emptyList(), 0, 0))
    }

    @Test
    fun shuffleIsDisabledWithoutAnchor() {
        assertFalse(QueueOrderEditor.canShuffleEverything(4, null))
        assertEquals(
            QueueEditResult.NoOp,
            QueueOrderEditor.shuffleEverything(listOf(0, 1, 2, 3), null)
        )
    }

    @Test
    fun shuffleIsDisabledForEmptyAndOneRowQueues() {
        assertFalse(QueueOrderEditor.canShuffleEverything(0, null))
        assertFalse(QueueOrderEditor.canShuffleEverything(1, 0))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.shuffleEverything(emptyList(), 0))
        assertEquals(QueueEditResult.NoOp, QueueOrderEditor.shuffleEverything(listOf(0), 0))
    }

    @Test
    fun shuffleIsDisabledForInvalidAnchor() {
        assertFalse(QueueOrderEditor.canShuffleEverything(4, -1))
        assertFalse(QueueOrderEditor.canShuffleEverything(4, 4))
        assertEquals(
            QueueEditResult.NoOp,
            QueueOrderEditor.shuffleEverything(listOf(0, 1, 2, 3), 4)
        )
    }

    @Test
    fun shuffleMovesAnchorFirstAndKeepsEveryRow() {
        val order = listOf(0, 1, 2, 3, 4)
        val result = QueueOrderEditor.shuffleEverything(order, 3, Random(1))
            as QueueEditResult.Applied

        assertEquals(3, result.order.first())
        assertEquals(order.size, result.order.size)
        assertEquals(order.toSet(), result.order.toSet())
    }

    @Test
    fun shuffleKeepsOrderIdentityNotJustPositions() {
        val order = listOf(4, 2, 0, 3, 1)
        val result = QueueOrderEditor.shuffleEverything(order, 2, Random(5))
            as QueueEditResult.Applied

        assertEquals(0, result.order.first())
        assertEquals(order.toSet(), result.order.toSet())
    }

    @Test
    fun everyNonAnchorRowCanMoveAcrossShuffles() {
        val order = (0..5).toList()
        val seen = mutableSetOf<Int>()
        repeat(200) {
            val result = QueueOrderEditor.shuffleEverything(order, 0, Random(it))
                as QueueEditResult.Applied
            seen.addAll(result.order.drop(1).take(1))
        }

        assertEquals(order.drop(1).toSet(), seen)
    }

    @Test
    fun shufflePlayRandomReturnsPermutationStartingWithRandomTrack() {
        val order = (0 until 10).toList()
        val firstRows = mutableSetOf<Int>()
        repeat(100) { seed ->
            val result = QueueOrderEditor.shufflePlayRandom(order, Random(seed))
            assertEquals(order.size, result.order.size)
            assertEquals(order.toSet(), result.order.toSet())
            firstRows.add(result.order.first())
        }
        // Over 100 seeds across 10 elements, we should see multiple different starting tracks
        assertTrue(firstRows.size > 1)
    }

    @Test
    fun shufflePlayRandomSingleItemReturnsSameItem() {
        val result = QueueOrderEditor.shufflePlayRandom(listOf(42))
        assertEquals(listOf(42), result.order)
    }

    @Test(expected = IllegalArgumentException::class)
    fun shufflePlayRandomEmptyThrows() {
        QueueOrderEditor.shufflePlayRandom(emptyList())
    }
}
