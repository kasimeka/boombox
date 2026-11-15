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

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.widget.Toast
import org.akanework.gramophone.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Playlists are the m3u8 files the media index knows about, except the ones Gramophone generates
 * for queues and favourites. Saved playlists are written to the app's media folder and scanned in.
 */
object PlaylistStore {
    private const val FOLDER = "playlists"
    private const val EXTENSION = "m3u8"
    private const val MIME_TYPE = "audio/x-mpegurl"
    private val illegalFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val queueMirrorName =
        Regex("_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.m3u8$")

    data class Playlist(val file: File, val title: String, val songCount: Int)

    private val savedCount = MutableStateFlow(0)

    /** Goes up each time a playlist has been scanned in, so screens reload the list. */
    val revision: StateFlow<Int> = savedCount.asStateFlow()

    private fun isAutomated(file: File) =
        file.name == QueueExport.FAVOURITES_FILE_NAME || queueMirrorName.containsMatchIn(file.name)

    /** Every playlist in the media index, except automated ones, sorted by title. */
    @Suppress("DEPRECATION")
    fun list(context: Context): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        context.contentResolver.query(
            MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Playlists.NAME, MediaStore.Audio.Playlists.DATA),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Playlists.DATA)
            while (cursor.moveToNext()) {
                val file = File(cursor.getString(dataColumn) ?: continue)
                if (!file.isFile || isAutomated(file)) continue
                playlists += Playlist(
                    file = file,
                    title = cursor.getString(nameColumn) ?: file.nameWithoutExtension,
                    songCount = entries(file).size,
                )
            }
        }
        return playlists.sortedBy { it.title.lowercase(Locale.ROOT) }
    }

    /** The queue source for a playlist, keyed by its file so replays reuse the same queue. */
    fun source(file: File) = QueueSource(
        kind = QueueSourceKind.PLAYLIST,
        identifier = file.absolutePath,
        displayName = file.nameWithoutExtension,
    )

    /**
     * Has the media index drop a playlist file that is gone, then reloads the lists. Until the scan
     * finishes, [list] already leaves the missing file out.
     */
    fun forgetMissing(context: Context, file: File) {
        Toast.makeText(context, R.string.playlist_not_found, Toast.LENGTH_SHORT).show()
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME_TYPE)) { _, _ ->
            savedCount.update { it + 1 }
        }
    }

    /** The song paths a playlist file lists, in playlist order. A file that is gone lists nothing. */
    fun entries(file: File): List<String> =
        try {
            M3uImport.entries(file.readText())
        } catch (_: IOException) {
            emptyList()
        }

    /**
     * Writes [rows] to a new playlist named after [title], keeping its punctuation. Characters that
     * cannot appear in a file name are dropped, and a taken name gets a number. The file is scanned
     * into the media index, and [revision] goes up once that finishes. Returns null when the app's
     * media folder is unavailable.
     */
    fun create(context: Context, title: String, rows: List<QueueExportRow>): File? {
        val dir = context.externalMediaDirs.firstOrNull()?.let { File(it, FOLDER) } ?: return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val base = illegalFileNameCharacters.replace(title, "").trim().ifEmpty { "Playlist" }
        var file = File(dir, "$base.$EXTENSION")
        var suffix = 2
        while (file.exists()) {
            file = File(dir, "$base ($suffix).$EXTENSION")
            suffix++
        }
        file.writeBytes(QueueExport.content(rows))
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(MIME_TYPE)) { _, _ ->
            savedCount.update { it + 1 }
        }
        return file
    }
}
