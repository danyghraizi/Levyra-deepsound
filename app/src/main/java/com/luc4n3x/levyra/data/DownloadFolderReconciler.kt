package com.luc4n3x.levyra.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import com.luc4n3x.levyra.data.local.DownloadEntity
import com.luc4n3x.levyra.data.locallibrary.localArtworkModel
import com.luc4n3x.levyra.domain.Track
import com.luc4n3x.levyra.data.locallibrary.LocalMediaStoreScanner
import com.luc4n3x.levyra.data.local.LevyraDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import timber.log.Timber

/**
 * Imports audio files that already exist in the Levyra download folder into the downloads
 * database, so the Offline section lists music transferred to the device outside the app.
 *
 * Files copied by the user are not owned by the app, so media read permission is required;
 * without it the reconciliation is skipped silently.
 */
object DownloadFolderReconciler {
    private const val MIN_INTERVAL_MS = 60_000L
    private const val IMPORT_CHUNK = 150
    private const val FAVORITES_SEEDED_KEY = "favorites_seeded_v1"
    private const val LOG_TAG = "LevyraOfflineImport"
    const val OFFLINE_ART_DIR = "offline_art"
    private val mutex = Mutex()
    private var lastRunAt = 0L

    fun offlineArtFile(context: Context, trackId: String): File? {
        if (!trackId.startsWith("offline-")) return null
        val dir = File(context.applicationContext.filesDir, OFFLINE_ART_DIR)
        val file = File(dir, trackId.removePrefix("offline-") + ".img")
        return file.takeIf { it.isFile && it.length() > 512L }
    }

    suspend fun reconcile(context: Context, force: Boolean = false): Int = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force && now - lastRunAt < MIN_INTERVAL_MS) return 0
        val appContext = context.applicationContext
        if (!LocalMediaStoreScanner(appContext).hasPermission()) return 0

        val dao = LevyraDatabase.get(appContext).downloadedTracksDao()
        val scanned = withContext(Dispatchers.IO) { DownloadFolderScanner.scan(appContext) }
        if (scanned.isEmpty()) {
            lastRunAt = now
            return 0
        }
        val scannedUris = scanned.mapTo(hashSetOf()) { it.uri }
        val existing = withContext(Dispatchers.IO) { dao.all() }

        // Rows imported before tag/artwork support are re-created once with full metadata.
        val stale = existing.filter {
            it.trackId.isBlank() && it.uri in scannedUris
        }
        stale.forEach { row ->
            withContext(Dispatchers.IO) { dao.deleteById(row.id) }
        }
        val keptUris = existing.asSequence()
            .filterNot { it in stale }
            .mapTo(hashSetOf()) { it.uri }

        val missing = scanned.filter { it.uri !in keptUris }
        val prepared = coroutineScope {
            val gate = Semaphore(4)
            missing.map { entity ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        try {
                            enrich(appContext, entity)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Timber.w(error, "Metadata read failed for %s", entity.fileName)
                            entity to null
                        }
                    }
                }
            }.awaitAll()
        }
        // Insert in chunks: each insert wakes the downloads flow, which re-maps the whole list.
        prepared.chunked(IMPORT_CHUNK).forEach { chunk ->
            val entities = chunk.map { it.first }
            val ids = runCatching {
                withContext(Dispatchers.IO) { dao.insertAll(entities) }
            }.getOrElse { emptyList() }
            ids.forEachIndexed { index, id ->
                val artwork = chunk.getOrNull(index)?.second ?: return@forEachIndexed
                withContext(Dispatchers.IO) { saveArtwork(appContext, id, artwork) }
            }
        }
        lastRunAt = now
        if (missing.isNotEmpty()) {
            Timber.i("Imported %d existing download files into the Offline section", missing.size)
        }
        Log.i(LOG_TAG, "reconcile: imported ${missing.size}, scanned ${scanned.size}")
        seedFavoritesOnce(appContext, dao.all())
        missing.size
    }

    /** One-time convenience: put every imported file into Favorites. */
    private suspend fun seedFavoritesOnce(context: Context, rows: List<DownloadEntity>) {
        val prefs = context.getSharedPreferences("levyra_offline_import", Context.MODE_PRIVATE)
        if (prefs.getBoolean(FAVORITES_SEEDED_KEY, false)) return
        val tracks = rows
            .filter { it.trackId.isNotBlank() }
            .map { row ->
                val artwork = row.uri
                    .takeIf { it.startsWith("content://", ignoreCase = true) }
                    ?.let { localArtworkModel(it, 0L) }
                    .orEmpty()
                Track(
                    id = row.trackId,
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationMs = row.durationMs,
                    streamUrl = "",
                    videoUrl = "",
                    thumbnailUrl = artwork,
                    largeThumbnailUrl = artwork,
                    source = "Offline",
                    moodTags = emptySet(),
                    energy = 0,
                    vocal = 0,
                    replayScore = 0,
                    cacheScore = 0,
                    accentStart = 0,
                    accentEnd = 0
                )
            }
        if (tracks.isEmpty()) return
        val result = runCatching { FavoritesStore(context).addFavorites(tracks) }
            .onFailure { Timber.w(it, "Seeding favorites failed") }
        Log.i(LOG_TAG, "seeded favorites: ${tracks.size} tracks, total ${result.getOrNull()?.size ?: -1}")
        prefs.edit().putBoolean(FAVORITES_SEEDED_KEY, true).apply()
    }

    private fun enrich(context: Context, entity: DownloadEntity): Pair<DownloadEntity, ByteArray?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(entity.uri))
            fun value(key: Int): String = retriever.extractMetadata(key)
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "Unknown" }
                .orEmpty()
            val title = value(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { entity.title }
            val artist = value(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { entity.artist }
            val album = value(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { entity.album }
            val duration = value(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull()
                ?.takeIf { it > 0L }
                ?: entity.durationMs
            val artwork = extractArtwork(context, Uri.parse(entity.uri))
                ?: runCatching { retriever.embeddedPicture }.getOrNull()
            entity.copy(
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                embeddedMetadata = true
            ) to artwork
        } catch (error: Exception) {
            Timber.d(error, "Metadata read failed for %s", entity.fileName)
            entity to null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractArtwork(context: Context, uri: Uri): ByteArray? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bitmap = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            }.getOrNull()
            if (bitmap != null) {
                val bytes = runCatching {
                    ByteArrayOutputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                        output.toByteArray()
                    }
                }.getOrNull()
                bitmap.recycle()
                if (bytes != null && bytes.size > 512) return bytes
            }
        }
        return null
    }

    private fun saveArtwork(context: Context, downloadId: Long, artwork: ByteArray) {
        if (artwork.size < 512) return
        val dir = File(context.filesDir, OFFLINE_ART_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) return
        File(dir, "$downloadId.img").writeBytes(artwork)
    }
}
