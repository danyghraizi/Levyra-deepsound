package com.luc4n3x.levyra.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LevyraPersonalOrbitRankingTest {
    @Test
    fun ranksCandidatesBySharedArtistBeforeUnrelatedOnes() {
        val favorite = track(id = "favorite001", title = "Seed", artist = "Tash Sultana")
        val sharedArtist = track(id = "candidate01", title = "Jungle", artist = "Tash Sultana")
        val unrelated = track(id = "candidate02", title = "Outro", artist = "Stephen")

        val orbit = LevyraPersonalOrbit.build(
            currentTrack = null,
            recentSearches = emptyList(),
            favorites = listOf(favorite),
            tracks = listOf(unrelated, sharedArtist),
            homeSections = emptyList(),
            charts = emptyList(),
            limit = 3,
            languageCode = "en"
        )

        assertEquals(listOf("favorite001", "candidate01", "candidate02"), orbit.map { it.id })
    }

    @Test
    fun ranksCandidatesBySharedMoodTags() {
        val favorite = track(
            id = "favorite002",
            title = "Seed",
            artist = "Seed Artist",
            moodTags = setOf("chill", "night")
        )
        val moodMatch = track(
            id = "candidate03",
            title = "Underwater",
            artist = "Other Artist",
            moodTags = setOf("night")
        )
        val unrelated = track(
            id = "candidate04",
            title = "Loud",
            artist = "Another Artist",
            moodTags = setOf("workout")
        )

        val orbit = LevyraPersonalOrbit.build(
            currentTrack = null,
            recentSearches = emptyList(),
            favorites = listOf(favorite),
            tracks = listOf(unrelated, moodMatch),
            homeSections = emptyList(),
            charts = emptyList(),
            limit = 3,
            languageCode = "en"
        )

        assertEquals(listOf("favorite002", "candidate03", "candidate04"), orbit.map { it.id })
    }

    private fun track(
        id: String,
        title: String,
        artist: String,
        moodTags: Set<String> = emptySet()
    ): Track = Track(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        durationMs = 220_000L,
        streamUrl = "",
        videoUrl = "https://www.youtube.com/watch?v=$id",
        thumbnailUrl = "https://i.scdn.co/image/${id.padEnd(22, '0')}",
        largeThumbnailUrl = "https://i.scdn.co/image/${id.padEnd(22, '0')}",
        source = "YouTube Music",
        moodTags = moodTags,
        energy = 50,
        vocal = 50,
        replayScore = 80,
        cacheScore = 80,
        accentStart = 0,
        accentEnd = 0
    )
}
