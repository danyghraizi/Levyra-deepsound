package com.luc4n3x.levyra.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local media artwork is published as a MediaStore content URI. Media3's default loader opens that
 * URI as an image stream, which fails for an audio file, so notification and system artwork fell
 * back to repeated failed decodes. Thumbnails from the media provider decode correctly.
 */
internal class LevyraBitmapLoader(context: Context) : BitmapLoader {
    private val appContext = context.applicationContext
    private val delegate = DataSourceBitmapLoader(appContext)

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (isMediaStoreUri(uri)) loadMediaThumbnail(uri) else delegate.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        val artworkUri = metadata.artworkUri
        if (artworkUri != null && isMediaStoreUri(artworkUri)) return loadMediaThumbnail(artworkUri)
        return delegate.loadBitmapFromMetadata(metadata)
            ?: Futures.immediateFailedFuture(IllegalArgumentException("No artwork"))
    }

    private fun loadMediaThumbnail(uri: Uri): ListenableFuture<Bitmap> = Futures.submit(
        Callable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    appContext.contentResolver.loadThumbnail(
                        uri,
                        Size(THUMBNAIL_SIZE_PX, THUMBNAIL_SIZE_PX),
                        null
                    )
                }.getOrElse {
                    delegate.loadBitmap(uri).get(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            } else {
                delegate.loadBitmap(uri).get(DECODE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
        },
        executor
    )

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.scheme.equals("content", ignoreCase = true) &&
            uri.authority.equals("media", ignoreCase = true)

    private companion object {
        const val THUMBNAIL_SIZE_PX = 512
        const val DECODE_TIMEOUT_SECONDS = 10L
        val executor: ListeningExecutorService = MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(2) { runnable ->
                Thread(runnable, "levyra-artwork").apply { isDaemon = true }
            }
        )
    }
}
