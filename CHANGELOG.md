# Changelog

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
