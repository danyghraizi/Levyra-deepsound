package com.luc4n3x.levyra.player

import com.luc4n3x.levyra.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalQueueTrackResolverTest {
    @Test
    fun rebuildsMediaStoreUriFromPersistedIdentity() {
        val track = localTrack("local:ms:external_primary:1000273412")

        assertEquals(
            "content://media/external_primary/audio/media/1000273412",
            resolveLocalQueueMediaUri(track)
        )
    }

    @Test
    fun keepsExistingLocalStreamUri() {
        val track = localTrack("local:ms:external_primary:42")
            .copy(streamUrl = "content://media/external_primary/audio/media/42")

        assertEquals("content://media/external_primary/audio/media/42", resolveLocalQueueMediaUri(track))
    }

    @Test
    fun ignoresRemoteAndMalformedIdentityTracks() {
        assertNull(resolveLocalQueueMediaUri(localTrack("audio123456").copy(source = "YouTube Music")))
        assertNull(resolveLocalQueueMediaUri(localTrack("local:ms:external_primary:notanumber")))
        assertNull(resolveLocalQueueMediaUri(localTrack("local:ms:external_primary")))
    }

    private fun localTrack(id: String): Track = Track(
        id = id,
        title = "Drop in the Ocean",
        artist = "Ron Pope",
        album = "Lost & Found",
        durationMs = 250_394L,
        streamUrl = "",
        videoUrl = "",
        thumbnailUrl = "",
        largeThumbnailUrl = "",
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
