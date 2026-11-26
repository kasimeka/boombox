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

package org.akanework.gramophone.ui.components.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.akanework.gramophone.logic.ui.SwipeRules
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.ui.SongMenuContext
import kotlinx.coroutines.delay
import uk.akane.libphonograph.items.albumId
import uk.akane.libphonograph.items.artistId

private const val HIGHLIGHT_FADE_IN_MS = 200
private const val HIGHLIGHT_HOLD_MS = 600L
private const val HIGHLIGHT_FADE_OUT_MS = 400

/**
 * Song row used in queue detail, reusing the regular song row's content, menu, and actions with
 * queue-specific affordances (drag handle/reorder and "Remove from queue").
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun QueueSongRow(
    item: MediaItem?,
    isDragging: Boolean,
    dragTranslation: () -> Float,
    isCurrent: Boolean,
    isPlaying: Boolean,
    canPlayNext: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onDetails: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    highlighted: Boolean = false,
    onHighlightEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val currentOnHighlightEnd by rememberUpdatedState(onHighlightEnd)
    val currentOnRemoveFromQueue by rememberUpdatedState(onRemoveFromQueue)
    val highlightAlpha = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (!highlighted) return@LaunchedEffect
        highlightAlpha.animateTo(1f, tween(HIGHLIGHT_FADE_IN_MS))
        delay(HIGHLIGHT_HOLD_MS)
        highlightAlpha.animateTo(0f, tween(HIGHLIGHT_FADE_OUT_MS))
        currentOnHighlightEnd()
    }
    val swipeOffset = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = dragTranslation() }
            .fillMaxWidth()
            .height(72.dp)
            .clipToBounds(),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.remove_from_queue),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = highlightAlpha.value))
                .swipeToRemove(
                    enabled = !isDragging,
                    offset = swipeOffset,
                    onRemove = { currentOnRemoveFromQueue() },
                )
                .clickable(onClick = onPlay)
                .padding(start = 24.dp, end = 6.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item?.mediaMetadata?.artworkUri)
                    .crossfade(true)
                    .placeholder(R.drawable.ic_default_cover)
                    .error(R.drawable.ic_default_cover)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
            if (isCurrent) {
                NowPlayingInlineIcon(isPlaying = isPlaying)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = nowPlayingTextStartPadding(isCurrent)),
            ) {
                Text(
                    text = item?.mediaMetadata?.title?.toString()
                        ?: context.getString(R.string.unknown_title),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = nowPlayingTitleColor(isCurrent),
                )
                Text(
                    text = item?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.play_next)) },
                        enabled = canPlayNext,
                        onClick = {
                            menuOpen = false
                            onPlayNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.add_to_queue)) },
                        onClick = {
                            menuOpen = false
                            onAddToQueue()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.go_to_album)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canGoToAlbum(item),
                        onClick = {
                            menuOpen = false
                            onGoToAlbum()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.go_to_artist)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canGoToArtist(item),
                        onClick = {
                            menuOpen = false
                            onGoToArtist()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.details)) },
                        enabled = item != null,
                        onClick = {
                            menuOpen = false
                            onDetails()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canDelete(item),
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        enabled = org.akanework.gramophone.ui.SongMenuLogic.canShare(item),
                        onClick = {
                            menuOpen = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove_from_queue)) },
                        onClick = {
                            menuOpen = false
                            onRemoveFromQueue()
                        },
                    )
                }
            }
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(24.dp),
            )
        }
    }
}

/**
 * Swipes the row left to remove it, the way ItemTouchHelper does: the list keeps any gesture it
 * has started scrolling, and a swipe only begins on a leftward drag that is clearly more
 * horizontal than vertical. Past [SWIPE_REMOVE_FRACTION] of the width, or on a fast leftward
 * fling, the row slides fully away to its red background and is removed; otherwise it springs back.
 * Follows the swipe-to-dismiss sample in the Compose animation guide.
 */
private fun Modifier.swipeToRemove(
    enabled: Boolean,
    offset: Animatable<Float, AnimationVector1D>,
    onRemove: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocity = VelocityTracker()
            var travelled = Offset.Zero
            var claimed = false
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!claimed) {
                    // The list already took this gesture as a scroll.
                    if (change.isConsumed) return@awaitEachGesture
                    travelled += change.positionChange()
                    if (travelled.getDistance() < viewConfiguration.touchSlop) continue
                    claimed = travelled.x < 0 && SwipeRules.isHorizontal(travelled.x, travelled.y)
                    if (!claimed) return@awaitEachGesture
                }
                val dx = change.positionChange().x
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                launch { offset.snapTo((offset.value + dx).coerceAtMost(0f)) }
            }
            if (!claimed) return@awaitEachGesture
            val width = size.width.toFloat()
            val flingPx = SwipeRules.FLING_DP_PER_SEC.dp.toPx()
            val committed = SwipeRules.isCommitted(
                -offset.value, width, -velocity.calculateVelocity().x, flingPx
            )
            launch {
                if (committed) {
                    offset.animateTo(-width, tween(SwipeRules.SETTLE_MS.toInt()))
                    onRemove()
                } else {
                    offset.animateTo(0f, tween(SwipeRules.SETTLE_MS.toInt()))
                }
            }
        }
    }
}
