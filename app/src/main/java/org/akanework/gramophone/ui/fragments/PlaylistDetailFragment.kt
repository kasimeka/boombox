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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.getFile
import org.akanework.gramophone.logic.queue.M3uImport
import org.akanework.gramophone.logic.queue.PlaylistStore
import org.akanework.gramophone.logic.setMediaItemsWithTitle
import org.akanework.gramophone.ui.GramophoneTheme
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.pureDark
import java.io.File

/** Read-only view of a playlist's songs. Tapping a song plays the playlist from that song. */
class PlaylistDetailFragment : BaseFragment() {
    companion object {
        const val ARG_PATH = "Path"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val activity = requireActivity() as MainActivity
        val file = File(requireArguments().getString(ARG_PATH)!!)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GramophoneTheme(pureDark = pureDark(requireContext())) {
                    PlaylistDetailScreen(
                        activity = activity,
                        file = file,
                        onBack = { activity.supportFragmentManager.popBackStack() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailScreen(activity: MainActivity, file: File, onBack: () -> Unit) {
    // The playlist was deleted while it was open: close it and let the index forget it.
    LaunchedEffect(file) {
        if (!file.isFile) {
            PlaylistStore.forgetMissing(activity, file)
            onBack()
        }
    }
    val songs by produceState<List<MediaItem>?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            val songsByPath = activity.reader.songListFlow.first()
                .associateBy { it.getFile()?.absolutePath }
            M3uImport.resolve(PlaylistStore.entries(file)) { songsByPath[it] }.matches
        }
    }
    val bottomPadding = with(LocalDensity.current) {
        activity.playerBottomSheet.bottomPadding.intValue.toDp()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.nameWithoutExtension) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val list = songs
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {}
        } else {
            LazyColumn(contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + bottomPadding,
            )) {
                itemsIndexed(list) { index, item ->
                    ListItem(
                        headlineContent = { Text(item.mediaMetadata.title?.toString().orEmpty()) },
                        supportingContent = { Text(item.mediaMetadata.artist?.toString().orEmpty()) },
                        modifier = Modifier.clickable {
                            playFrom(activity, file, list, index)
                        },
                    )
                }
            }
        }
    }
}

/** Plays the playlist's songs from [index]. Controller calls run on the main thread. */
private fun playFrom(activity: MainActivity, file: File, songs: List<MediaItem>, index: Int) {
    val controller = activity.getPlayer() ?: return
    controller.setMediaItemsWithTitle(
        songs,
        position = index,
        title = file.nameWithoutExtension,
        original = true,
        source = PlaylistStore.source(file),
    )
    controller.prepare()
    controller.play()
}
