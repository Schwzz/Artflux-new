# Artflux repository review

Reviewed baseline: `6bcfd461810fef8c58f96f57d1c63b5a93ce01d7` (2026-10-21).

Artflux already has Compose feeds, source configuration and diagnostics, media playback, themes, saves, and collections. The highest-value first step is making that existing workflow dependable.

## Changes in this pass

| Finding | Change |
| --- | --- |
| Editing the active source returned early because its ID had not changed. | Compare the full configuration so changed URLs, authentication, and field mappings take effect immediately. |
| Typing a new search could change the query used for subsequent pages of existing results. | Keep draft text separate from the submitted query. |
| Responses used filters captured before the network request; later pages were sorted separately. | Reapply current client filters and sort all loaded artwork together. |
| Duplicate items within the same page survived, and an empty filtered first page could strand browsing. | Deduplicate every batch; scan up to three pages automatically and offer a manual continuation. |
| Automatic scrolling could stall when switching tabs or finishing a short page. | Recheck pagination after relevant feed/tab changes and provide a Load more button. |
| Page-load failures had no persistent retry control. Refresh failures hid usable artwork. | Add inline retry, pause automatic retries, preserve existing artwork and pagination on refresh failure. |
| Cancelled coroutines left blocking HTTP work running. | Cancel the actual OkHttp call, including body reads, and propagate coroutine cancellation. |
| Dismissed or superseded diagnostics could publish late results. | Cancel the previous diagnostic job and guard result publication. |
| Encryption silently fell back to a key derived from the app package name. | Require Android Keystore in production; inject random keys explicitly in encryption tests. |
| Failed credential persistence was reported as a successful source save. | Honor persistence failures, retain the existing source, and keep the editor open with an error message. Preserve legacy data if migration fails so it can be retried. |
| Changing video looping recreated a player without disposing the old instance; playback also issued an extra probe request. | Update repeat mode on the existing player, release by player identity, and use Media3's own playback errors. |
| Fresh local builds required a manually generated custom debug keystore; README referenced the old repository. | Use Android's standard debug signing and correct build instructions. |
| CI did not run lint or retain verification reports. | Run lint, upload reports, cancel superseded runs, limit workflow permissions, and require an APK artifact after a successful build. |

## Improvements to tackle next

1. **Prevent saved-artwork ID collisions across sources.** The parser uses each provider's raw numeric ID, and saves/collections use that ID as a global key. Add a stable source ID plus provider post ID, then migrate saved records and collection references together. Include ID-less APIs, whose current index-based fallback IDs can repeat across pages. This needs a reviewed data migration before shipping.
2. **Move saves and collections to Room.** The current JSON SharedPreferences store rewrites and serializes entire lists on the main thread. A database would support atomic collection updates, migrations, large libraries, and responsive search. Preserve existing JSON through a tested import migration.
3. **Make downloads accurate and compatible.** Choose filenames from the selected download URL, preserve actual formats, handle storage permissions on Android 7–9, and replace the misleading Original = 1080p success label. Add download history/status and optional Wi-Fi-only downloads.
4. **Make source failures actionable.** Distinguish rate limits, invalid JSON mappings, expired credentials, and empty results. An object without the configured item array currently looks like a valid empty feed. Add bounded backoff for 429 responses and a clear configuration warning.
5. **Remember browsing preferences.** Persist each tab's selected source, filters, and submitted search; restore scroll state after process death. Show active filter chips with individual removal.
6. **Extend library tools.** Add local search, sorting, bulk collection actions, backup/import, and optional offline artwork. Keep saved metadata separate from downloaded files and show storage usage.
7. **Improve accessibility and maintainability.** Extract user-facing strings for localization, audit contrast/touch targets and TalkBack, and split the large settings/view-model/test files by feature as they change. Keep UI styling consistent with the existing themes.
8. **Tighten transport security deliberately.** Cleartext traffic is globally enabled for custom-source compatibility. Offer HTTPS by default, explain HTTP when configuring a source, and avoid sending credentials over cleartext connections. Review source URLs and diagnostic output for embedded secrets.

## Verification

Regression tests exercise the real view-model paths with controlled responses: source editing, submitted queries, current filters, global sorting, same-page duplicates, refresh failure, explicit retry, filtered-page scanning, bounded duplicate scanning, stale search responses, and dismissed diagnostics. Additional tests cover HTTP-call cancellation, response reading, secret redaction, credential-storage failure, migration retry, encryption round trips, and pagination UI controls.

`git diff --check` passes. Android tests and compilation have **not run**: this environment cannot reach the Gradle distribution server. The initial GitHub write attempt was denied; access was subsequently restored. Android verification is pending the pull-request CI run. A passing CI build does not replace device testing.

Device checks before a release:

- Rapidly switch sources, edit the active source, and change filters while a page loads.
- Type a new draft query, load another page, then submit the draft.
- Lose connectivity during refresh and pagination; verify artwork remains visible and retry works.
- Test empty filtered results and repeated random-source pages.
- Swipe between videos, toggle looping, background the app, and close the viewer; confirm playback stops and no extra player remains.
- Save an authenticated source, restart the app, and confirm authentication survives.
- Verify existing saves and collection membership remain intact.

The larger items above remain recommendations, not completed features in this change.
