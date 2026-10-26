package com.luc4n3x.levyra.data

import com.luc4n3x.levyra.data.local.DownloadEntity
import com.luc4n3x.levyra.data.local.favoriteDownloadMatches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadMatchingTest {
    @Test
    fun matchesByTrackIdEvenWhenTitlesDiffer() {
        val download = download(trackId = "yt:abc", title = "Different title", artist = "A")

        assertTrue(favoriteDownloadMatches("yt:abc", "Some title", "B", download))
    }

    @Test
    fun matchesByNormalizedTitleAndArtist() {
        val download = download(trackId = "local:1", title = "Qalbe  Meshtaq!", artist = "Wael Kfoury")

        assertTrue(favoriteDownloadMatches("yt:other", "qalbe meshtaq", "wael kfoury", download))
        assertTrue(favoriteDownloadMatches("yt:other", "Qalbe Meshtaq", "Wael Kfoury & Friends", download))
    }

    @Test
    fun matchesArabicTitlesAndArtists() {
        val download = download(trackId = "local:2", title = "أهلا لبنانيي", artist = "هشام الحاج")

        assertTrue(favoriteDownloadMatches("yt:other", "أهلا  لبنانيي!", "هشام الحاج", download))
    }

    @Test
    fun rejectsSameTitleWithDifferentArtist() {
        val download = download(trackId = "local:1", title = "Hello", artist = "Adele")

        assertFalse(favoriteDownloadMatches("yt:other", "Hello", "Lionel Richie", download))
    }

    @Test
    fun rejectsUnmatchedTrackIdWithoutTitle() {
        val download = download(trackId = "local:1", title = "Hello", artist = "Adele")

        assertFalse(favoriteDownloadMatches("yt:other", "   ", "Adele", download))
        assertFalse(favoriteDownloadMatches("yt:other", "Goodbye", "Adele", download))
    }

    private fun download(trackId: String, title: String, artist: String): DownloadEntity = DownloadEntity(
        trackId = trackId,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 200_000L,
        fileName = "file.opus",
        uri = "content://media/external/audio/media/1",
        mimeType = "audio/opus",
        embeddedMetadata = true,
        downloadPreset = "balanced",
        downloadQuality = "high",
        savedAt = 1L
    )
}
