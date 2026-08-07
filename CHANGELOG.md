# Changelog

## 0.11.0

- Offline cache for online (fqnovel) audiobooks. Playback now streams through a
  shared ExoPlayer SimpleCache keyed per (audiobook, chapter), so a chapter that
  has played once keeps playing without network; local SAF files bypass the
  cache entirely. LRU eviction caps the cache at 1 GB.
- Prefetch whole books or the next 20 chapters from the book-detail menu. A
  foreground service downloads chapters into the same cache with an ongoing
  progress notification, a cancel action, and per-book status in the menu.
- Settings › 离线缓存 shows current cache usage and a one-tap 清空缓存; the
  book-detail menu also offers 清除本书缓存 for a single title.

## 0.10.0

- M4B embedded-chapter parsing: single-file audiobooks expand into real chapters
  during import and rescan. Chapter titles come from the file's own markers;
  playback uses Media3 clipping composed with per-book intro/outro skipping.
  Parsing runs through a pinned, checksum-verified static ffprobe binary
  (downloaded once, cached) and degrades silently to whole-file chapters when a
  file has no markers or the binary is unavailable.
- Listening statistics (设置 › 听书统计): real listening time is recorded on the
  playback ticker and split across local calendar days — total / last-7-days /
  today, a weekly bar chart, finished-book count, and a top-books ranking that
  links back to each book. Deleting a book also removes its stats.
- The full player now shows time remaining at the current speed: chapter
  remaining by default, tap to switch to whole-book remaining.
- Precision scrubbing: while dragging the seek bar, pull down to drop into
  quarter-sensitivity fine mode (re-anchored so the thumb doesn't jump), with
  a hint label and haptic feedback on mode change.
- Room v8 adds chapter clip windows and the listen_sessions table; backups carry
  clip windows so restored m4b books keep their chapter split.

## 0.9.7

- Fix a 0.9.6 regression: the chapter list again opens at the chapter in
  progress instead of the very bottom (the single-card list defeated the
  initial scroll). Chapters now render as lazy 100-per-card groups.
- Long books gain a 选集 strip under the tabs (1–100, 101–200, …) that
  jumps straight to a chapter block instead of scrolling hundreds of rows;
  it appears only when there is more than one group.

## 0.9.6

- Shelf top bar now lifts to a raised surface while the grid scrolls
  (pinned scroll behaviour), and grid tiles get more breathing room with
  realistic cover shadows no longer clipped by tight cells.
- Chapter and bookmark lists on the book-detail page sit inside a single
  card with consistent inset dividers; the chapter in progress is
  highlighted across the whole row, not just its number tile.
- Shared BookGridTile component keeps shelf and online hot-book grids
  identical in height and rhythm; all empty states (shelf, online search,
  bookmarks) now use one EmptyState look.
- Full player readability: a soft gradient darkens behind the controls on
  bright covers, timestamps gain a subtle text shadow, and the cover
  progress bar track keeps contrast on dark artwork.
- Scrubbing the seek bar shows a floating time bubble tracking the thumb,
  with haptic ticks on drag start/finish; skip buttons shrink and
  ±30s buttons grow so the control hierarchy reads at a glance.
- Active sleep timer swaps its icon to TimerOff and a brighter halo;
  the mini player bar gains a next-chapter button.
- Navigating shelf → book detail slides laterally (push/pop) instead of
  the generic cross-fade, matching the navigation hierarchy.
- Home-screen widget: fallback artwork now renders the same palette-wash +
  book-initial style as the in-app fallback cover, and widget card corners
  match the app's 12dp card radius.

## 0.9.5

- The full player's skip intro/outro button now opens the same settings
  dialog as the book-detail menu instead of sitting disabled; the dialog
  itself is a shared component so both surfaces stay in sync.
- Drop the duplicate book count from the shelf grid header (the app bar
  already shows it) and add a small play badge on the tile currently loaded
  in the player.
- The player now rises from and sinks back into the mini player bar with a
  vertical slide; the mini bar itself slides in/out, gets a filled round
  play toggle echoing the full player's button, and a hairline top divider.
- The 继续播放 button tells you where it will resume (chapter · position),
  and the chapter list opens scrolled to the chapter in progress.
- Cover breathing eases back to rest on pause instead of snapping; active
  player tool buttons get a soft halo that reads on the blurred backdrop.
- Book-detail header grows with long titles (340dp is now a floor, not a
  ceiling); SectionCards gain a hairline outline in light mode; the online
  welcome grid reflows adaptively like the shelf instead of fixed 3-up rows.
- Move all remaining hard-coded UI strings (online catalogue, badges,
  skip-offset toasts) into string resources.

## 0.9.4

- Keep the online book metadata that was fetched but dropped at import:
  blurb, category, and word count now persist on the shelf copy (DB v7),
  ride along in backups, and show up in the app — an expandable 简介
  section, category chip, and word count on the book detail page, plus
  category/word-count metadata on online search cards and the edition
  picker.

## 0.9.3

- Give shelf and detail covers a paperback finish: spine crease with a catch
  light, page edges at the fore-edge, cut-page hairlines at the head, a
  debossed hairline frame, and a warm contact shadow — all theme-aware so the
  effect stays subtle in dark mode.

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
