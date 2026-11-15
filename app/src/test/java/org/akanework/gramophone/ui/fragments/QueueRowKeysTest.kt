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

package org.akanework.gramophone.ui.fragments

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRowKeysTest {
    private val old = listOf(DragRow(1L, "a"), DragRow(2L, "b"), DragRow(3L, "c"))

    @Test
    fun insertedDuplicateTakesANewKeyAndTheOriginalKeepsItsOwn() {
        var seed = 10L
        val rows = reuseRowKeys(old, listOf("a", "b", "b", "c")) { seed++ }

        assertEquals(listOf(1L, 2L, 10L, 3L), rows.map { it.key })
    }

    @Test
    fun removedDuplicateDropsItsKeyAndTheRestKeepTheirs() {
        val duplicated = listOf(DragRow(1L, "a"), DragRow(2L, "b"), DragRow(3L, "b"), DragRow(4L, "c"))
        val rows = reuseRowKeys(duplicated, listOf("a", "b", "c")) { error("no new key expected") }

        assertEquals(listOf(1L, 2L, 4L), rows.map { it.key })
    }
}
