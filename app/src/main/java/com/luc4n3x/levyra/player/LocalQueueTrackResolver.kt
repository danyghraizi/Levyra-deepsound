package com.luc4n3x.levyra.player

import com.luc4n3x.levyra.data.locallibrary.LOCAL_MEDIA_TRACK_ID_PREFIX
import com.luc4n3x.levyra.domain.Track

/**
 * Persisted queue entries strip stream URLs, so a restored local track only carries its
 * MediaStore identity. Rebuild the playable content URI from that identity.
 */
internal fun resolveLocalQueueMediaUri(track: Track): String? {
    if (!track.source.equals("Offline", ignoreCase = true)) return null
    if (track.streamUrl.isNotBlank()) return track.streamUrl
    if (!track.id.startsWith(LOCAL_MEDIA_TRACK_ID_PREFIX, ignoreCase = true)) return null
    val parts = track.id.removePrefix(LOCAL_MEDIA_TRACK_ID_PREFIX).split(':')
    if (parts.size != 3 || !parts[0].equals("ms", ignoreCase = true)) return null
    val volume = parts[1].takeIf { it.isNotBlank() } ?: return null
    val mediaId = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
    return "content://media/$volume/audio/media/$mediaId"
}
