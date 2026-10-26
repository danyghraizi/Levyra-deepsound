package com.luc4n3x.levyra.data.local

import java.util.Locale

private val DownloadTextSeparator = Regex("[^\\p{L}\\p{N}]+")

internal fun normalizeDownloadText(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace(DownloadTextSeparator, " ")
        .trim()

internal fun favoriteDownloadMatches(
    trackId: String,
    title: String,
    artist: String,
    download: DownloadEntity
): Boolean {
    if (trackId.isNotBlank() && download.trackId == trackId) return true
    val titleKey = normalizeDownloadText(title)
    if (titleKey.isBlank() || normalizeDownloadText(download.title) != titleKey) return false
    val artistKey = normalizeDownloadText(artist)
    if (artistKey.isBlank()) return true
    val downloadArtist = normalizeDownloadText(download.artist)
    return downloadArtist.isNotBlank() &&
        (downloadArtist.contains(artistKey) || artistKey.contains(downloadArtist))
}
