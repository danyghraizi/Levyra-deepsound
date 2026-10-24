package com.luc4n3x.levyra.data

import android.content.Context
import com.luc4n3x.levyra.data.collections.isPersonalCollectionTrack
import com.luc4n3x.levyra.data.local.LevyraDatabase
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.player.offline.work.OfflineExportWorker
import timber.log.Timber

/**
 * Queues an offline download for every track that is newly added to the favorites.
 *
 * The download reuses the regular manual download pipeline, so it honours the
 * user's download settings (Wi-Fi/charging constraints, folder layout, tags,
 * artwork). Tracks that are already downloaded, already queued, or that belong
 * to a personal collection (local files, stream-only) are skipped.
 */
object FavoriteAutoDownloader {
    private val ACTIVE_TASK_STATES = setOf("QUEUED", "RUNNING", "RETRYING", "PAUSED")

    suspend fun enqueue(context: Context, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val appContext = context.applicationContext
        val enabled = runCatching {
            LevyraPreferences(appContext).downloadSettings().autoDownloadFavorites
        }.getOrDefault(false)
        if (!enabled) return

        val database = LevyraDatabase.get(appContext)
        val downloaded = runCatching {
            database.downloadedTracksDao().all().mapTo(hashSetOf()) { it.trackId }
        }.getOrDefault(emptySet())
        val tasks = database.offlineDownloadTasksDao()

        tracks.distinctBy { it.id }.forEach { track ->
            if (track.id.isBlank() || track.isPersonalCollectionTrack()) return@forEach
            if (track.id in downloaded) return@forEach
            val existing = runCatching { tasks.byKey(track.id) }.getOrNull()
            if (existing != null && existing.state in ACTIVE_TASK_STATES) return@forEach
            runCatching {
                OfflineExportWorker.enqueue(
                    context = appContext,
                    trackId = track.id,
                    trackPayload = TrackPayloadCodec.encode(track)
                )
            }.onFailure { error ->
                Timber.w(error, "Favorite auto-download failed for %s", track.id)
            }
        }
    }
}
