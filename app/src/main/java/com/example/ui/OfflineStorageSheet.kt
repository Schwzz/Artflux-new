package com.example.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ArtworkDownload
import com.example.data.OfflineStorageSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineStorageSheet(downloads: List<ArtworkDownload>, storage: OfflineStorageSummary, onScan: () -> Unit,
    onRemove: (Set<Long>) -> Unit, onRetry: (ArtworkDownload) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    fun bytes(value: Long) = Formatter.formatShortFileSize(context, value.coerceAtLeast(0))
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var missingOnly by remember { mutableStateOf(false) }
    var largestFirst by remember { mutableStateOf(true) }
    var pendingRemoval by remember { mutableStateOf(emptyList<ArtworkDownload>()) }
    val offline = downloads.filter { it.offline }
    val visible = offline.filter { !missingOnly || it.fileMissing }.let { if (largestFirst) it.sortedByDescending { r -> r.storedBytes } else it }
    val selected = offline.filter { it.downloadId in selection }
    LaunchedEffect(Unit) { onScan() }
    LaunchedEffect(downloads) { selection = selection.intersect(offline.map { it.downloadId }.toSet()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("offline_storage_sheet"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Offline storage", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onScan, Modifier.testTag("scan_offline_storage")) { Icon(Icons.Default.Refresh, "Scan offline files") }
                }
                Text(if (storage.available) "${bytes(storage.usedBytes)} used · ${bytes(storage.freeBytes)} free" else "Device storage is unavailable", style = MaterialTheme.typography.titleSmall)
                Text("${offline.count { it.complete }} ready · ${offline.count { it.fileMissing }} missing · ${storage.fileCount} files including unfinished transfers", style = MaterialTheme.typography.bodySmall)
                Text("Removing a copy keeps saved artwork and collections. Gallery downloads are managed separately.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(missingOnly, { missingOnly = !missingOnly; selection = emptySet() }, label = { Text("Missing only") })
                    FilterChip(largestFirst, { largestFirst = !largestFirst }, label = { Text("Largest first") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ selection = if (visible.isNotEmpty() && visible.all { it.downloadId in selection }) emptySet() else visible.map { it.downloadId }.toSet() }) { Text("Select all") }
                    TextButton({ pendingRemoval = selected }, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("remove_selected_offline")) { Text("Remove (${selected.size})") }
                }
                if (selected.isNotEmpty()) Text("Selected: ${bytes(selected.sumOf { it.storedBytes })}", style = MaterialTheme.typography.labelMedium)
                if (visible.isEmpty()) Text(if (missingOnly) "No missing files found." else "No offline copies yet. Save artwork offline from Library.", Modifier.padding(vertical = 20.dp))
            }
            items(visible, key = { it.downloadId }) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(record.downloadId in selection, { checked -> selection = if (checked) selection + record.downloadId else selection - record.downloadId },
                            modifier = Modifier.testTag("offline_select_${record.downloadId}"))
                        Column(Modifier.weight(1f).padding(6.dp)) {
                            Text(record.item.title, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                            Text("${record.item.sourceName} · ${record.label}", style = MaterialTheme.typography.bodySmall)
                            Text(if (record.fileMissing) "No readable local file" else bytes(record.storedBytes), style = MaterialTheme.typography.labelSmall)
                            if (record.fileMissing || record.status == android.app.DownloadManager.STATUS_FAILED) TextButton({ onRetry(record) }) { Text("Download again") }
                        }
                    }
                }
            }
            item { TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Done") } }
        }
    }
    if (pendingRemoval.isNotEmpty()) AlertDialog(onDismissRequest = { pendingRemoval = emptyList() },
        title = { Text("Remove ${pendingRemoval.size} offline copies?") },
        text = { Text("Delete selected local files and cancel their unfinished transfers. This can free about ${bytes(pendingRemoval.sumOf { it.storedBytes })}. Saved artwork, collections, and gallery downloads are kept.") },
        confirmButton = { TextButton({ onRemove(pendingRemoval.map { it.downloadId }.toSet()); pendingRemoval = emptyList(); selection = emptySet() }, Modifier.testTag("confirm_remove_offline")) { Text("Remove copies") } },
        dismissButton = { TextButton({ pendingRemoval = emptyList() }) { Text("Cancel") } })
}
