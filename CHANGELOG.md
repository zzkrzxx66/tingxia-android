# Changelog

## 0.8.0

- Rebuild the interface on a real corner ladder (6/10/14/20/28) so badges,
  buttons, cards, and panels finally read as a hierarchy instead of sharing one
  8dp radius, and replace five hand-rolled card containers with a single one.
- Render book artwork at its authored 3:4 instead of cropping it square, which
  had been cutting the tops off covers.
- Add opt-in Material You wallpaper colour (Android 12+, falls back to the
  forest palette), switchable under Settings › 外观.
- Rework the shelf into a 3-up portrait grid with progress on the artwork, and
  make the idle tab visible again instead of matching its own track.
- Rework online search: portrait result cards with aligned rows, a single
  actionable magnifier, and an empty state that leads with a hero and a
  popular-search tile grid rather than half a screen of white space.
- Float the mini player on a rounded, shadowed surface.

## 0.5.0

- Redesign the shelf, book detail, full player, mini player, and settings around
  a quieter local-audiobook interface with clearer visual hierarchy.
- Introduce a neutral light/dark theme with forest controls, restrained copper
  accents, compact shapes, and consistent typography.
- Make search permanently available, surface active filters and sorting, and
  improve book, progress, chapter, and bookmark scanning at a glance.
- Refine playback controls for one-handed use and organize book chapters,
  bookmarks, settings, and feedback into clearer interaction patterns.

## 0.4.5

- Fix live position mapping when a book's intro/outro skip values change: the
  player now converts the current position through source-file coordinates
  instead of reusing the old clip-relative position, so adjusting skip settings
  no longer jumps or rewinds the chapter unexpectedly.
- Keep at least one playable second when a grown outro would otherwise land the
  listener on the clip end and instantly finish the chapter.
- Show an active skip summary in the full player so configured intro/outro
  skipping is visible while listening.
- Lock the skip pipeline in with real-player coverage: MediaItem clipping
  configuration, the controller-to-session bundle round-trip, and audible
  start/end boundaries played through ExoPlayer against a generated audio file.

## 0.4.4

- Rework the playback widget around a larger cover and clearer information,
  progress, and transport-control hierarchy for two-row launcher placements.
- Add an adaptive compact layout for one-row placements and switch layouts as
  the widget is resized.
- Align light and dark widget palettes with the app and replace the busy cover
  placeholder with a restrained book mark.

## 0.4.3

- Redesign the home-screen widget with real book artwork, stronger visual
  hierarchy, custom media controls, and refined light and dark palettes.
- Decode widget artwork off the main thread with bounded memory and bitmap
  caching, preserving playback responsiveness for large cover images.

## 0.4.2

- Add a resizable home-screen playback widget with current book, chapter,
  chapter progress, and previous/play-pause/next controls.
- Keep the widget synchronized with Media3 playback and cache the latest state
  so it remains useful when the app UI is closed.
- Resume the most recent book directly from the widget when no active queue is
  loaded.

## 0.4.1

- Add per-book intro and outro skipping for every chapter, configurable from
  the book detail menu from 0 to 300 seconds.
- Apply updated skip settings to the active queue immediately while preserving
  the current chapter, playback position, speed, and play/pause state.
- Preserve skip settings in backups and add a compatible Room migration for
  existing libraries.

## 0.4.0

- Add versioned JSON backup and restore for books, chapter metadata, progress,
  completion state, bookmarks, and settings.
- Add multi-select audio import through the system document picker without
  copying source audio into app storage.
- Keep restored books available for later SAF reauthorization when the source
  folder is not currently accessible.

## 0.3.1

- Restore the latest book queue, chapter, position, speed, and autoplay behavior
  when Android requests playback resumption after process recreation.
- Add stop or auto-skip handling for damaged chapters. Permission failures always
  stop playback and request folder reauthorization.
- Persist progress every five seconds and on seeks, and remove blocking database
  work from playback service teardown.
- Add bookmark note editing and custom book cover selection/removal.
- Add a tag-driven workflow for signed APK/AAB GitHub Releases.

## 0.3.0

- Add hardened SAF rescans and reauthorization, chapter completion management,
  per-book playback settings, sleep timers, search, sorting, and theme selection.
