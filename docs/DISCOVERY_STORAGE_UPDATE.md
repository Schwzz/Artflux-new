# Artflux 1.2: discovery, storage, and viewer update

Built on the Home/Library update in PR #2. Application ID and existing metadata are preserved.

## Source health

Open Source health from Settings, the source picker, or Home's error notice. Status comes from the last browsing request, manual check, or diagnostic, with a check time and last successful connection time. Checks do not alter the feed. There is no background polling of every source.

HTTP 401, 403, 429, server failures, connectivity failures, and broken response mappings have different recovery messages. HTTP 429 and 503 respect Retry-After (seconds or HTTP date, capped at one day); 429 defaults to a one-minute cooldown. Retry controls and feed/related requests honor that cooldown. Status persistence contains codes/times, not response bodies, API keys, or URLs. Customize/Edit and full Diagnostics remain available.

## Offline storage

Open the storage button in Library or Offline storage in Settings. The manager shows actual app-private download-directory usage, available space, ready/missing copies, and unfinished files. Scan checks local file readability. Offline records persist measured size and missing-file status. Missing copies can be downloaded again.

Select individual records or all visible records, filter missing files, and sort largest first. Removal requires confirmation and affects only selected private downloads. It cancels their unfinished transfers and removes their local files/records; saved metadata and collection membership remain. Public gallery downloads are excluded. Untracked files are counted in directory usage but are not deleted by this screen. The Offline grid includes downloaded artwork even if it has not been separately bookmarked.

## Viewer

The viewer adds accessible previous/next buttons, a page counter, Similar, and zoom reset. Images have animated double-tap zoom, pinch zoom anchored at the gesture center and pan constrained to the fitted image, and tap-to-hide controls. Returning to an image resets its zoom. Error messages explain unavailable files, connection trouble, access restrictions, and rate limits without displaying raw request URLs. An explicit smaller-preview option is offered when available; downloads still use the chosen download quality. Video buffering has an indicator. Link sharing prefers the original post and does not share app-private content URIs as public URLs.

## Related artwork

Tap Similar or a tag in viewer details. Matches use shared tags or matching author metadata from saved/recently loaded artwork. The Artist chip narrows local matches to the same author metadata; numeric uploader IDs are not presented as artist names. A selected tag can also fetch matches from the artwork's original configured source. This is metadata similarity, not visual/AI image matching. No new paid service is required.

Blocked tags and active client filters apply. If the active rating is All, a known seed rating becomes the related search's rating filter. The seed and duplicate keys/URLs are excluded. Missing sources and tagless artwork gracefully fall back to available local matches. Requests are cancelled when the tag changes or the sheet closes; remote exploration is limited to ten pages per tag. Closing a result returns to the related sheet and original viewer. Home's source/query/filter/scroll session is not changed. Up to five nested viewer return points are retained.

## Verification

Regression tests cover HTTP classification/Retry-After, credential-free health records, strict response mapping, storage size/missing-state persistence, private-only removal, related filtering/cancellation/return navigation, cooldown enforcement, and fitted-image zoom bounds. UI tests exercise storage confirmation, source retry state, related selection, viewer paging/current-item actions, and zoom/control toggles at 360dp width. CI exports UI captures, test/lint reports, and a debug APK. Final results are recorded in PR #3.

Real-device checks remain necessary for background DownloadManager behavior, external file deletion, system storage availability, video decoding, and multitouch gesture feel. CI UI captures verify layout/interactions under Robolectric, not a physical device.
