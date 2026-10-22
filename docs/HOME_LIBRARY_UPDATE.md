# Artflux Home & Library update

Built on the verified reliability branch (`2607a21`).

## Included

- Home combines browsing and search. Top-left search expands inline, the source selector sits in the center, and filters are on the right. There is no Search bottom tab.
- Home, Library, and Settings navigation. Library includes Saved, Collections, Offline, and Downloads.
- Exactly three complete themes: Midnight (ink/cyan), Slate (charcoal/lavender), Paper (warm light/graphite). No separate accent picker or mode selector. Existing stored palette IDs migrate to these presets.
- New vector A/artwork launcher icon, with adaptive and monochrome variants.
- Removable active-filter/query chips, three grid densities, and working thumbnail quality selection.
- Per-source submitted search, filters, and scroll position. Previously loaded feeds are cached while the app is running. After a restart, restoring a deep position loads up to 20 pages and clamps to available results.
- Search history, saved searches, favorite source pins, and tag suggestions based on tags encountered while browsing. Suggestions are local; no remote autocomplete endpoint is claimed for unsupported sources.
- Exact tag blocking, ignoring capitalization, with enable/disable/remove controls in Settings. Long-press a tag in viewer details to block it. Blocked artwork is hidden from feeds and Library; saved data remains intact.
- Library search by title, author, source, or tags. Bulk add to collection, gallery download, offline save, and confirmed unsave. Collection creation, rename, deletion, and offline collection downloads.
- Durable transfer tracking through Android DownloadManager. Queue survives app process death; Downloads shows progress, failures, retry, cancellation, and removal of offline files. Public download records can be cleared without deleting completed gallery files.
- Offline originals stored in app-specific external storage. Images/GIFs and video playback use downloaded content URIs; video posters may be unavailable offline. Offline files are removed when the app is uninstalled.
- JSON backup and merge restore of saved metadata, collections, browsing preferences, and source configurations using Android's file picker. Credential fields and known credential URL parameters are excluded. Offline media files and transfer records are device-specific and excluded. Imported authenticated sources need their keys re-entered. Restore validates the version, structure, limits, and URLs before offering confirmation.
- Source-aware library keys, with migration of saved entries and collection references. ID-less providers use a URL-based hash instead of repeating page indexes.
- Android 7–9 gallery downloads request storage permission; app-private offline copies need none. The application ID remains unchanged, with version 1.1/code 2.

## Verification

Regression coverage includes blocking, persistence, suggestions, same-ID artworks from different sources, legacy collection migration, secret-free backup round trips, invalid backups, ID-less provider stability, durable download deduplication, and Home search integration. CI also runs the existing tests, Android lint, and APK assembly. Final results are recorded in the pull request.

Device verification remains necessary for gestures, visual spacing, system file pickers, storage permissions, background transfers, and offline image/video playback. Debug builds from different ephemeral signing keys may not install over one another; preserve/export your library before changing installations.
