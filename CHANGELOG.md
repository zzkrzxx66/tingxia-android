# Changelog

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
