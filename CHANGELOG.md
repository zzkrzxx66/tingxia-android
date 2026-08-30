# Changelog

## 0.16.0

- **AI 朗读（TTS）全库听书**。以前只有存在真人演播版的书能导入，搜到没有真人版的书就是死路。
  - 服务端一直返回着 `tts_tones`（多角色对话、成熟大叔音…），只是没人读。现在选版本页分两组：
    真人演播版本与 AI 朗读音色，上游推荐的音色带星标。
  - 堵点不在接口而在容器：TTS 流是 Opus in MP4，MP4 无法携带 Opus，旧的 `-c copy → .m4a`
    必然失败（`Could not find tag for codec opus`）。流服务现在先 ffprobe 判编码，Opus 走
    `-f ogg`（仍然 `-c copy`，不重编码，12 分钟的章节约 3 秒完成），AAC 保持 `.m4a`。
  - 同一本书可以换音色：TTS 的章节 item id 来自小说目录，与音色无关，所以进度、书签、
    章节标题全部保留，只有已缓存的录音需重新下载。缓存键因此加上了音色，而真人版（tone 0）
    保留旧键形状，已下载的章节不会被作废。
- **追更**。导入后目录一直是静态的，连载书更新了看不到。
  - 书籍详情菜单新增“检查更新”，并在打开应用时每 6 小时自动扫一次未完结的在线书（可在设置关闭），
    有新章发通知。
  - 只追加新的 item id，现有章节行一律不动，所以章节 id、进度、书签、自定义标题都不会被冲掉。
  - 新章带“n 章未读”徽标，打开章节列表即消。已完结的书不再参与自动检查。
- **时长与目录信息不再靠听完才知道**。以前在线书导入后总时长为空，统计页和“继续收听”卡都无数
  可用；现在导入时就写入全书总时长、章节数、评分、收听人数与完结状态（新接口 `/audio/meta`），
  缓存单章时也顺手回填章节时长（`/audio/duration`，不需下载音频）。
- **预热下一章**。流服务是整章下完 + ffmpeg 才吐第一个字节，冷章节开头要干等。现在播放当前章时
  对后两章发 `/audio/warm`，把那份开销挪到上一章的播放时间里；批量缓存也先预热再下载。
- **边听边看正文**。`/chapter` 接口一直存在但从未使用。播放器右上角新增正文入口，书籍详情
  菜单也能打开当前章，字号可调。真人有声的 item id 与小说不同，所以按章号对齐（复用 0.13.1
  的匹配算法），对齐不上时宁可提示缺失也不上错章。
- **搜索与发现**。以前只有第一页结果和一个固定热门列表。
  - 搜索支持翻页（带 `searchId`，去重后全重复的一页即视为到底）、保留搜索历史。
  - 发现页改为 8 个分区横向列表（热门有声剧/玄幻仙侠/都市/悬疑/历史/科幻/言情/评书），
    每个分区“更多”直接转成搜索。
  - 搜索结果直接标注“真人有声 / AI 朗读”、评分、收听人数和连载状态，不必点进去才发现
    没有真人版。

### 服务端（fqnovel + fqnovel-stream）

- 新接口：`/audio/voices/{bookId}`、`/audio/meta/{audioBookId}`、
  `/audio/duration/{audioBookId}/{itemId}`、`/discover/sections`、`/audio/warm/...`、`/healthz`。
- 搜索结果新增 `hasRealAudio` / `audioBookIds` / `ttsEnabled` / `score` / `listenCount` /
  `finished` / `audioCoverUrl`。
- 流服务缓存从“无上限不淘汰”改为 20 GiB LRU（按最后访问时间），并在 `/healthz`
  暴露缓存大小、命中率与预热中的章节数。
- 可选令牌校验（`FQ_API_TOKEN` / `STREAM_API_TOKEN`，默认关闭）。它不是为了保密，
  而是因为 `/audio/play` 每次调用都消耗上游风控额度，被外部扫到会把设备注册刷废。
  客户端用 `-PfqApiToken=...` 构建时带上。

## 0.15.1

- New launcher icon. The old one was a blue-violet box with a sound wave — a default-template
  look that shared nothing with the app's forest-and-paper palette.
  - The mark is the 匣 as an object: a paper-white box holding three level bars, on a forest
    gradient. One filled shape and two colours, which is what every audiobook icon that stays
    readable on a 48px home screen comes down to; the previous mark was thin line art with three
    competing elements and turned to mush at that size.
  - The bars are holes in the box (evenOdd), not a painted layer, so they carry the background
    gradient and cannot drift out of register at any scale.
  - Geometry sits inside the 66dp safe circle, so no launcher mask — squircle, circle or square —
    can crop it.
  - A real monochrome layer for Android 13+ themed icons. The old adaptive icon pointed its
    monochrome slot at the full-colour foreground, so themed mode rendered a solid blob with the
    pattern gone.

## 0.15.0

- Streaming playback feedback, rebuilt. The player only knew whether it was playing, so a first
  load or a mid-stream stall showed nothing at all and a 500 ms progress poll stepped the bar
  visibly behind the audio — a slow chapter looked like a frozen screen.
  - Two new states: 正在载入 appears the instant a chapter is requested, so the tap is never
    silent, and 缓冲中 only appears after a stall lasts longer than 350 ms, so short network
    hiccups no longer flash a spinner. Pausing during a stall drops the indicator; the buffer
    refilling in the background is not the listener's problem.
  - The scrub bar is drawn rather than a disabled Slider: it now carries the buffered head as a
    second track, and while stalled a highlight travels over the part that is not playable yet.
    Before the duration is known the bar is indeterminate and the timestamps read `--:--`
    instead of a confident 0:00.
  - The playhead is interpolated between polls, so the bar and the mini-player ring move
    continuously; seeks and chapter changes snap instead of sliding across the track.
  - The 76dp transport button gains a loading ring, its glyph cross-fades between play and pause
    and dips on press. The cover keeps breathing while loading, chapter titles cross-fade, the
    mini player's ring spins while loading with 缓冲中 on its second line, and the chapter picker
    spins on the row it is loading.
- Buffering policy for speech streaming: 60s–180s buffer, 1.2s to start a chapter, 2.5s after a
  rebuffer, and a 60s back buffer so a 30-second rewind replays from memory instead of
  refetching. Audio-only playlists make the extra memory cheap, and it is what carries a chapter
  through a tunnel.

## 0.14.0

- Home-screen playback widget, rebuilt:
  - Cover and panel are one object: the cover is a tile beside the panel at its full height, rounded
    on the outer edge and square where the two meet, with the panel running 2dp underneath so no
    hairline can open at the seam.
  - The panel takes its colour from the cover — the artwork's average, chroma pulled halfway to grey
    and luminance forced to one dark level, poured into an alpha gradient with a hairline rim. White
    text stays readable whether the cover is a woodcut or a pastel photograph. Android 12+; below
    that a neutral gradient of the same shape.
  - The strip carries 书名 · 章节 with elapsed/total on the same baseline, three centred controls at
    fixed sizes, and the progress hairline along the bottom. The tall size adds the chapter line with
    its 第 x/y 章 count.
  - Both sizes fill the cell they are given. `OPTION_APPWIDGET_MIN_HEIGHT` is the bottom of the resize
    range, not the box on screen — reading it left the widget short of its cell and the cover cropped
    or letterboxed; portrait now reads `MAX_HEIGHT`, and the cover bitmap is rendered for that box.
  - Covers load through Coil, so online books (https artwork) get their real cover instead of the
    generated placeholder, and the placeholder itself matches the in-app fallback.

- Visual pass over the shelf and shared surfaces:
  - Softer corner ladder (cards 16dp, sheets/buttons 20–28dp, artwork 8–18dp) replaces the
    near-rectangular print look, and cards lost their hairline outline in favour of a soft
    shadow — at the new radii the outline made every card read as a form field.
  - Shelf tiles are larger (adaptive 108dp instead of 88dp) and carry a play button in the
    bottom-right corner: one tap resumes that book, the rest of the tile still opens the book
    page. The progress line moved beside it so the two never overlap.
  - New 继续收听 hero card at the top of the shelf — last book, percent listened, time left and a
    big resume button. It scrolls away with the grid, as does the filter/sort row.
  - Mini player is a floating capsule instead of a full-width bar, with chapter progress drawn
    as a ring around the play button rather than a hairline across the top.
  - The online page shows shimmering placeholder tiles while the hot list is still loading.

## 0.13.1

- Chapter-title alignment for online metadata sync no longer assumes both sides start at the same
  place. A 片头 file, a missing 序章 or a folder that begins at chapter 30 used to shift every
  title by a fixed amount.
  - Default is now number matching: the chapter number is parsed from both the local filename
    /title and the online title (Arabic, full-width and Chinese numerals, `第412章`, `EP412`,
    `001_第412章`), then paired by number. Gaps, extra intro files, mid-book folders and 上/下
    splits all fall out of this for free; chapters whose number cannot be read keep their titles.
  - Manual 漂移 mode is the fallback, seeded with the drift the number matches imply and adjusted
    in ±1 / ±10 steps.
  - Both modes show 已匹配 x / y 章 plus a live preview of the first rows before anything is
    written, and 只同步书籍信息 remains one tap away.

## 0.13.0

- Chapter picker in the player (选集 x/N in the tool row): a bottom sheet that switches chapter
  without leaving playback and without rebuilding the queue, so the play/pause state survives
  the tap. Previously the only way to change chapter mid-listen was to back out to the book page.
  - Header stays put while the list scrolls: total / filtered counts, cached counter for online
    books, search by title or chapter number, ascending↔descending, 未听完 / 已缓存 filters,
    and one-tap 定位到在听.
  - Every chapter is its own lazy item with sticky 第 x–y 章 headers, so a 1000-chapter book only
    composes what is on screen and jumping to the current chapter no longer relies on an
    estimated row height.
  - Long-press starts multi-select: batch cache (one foreground job and one notification for the
    whole selection), clear cache, mark listened / clear listened.
  - Chapter rows moved to a shared component and gained a real third state — started-but-
    unfinished chapters no longer look identical to never-played ones — plus a progress bar on
    the chapter that is actually loaded in the player.
- Book-detail chapter list rebuilt on the same components as the picker:
  - The tab row and the chapter toolbar are pinned (sticky), so search, order, filter, 定位在听
    and the 选集 block strip stay reachable 300 rows down instead of scrolling out of view.
  - One lazy item per chapter with sticky 第 x–y 章 headers replaces the 100-rows-per-card
    layout that composed a whole block at once, and the jump-to-current-chapter scroll now
    addresses an exact index instead of multiplying an assumed 64dp row height.
  - Long-press starts multi-select here too, with the same batch actions plus 编辑章节标题 when a
    single chapter is selected; back exits the selection instead of leaving the screen. The old
    per-row long-press menu is gone, so both lists now behave the same way.

- Online metadata sync for imported local audiobooks (书籍详情 › 同步在线信息): search the
  fqnovel catalogue, pick the matching entry, and its blurb, author, category, word count,
  cover and chapter titles are written onto the local book. Nothing is applied until a
  candidate is confirmed, and audio files are never touched.
  - Chapter titles land in `customTitle`, so the scanned filenames survive underneath.
    A chapter-count mismatch asks whether to align the leading chapters or sync book fields
    only, instead of silently misaligning a shorter or longer online table of contents.
  - 清除在线信息 restores the pre-sync author, cover and chapter titles from a snapshot kept
    in Room v10 (`books.metaSyncSourceId` / `metaSyncedAt` / `metaSyncBackup`). A cover the
    user picked after syncing is kept rather than rolled back. Backups do not carry the
    snapshot, so a restored book keeps the synced values as plain metadata.

## 0.12.0

- Chapter-level cache control in the chapter list: every online-book row gets a
  download icon — tap to cache that chapter, spinner while it downloads, tap a
  cached chapter to evict it. The long-press menu gains 缓存本章 / 清除本章缓存.
  Cache state is persisted in the database (v9) so the list reflects reality.
- Whole-book / next-20 prefetch stays in the book-detail menu, now marking
  chapters as cached as each one lands; playing a book also backfills the
  flags from whatever already sits in the on-disk cache.
- Fix: mini player capsule lost its cover when the book used a local cover
  file — artwork file: URIs are now restored to plain filesystem paths before
  they reach the UI state.

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
