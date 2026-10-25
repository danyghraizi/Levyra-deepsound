package com.luc4n3x.levyra.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.luc4n3x.levyra.data.local.DownloadEntity
import com.luc4n3x.levyra.data.locallibrary.LOCAL_MEDIA_TRACK_ID_PREFIX
import com.luc4n3x.levyra.data.locallibrary.localIdentityKey
import java.io.File
import timber.log.Timber

/**
 * Scans the default Levyra download folder (`Music/Levyra`) for audio files that were placed
 * there outside the app (file transfer, restore) so they can be shown in the Offline section.
 */
object DownloadFolderScanner {
    const val FOLDER_NAME = "Levyra"

    fun scan(context: Context): List<DownloadEntity> {
        val resolver = context.applicationContext.contentResolver
        val scoped = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val collection = if (scoped) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.MediaColumns.DATE_ADDED
        )
        val selection = if (scoped) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.MediaColumns.SIZE} > 0 AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ? AND ${MediaStore.MediaColumns.SIZE} > 0"
        }
        val path = if (scoped) {
            "${Environment.DIRECTORY_MUSIC}/$FOLDER_NAME/%"
        } else {
            "%${File.separator}${Environment.DIRECTORY_MUSIC}${File.separator}$FOLDER_NAME${File.separator}%"
        }
        return runCatching {
            resolver.query(collection, projection, selection, arrayOf(path), null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val fileNameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val savedAtIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(fileNameIndex).orEmpty()
                        if (name.isBlank()) continue
                        val mediaTitle = cursor.getString(titleIndex)
                            ?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                            ?: name.substringBeforeLast('.').ifBlank { "Levyra Track" }
                        val mediaId = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(collection, mediaId)
                        val volume = contentUri.pathSegments.firstOrNull().orEmpty()
                        val trackId = if (mediaId > 0L && volume.isNotBlank()) {
                            LOCAL_MEDIA_TRACK_ID_PREFIX + localIdentityKey(volume, mediaId)
                        } else {
                            ""
                        }
                        add(
                            DownloadEntity(
                                trackId = trackId,
                                title = mediaTitle,
                                artist = cursor.getString(artistIndex).orEmpty()
                                    .takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                                album = cursor.getString(albumIndex).orEmpty()
                                    .takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty(),
                                durationMs = cursor.getLong(durationIndex).coerceAtLeast(0L),
                                fileName = name,
                                uri = contentUri.toString(),
                                mimeType = cursor.getString(mimeIndex).orEmpty(),
                                embeddedMetadata = false,
                                downloadPreset = "",
                                downloadQuality = "",
                                savedAt = (cursor.getLong(savedAtIndex) * 1_000L).coerceAtLeast(0L)
                            )
                        )
                    }
                }
            }.orEmpty()
        }.onFailure { error ->
            Timber.w(error, "Download folder scan failed")
        }.getOrDefault(emptyList())
    }

    fun uriReadable(context: Context, rawUri: String): Boolean {
        if (rawUri.isBlank()) return false
        return runCatching {
            val uri = Uri.parse(rawUri)
            when (uri.scheme?.lowercase()) {
                "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")
                    ?.use { it.length != 0L } ?: false
                "file" -> uri.path?.let(::File)?.let { it.isFile && it.length() > 0L } == true
                else -> File(rawUri).let { it.isFile && it.length() > 0L }
            }
        }.getOrDefault(false)
    }
}
