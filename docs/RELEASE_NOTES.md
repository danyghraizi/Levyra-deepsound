# Levyra 3.0.3

## Highlights

This personal-fork build focuses on how Levyra feels in daily use: faster playback transitions, a widget that can start music without opening the app, reliable artist names, and downloads that follow your likes.

### Smoother playback and navigation

- Starting a track, skipping next/previous and opening the player no longer rebuild the personal orbit on the main thread. On the test device this removed multi-second frame stalls that appeared after every play or skip.
- The player artwork morph no longer recomposes on every animation frame.
- An app-specific Baseline Profile now covers startup, Home, Library, the player and tab switching, removing the JIT compilation that caused first-open hitches.

### Widget

- Tapping the widget body opens the app normally instead of forcing the player tab.
- Play/pause, next and previous work when the app process was closed: the widget keeps its media session connected until playback starts and restores the persisted queue and position.
- Restored local files are re-linked to their MediaStore URI, so a cold widget action can still play them.

### Artist names

- Some streamed tracks arrived with an empty artist because the channel name in the YouTube response was not used. The resolver now fills a blank artist from the resolved video's author, so the player, miniplayer and notification show the artist again.

### Favorites and downloads

- Removing a favorite (the heart) now deletes its downloaded copy from the device, including imported local files matched by identity or normalized title and artist. The file and the download row are removed together.
- Adding a favorite still auto-downloads when that automation is enabled.
- Bulk unfavorite from a selection does not delete files.

### Other fixes

- Session and notification artwork uses the media thumbnail API for local files instead of failing to decode the audio URI.
- Manual skips no longer leave playback paused when the next stream cannot be resolved.
- Download matching now handles non-Latin titles, including Arabic.

## Validation

- Repository fast AI quality gate: passed.
- Targeted unit tests for orbit ranking, local queue URI resolution and download matching: passed.
- Android lint and unsigned F-Droid release compilation: passed during the full gate run.
- The full AI quality gate still reports 50 localization tests that fail on the fork's existing baseline: Hebrew was removed from the language catalog and core strings while feature copy maps still contain Hebrew entries. This predates 3.0.3 and is unrelated to these changes; it is not claimed as passing.
- Device checks on a Samsung SM-G990B (Android 16) covered playback, skips, widget cold start, the player transition, scrolling and tab-switch measurements, the artist fix and favorite-download deletion.
- The APK attached here is signed with the personal debug keystore used by earlier installations of this fork, not the upstream release key. Verify the signature before installing.

## Versioning

- Version name: `3.0.3`
- Version code: `3000300`

Levyra Desktop is versioned and released separately; this update does not change the Windows app.

## Upgrade notes

The package id is unchanged, so this build upgrades the existing installation in place. Library, playlists, favorites, downloads, listening history, settings, skin and player style are preserved. Unliking a song now deletes its downloaded file; re-download it from the song actions if needed.

## Final note

This is a personal-fork release built from the `auto-download-v3.0.2` branch. Catalog, lyrics and streaming behavior still depend on external services and can vary by region.
