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

package org.akanework.gramophone.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.queue.Queue
import org.akanework.gramophone.logic.queue.QueueListRules
import org.akanework.gramophone.logic.queue.QueueOrderEditor
import org.akanework.gramophone.logic.queue.QueuePlaybackAction
import org.akanework.gramophone.logic.queue.QueuePlaybackRules
import org.akanework.gramophone.logic.queue.QueuePlaybackState
import org.akanework.gramophone.logic.queue.QueueRowPresentation
import org.akanework.gramophone.logic.queue.QueueRowState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.queue.QueueDragReorder
import java.util.UUID

data class DragQueue(val key: Long, val queue: Queue)

data class PinnedQueueCollection(
    val id: String,
    val title: String,
    val songCount: Int,
    @DrawableRes val iconRes: Int,
    val onClick: () -> Unit,
    val onPlay: () -> Unit,
    val onShuffle: () -> Unit,
)

private class QueueListDragState {
    var draggingKey by mutableStateOf<Long?>(null)
    var dragStartIndex by mutableStateOf<Int?>(null)
    var dragStartQueues by mutableStateOf<List<Queue>>(emptyList())
    var dragOffset by mutableFloatStateOf(0f)
    var exitingKey by mutableStateOf<Long?>(null)
    var exitOffset by mutableFloatStateOf(0f)
    var overPlaylistZone by mutableStateOf(false)
}

/**
 * Shared queue list used by the Queues tab and the add-to-queue picker.
 */
@Composable
fun QueueListContent(
    queues: List<Queue>,
    activeQueueId: UUID?,
    isPlaying: Boolean,
    onQueueClick: (Queue) -> Unit,
    modifier: Modifier = Modifier,
    pinnedCollections: List<PinnedQueueCollection> = emptyList(),
    activeCurrentRow: Int? = null,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 88.dp),
    onPlayQueue: ((Queue) -> Unit)? = null,
    onShuffleQueue: ((Queue) -> Unit)? = null,
    onMoveQueue: ((from: Int, to: Int) -> Unit)? = null,
    onQueueConvertedToPlaylist: ((queue: Queue, restore: () -> Unit) -> Unit)? = null,
    showPlaylists: Boolean = false,
) {
    Box(modifier.fillMaxSize()) {
        if (queues.isEmpty() && pinnedCollections.isEmpty()) {
            Text(
                text = stringResource(R.string.queue_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            var pinnedExpanded by rememberSaveable { mutableStateOf(false) }
            val sectionExpanded = !showPlaylists || pinnedExpanded
            val pinnedRowCount = if (sectionExpanded) pinnedCollections.size else 0
            val headerItemCount =
                if (pinnedCollections.isNotEmpty()) pinnedRowCount + (if (showPlaylists) 2 else 1) else 0
            val scope = rememberCoroutineScope()
            var queueKeySeed by remember { mutableLongStateOf(0L) }
            var displayQueues by remember {
                mutableStateOf(queues.map { DragQueue(queueKeySeed++, it) })
            }
            val dragState = remember { QueueListDragState() }
            // The order the user dropped, held until the repository reports it. Without this the
            // stale repository order would briefly replace the dropped one.
            var pendingOrder by remember { mutableStateOf<List<UUID>?>(null) }

            LaunchedEffect(queues, dragState.draggingKey) {
                val pending = pendingOrder
                if (pending != null) {
                    if (queues.map { it.id } != pending) return@LaunchedEffect
                    pendingOrder = null
                }
                if (dragState.draggingKey == null) {
                    if (displayQueues.map { it.queue } != queues) {
                        displayQueues = queues.map { DragQueue(queueKeySeed++, it) }
                    }
                }
            }

            val rowHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
            val edgeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
            val latestDisplayQueues by rememberUpdatedState(displayQueues)

            fun onDragDelta(deltaY: Float, isScrollCompensation: Boolean = false) {
                val key = dragState.draggingKey ?: return
                val currentQueues = latestDisplayQueues
                val currentIndex = currentQueues.indexOfFirst { it.key == key }
                if (currentIndex < 0) return

                val step = QueueDragReorder.onDragDelta(
                    state = QueueDragReorder.State(
                        items = currentQueues,
                        currentIndex = currentIndex,
                        dragOffset = dragState.dragOffset,
                    ),
                    deltaY = deltaY,
                    rowHeightPx = rowHeightPx,
                    isScrollCompensation = isScrollCompensation,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex - headerItemCount,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )

                dragState.dragOffset = step.state.dragOffset
                if (step.swapped) {
                    displayQueues = step.state.items
                    if (step.requiresScrollAnchorReset) {
                        scope.launch {
                            listState.scrollToItem(headerItemCount, 0)
                        }
                    }
                }
            }

            fun isAboveSeparator(key: Long, dragOffset: Float): Boolean {
                val visible = listState.layoutInfo.visibleItemsInfo
                val dragged = visible.firstOrNull { it.key == key } ?: return false
                val separator = visible.firstOrNull { it.key == "pinned_divider" } ?: return false
                return dragged.offset + dragOffset < separator.offset + separator.size
            }

            // Incremental in-drag reordering with edge auto-scroll.
            LaunchedEffect(dragState.draggingKey) {
                if (dragState.draggingKey == null) return@LaunchedEffect
                while (dragState.draggingKey != null) {
                    val key = dragState.draggingKey ?: break
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                    if (info != null) {
                        val viewportStart = listState.layoutInfo.viewportStartOffset
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        val draggedTop = info.offset + dragState.dragOffset
                        val draggedBottom = draggedTop + info.size
                        val delta = when {
                            draggedTop < viewportStart + edgeThresholdPx ->
                                (draggedTop - (viewportStart + edgeThresholdPx)) * 0.2f
                            draggedBottom > viewportEnd - edgeThresholdPx ->
                                (draggedBottom - (viewportEnd - edgeThresholdPx)) * 0.2f
                            else -> 0f
                        }
                        if (delta != 0f &&
                            ((delta < 0f && listState.canScrollBackward) ||
                                (delta > 0f && listState.canScrollForward))
                        ) {
                            val consumed = listState.scrollBy(delta)
                            if (consumed != 0f) {
                                // List content scrolled by `consumed`. Compensate visual translation so the
                                // dragged item remains fixed under the finger.
                                onDragDelta(consumed, isScrollCompensation = true)
                            }
                        }
                    }
                    delay(16L)
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(rememberNestedScrollInteropConnection()),
            ) {
                if (pinnedCollections.isNotEmpty()) {
                    if (showPlaylists) {
                        item(key = "pinned_toggle") {
                            PinnedSectionToggle(
                                expanded = pinnedExpanded,
                                onToggle = { pinnedExpanded = !pinnedExpanded },
                            )
                        }
                    }
                    if (sectionExpanded) {
                        items(items = pinnedCollections, key = { "pinned_${it.id}" }) { collection ->
                            Box(Modifier.animateItem()) {
                                PinnedCollectionRow(item = collection)
                            }
                        }
                    }
                    item(key = "pinned_divider") {
                        if (showPlaylists) {
                            PinnedDropHint(active = dragState.overPlaylistZone)
                        } else {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
                if (displayQueues.isEmpty()) {
                    item(key = "queues_empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.queue_list_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                itemsIndexed(items = displayQueues, key = { _, item -> item.key }) { index, item ->
                    val queue = item.queue
                    val isActive = queue.id == activeQueueId
                    val row = remember(queue, activeQueueId, isPlaying) {
                        QueueListRules.row(
                            queue = queue,
                            isActive = isActive,
                            isPlaying = isPlaying,
                        )
                    }
                    val playbackAction = remember(queue, activeQueueId, isPlaying) {
                        QueuePlaybackRules.queuePlayButton(
                            QueuePlaybackState(
                                rowCount = queue.rows.size,
                                bookmark = queue.normalizedBookmark(),
                                hasBeenPlayed = queue.hasBeenPlayed || isActive,
                                isActive = isActive,
                                isPlaying = isActive && isPlaying,
                            )
                        )
                    }
                    val canShuffle = remember(queue, activeQueueId, activeCurrentRow) {
                        val anchor = QueuePlaybackRules.detailAnchor(
                            rowCount = queue.rows.size,
                            bookmark = queue.normalizedBookmark(),
                            isActive = isActive,
                            currentRow = if (isActive) activeCurrentRow else null,
                        )
                        QueueOrderEditor.canShuffleEverything(queue.rows.size, anchor)
                    }
                    val isDragging = dragState.draggingKey == item.key
                    val isExiting = dragState.exitingKey == item.key
                    val exitAlpha by animateFloatAsState(
                        targetValue = if (isExiting) 0f else 1f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "queueExit",
                        finishedListener = {
                            if (isExiting) {
                                onQueueConvertedToPlaylist?.invoke(queue) {
                                    dragState.exitingKey = null
                                }
                            }
                        },
                    )
                    val dragModifier = if (onMoveQueue != null) {
                        Modifier.pointerInput(item.key) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    val dragIndex = displayQueues.indexOfFirst { it.key == item.key }
                                    dragState.draggingKey = item.key
                                    dragState.dragStartIndex = dragIndex.takeIf { it >= 0 }
                                    dragState.dragStartQueues = displayQueues.map { it.queue }
                                    dragState.dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                    dragState.overPlaylistZone = showPlaylists && onQueueConvertedToPlaylist != null &&
                                        isAboveSeparator(item.key, dragState.dragOffset)
                                },
                                onDragEnd = {
                                    val key = dragState.draggingKey
                                    val startIndex = dragState.dragStartIndex
                                    dragState.overPlaylistZone = false
                                    val toPlaylist = key != null && showPlaylists && onQueueConvertedToPlaylist != null &&
                                        isAboveSeparator(key, dragState.dragOffset)
                                    dragState.draggingKey = null
                                    dragState.dragStartIndex = null
                                    dragState.dragStartQueues = emptyList()
                                    if (toPlaylist) {
                                        // The row stays where it was released and fades out; see exitAlpha.
                                        dragState.exitOffset = dragState.dragOffset
                                        dragState.dragOffset = 0f
                                        dragState.exitingKey = key
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    dragState.dragOffset = 0f
                                    if (key == null || startIndex == null) return@detectDragGesturesAfterLongPress
                                    val finalQueues = latestDisplayQueues
                                    val to = finalQueues.indexOfFirst { it.key == key }
                                    if (to < 0 || to == startIndex) return@detectDragGesturesAfterLongPress
                                    pendingOrder = finalQueues.map { it.queue.id }
                                    onMoveQueue(startIndex, to)
                                },
                                onDragCancel = {
                                    dragState.overPlaylistZone = false
                                    dragState.draggingKey = null
                                    dragState.dragStartIndex = null
                                    dragState.dragStartQueues = emptyList()
                                    dragState.dragOffset = 0f
                                },
                            )
                        }
                    } else {
                        Modifier
                    }
                    QueueListRow(
                        row = row,
                        playbackAction = playbackAction,
                        canShuffle = canShuffle,
                        showControls = onPlayQueue != null && onShuffleQueue != null,
                        onClick = { onQueueClick(queue) },
                        onPlay = onPlayQueue?.let { { it(queue) } },
                        onShuffle = onShuffleQueue?.let { { it(queue) } },
                        modifier = Modifier
                            .then(dragModifier)
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = when {
                                    isExiting -> dragState.exitOffset
                                    isDragging -> dragState.dragOffset
                                    else -> 0f
                                }
                                alpha = exitAlpha
                                scaleX = 0.92f + 0.08f * exitAlpha
                                scaleY = 0.92f + 0.08f * exitAlpha
                                if (isDragging) shadowElevation = 8f
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedSectionToggle(expanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "pinnedToggle")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
            .clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.playlists),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.ic_expand_more),
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinnedDropHint(active: Boolean) {
    val lineColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "dropLine",
    )
    val labelAlpha by animateFloatAsState(if (active) 1f else 0f, label = "dropLabel")
    // Grows smoothly when a queue is dragged over it, so the label has room without a jump.
    val height by animateDpAsState(if (active) 26.dp else 13.dp, label = "dropHeight")
    val linePadding by animateDpAsState(if (active) 2.dp else 6.dp, label = "dropLinePadding")
    Box(Modifier.fillMaxWidth().height(height).padding(horizontal = 20.dp)) {
        Text(
            text = stringResource(R.string.release_to_save_playlist),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = labelAlpha },
        )
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = linePadding),
            color = lineColor,
        )
    }
}

@Composable
private fun PinnedCollectionRow(
    item: PinnedQueueCollection,
    modifier: Modifier = Modifier,
) {
    val songCount = pluralStringResource(R.plurals.songs, item.songCount, item.songCount)
    val canPlay = item.songCount > 0
    val disabledTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .padding(end = 14.dp)
                .size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = songCount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = item.onPlay, enabled = canPlay) {
            Icon(
                painter = painterResource(R.drawable.ic_play_arrow),
                contentDescription = stringResource(R.string.play),
                tint = if (canPlay) MaterialTheme.colorScheme.primary else disabledTint,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(onClick = item.onShuffle, enabled = canPlay) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (canPlay) MaterialTheme.colorScheme.primary else disabledTint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun QueueListRow(
    row: QueueRowPresentation,
    playbackAction: QueuePlaybackAction,
    canShuffle: Boolean,
    showControls: Boolean,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val songCount = pluralStringResource(R.plurals.songs, row.songCount, row.songCount)
    val stateLabel = when (row.state) {
        // "paused", "playing", "empty" labels removed from rows; playing is shown via indicator.
        QueueRowState.PLAYING, QueueRowState.PAUSED, QueueRowState.EMPTY -> null
        QueueRowState.NEVER_PLAYED -> stringResource(R.string.queue_state_never_played)
        QueueRowState.ENDED -> stringResource(R.string.queue_state_ended)
    }
    // Keep which song was last playing/bookmarked, but remove the timestamp from the row.
    val bookmark = row.bookmarkPositionText?.let {
        row.bookmarkSongTitle ?: stringResource(R.string.queue_row_bookmark_song, row.bookmarkRow ?: 1)
    }
    val canPlay = playbackAction != QueuePlaybackAction.Disabled
    val isPlaying = playbackAction == QueuePlaybackAction.Pause

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (row.state == QueueRowState.PLAYING) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = listOfNotNull(songCount, stateLabel, bookmark).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showControls && onPlay != null && onShuffle != null) {
            IconButton(onClick = onPlay, enabled = canPlay) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = stringResource(R.string.pause),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            IconButton(onClick = onShuffle, enabled = canShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}


