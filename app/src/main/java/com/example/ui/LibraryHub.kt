package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ArtworkDownload
import com.example.model.*
import com.example.ui.components.MediaCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryHub(
    items: List<MediaItem>, collections: List<ArtCollection>, downloads: List<ArtworkDownload>, browsing: BrowsingPreferences,
    quality: ThumbnailQuality, blur: Boolean, onOpen: (Int, List<MediaItem>, Boolean) -> Unit,
    onCreate: (String) -> Unit, onRename: (String, String) -> Unit, onDelete: (String) -> Unit,
    onAdd: (String, List<MediaItem>) -> Unit, onRemove: (Set<String>) -> Unit,
    onDownload: (List<MediaItem>, Boolean) -> Unit, onRetry: (ArtworkDownload) -> Unit, onRemoveDownload: (Long) -> Unit,
    onRemoveFromCollection: (String, Set<String>) -> Unit = { _, _ -> },
    onStorage: () -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCollection by rememberSaveable { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    var addDialog by remember { mutableStateOf(false) }
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var removeOfflineRecord by remember { mutableStateOf<ArtworkDownload?>(null) }
    val collection = collections.firstOrNull { it.id == selectedCollection }
    val offlineKeys = downloads.filter { it.offline && it.complete }.map { it.item.libraryKey }.toSet()
    val libraryItems = if (tab == 2) (downloads.filter { it.offline && it.complete }.map { it.item } + items).distinctBy { it.libraryKey } else items
    val visible = libraryItems.filterNot { browsing.blocks(it) }.filter { item ->
        (collection == null || item.libraryKey in collection.mediaIds) && (tab != 2 || item.libraryKey in offlineKeys) &&
            (query.isBlank() || listOf(item.title, item.author.orEmpty(), item.sourceName).any { it.contains(query, true) } || item.tags.any { it.contains(query, true) })
    }
    val selected = visible.filter { it.libraryKey in selection }
    BackHandler(enabled = selection.isNotEmpty() || selectedCollection != null) {
        if (selection.isNotEmpty()) selection = emptySet() else selectedCollection = null
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedCollection != null) IconButton({ selectedCollection = null; selection = emptySet() }) { Icon(Icons.Default.ArrowBack, "Back to Library") }
            Column(Modifier.weight(1f)) {
                Text(collection?.name ?: "Your library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("${visible.size} artworks · ${offlineKeys.size} offline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onStorage, Modifier.testTag("library_storage_button")) { Icon(Icons.Default.Storage, "Manage offline storage") }
            if (collection != null) {
                IconButton({ name = collection.name; renameDialog = true }) { Icon(Icons.Default.Edit, "Rename collection") }
                IconButton({ confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, "Delete collection") }
            } else IconButton({ name = ""; createDialog = true }) { Icon(Icons.Default.CreateNewFolder, "New collection") }
        }
        if (collection == null) TabRow(tab) {
            listOf("Saved", "Collections", "Offline", "Downloads").forEachIndexed { i, title -> Tab(tab == i, { tab = i; selection = emptySet() }, text = { Text(title, maxLines = 1, style = MaterialTheme.typography.labelMedium) }) }
        }
        if (tab != 3 || collection != null) {
            OutlinedTextField(query, { query = it; selection = emptySet() }, placeholder = { Text(if (tab == 1 && collection == null) "Find a collection" else "Search your library") }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("library_search"))
        }
        if (tab == 3 && collection == null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (downloads.isEmpty()) item { LibraryEmpty("No downloads yet", "Queue artwork from Home or save a collection offline.") }
                items(downloads, key = { it.downloadId }) { record ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(record.item.title, maxLines = 2, fontWeight = FontWeight.Medium)
                            Text("${if (record.offline) "Offline copy" else "Gallery download"} · ${record.label}", style = MaterialTheme.typography.bodySmall)
                            if (record.total > 0 && !record.complete) LinearProgressIndicator(progress = { (record.bytes.toFloat() / record.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                            Row {
                                if (record.complete) TextButton({ onOpen(0, listOf(record.item), record.offline) }) { Text("Open") }
                                if (record.status == android.app.DownloadManager.STATUS_FAILED || record.fileMissing) TextButton({ onRetry(record) }) { Text("Retry") }
                                TextButton({ if (record.offline && record.complete) removeOfflineRecord = record else onRemoveDownload(record.downloadId) }) { Text(if (record.offline && record.complete) "Remove offline copy" else if (record.complete) "Clear record" else if (record.fileMissing) "Clear record" else "Cancel") }
                            }
                        }
                    }
                }
            }
        } else if (tab == 1 && collection == null) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (collections.isEmpty()) item { LibraryEmpty("Make room for your favorites", "Create a collection, then add artwork from your saved library.") }
                items(collections.filter { it.name.contains(query, true) }, key = { it.id }) { col ->
                    Card(onClick = { selectedCollection = col.id; query = ""; selection = emptySet() }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(col.name, fontWeight = FontWeight.SemiBold); Text("${col.mediaIds.size} artworks", style = MaterialTheme.typography.bodySmall) }
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ selection = if (selection.isEmpty()) visible.map { it.libraryKey }.toSet() else emptySet() }) { Text(if (selection.isEmpty()) "Select artworks" else "Clear (${selection.size})") }
                if (selection.isNotEmpty()) {
                    TextButton({ selection = visible.map { it.libraryKey }.toSet() }) { Text("Select all") }
                    TextButton({ addDialog = true }) { Text("Add to collection") }
                    TextButton({ onDownload(selected, false) }) { Text("Download") }
                    TextButton({ onDownload(selected, true) }) { Text("Save offline") }
                    if (collection != null) TextButton({ onRemoveFromCollection(collection.id, selected.map { it.libraryKey }.toSet()); selection = emptySet() }) { Text("Remove from collection") }
                    TextButton({ confirmRemove = true }) { Text("Unsave") }
                } else if (collection != null) TextButton({ onDownload(visible, true) }) { Text("Save collection offline") }
            }
            if (visible.isEmpty()) LibraryEmpty(if (query.isNotBlank()) "No matches" else if (tab == 2) "Take your art with you" else "Your next favorite is waiting",
                if (tab == 2) "Select saved artwork and tap Save offline. Copies stay on this device." else "Save artwork from the viewer. Blocked tags are hidden here too.")
            else LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Adaptive(browsing.density.tileWidth.dp), contentPadding = PaddingValues(12.dp),
                verticalItemSpacing = 10.dp, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                itemsIndexed(visible, key = { _, item -> item.libraryKey }) { index, item ->
                    Box {
                        val localUri = downloads.firstOrNull { it.offline && it.complete && it.item.libraryKey == item.libraryKey }?.localUri
                        val display = if (tab == 2 && localUri != null) item.copy(actualMediaUrl = localUri, previewUrl = if (item.mediaType == MediaType.VIDEO) "" else localUri, sampleUrl = null) else item
                        MediaCard(display, onClick = {
                            if (selection.isNotEmpty()) selection = if (item.libraryKey in selection) selection - item.libraryKey else selection + item.libraryKey
                            else onOpen(index, visible, tab == 2)
                        }, quality = quality, blurNsfw = blur)
                        Checkbox(checked = item.libraryKey in selection, onCheckedChange = { checked -> selection = if (checked) selection + item.libraryKey else selection - item.libraryKey },
                            modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = .85f), MaterialTheme.shapes.small))
                    }
                }
            }
        }
    }
    removeOfflineRecord?.let { record -> AlertDialog(onDismissRequest = { removeOfflineRecord = null }, title = { Text("Remove offline copy?") },
        text = { Text("Delete the local file for ${record.item.title}. Your saved artwork and collections are kept.") },
        confirmButton = { TextButton({ onRemoveDownload(record.downloadId); removeOfflineRecord = null }) { Text("Remove copy") } },
        dismissButton = { TextButton({ removeOfflineRecord = null }) { Text("Cancel") } }) }
    if (createDialog || renameDialog) AlertDialog(onDismissRequest = { createDialog = false; renameDialog = false },
        title = { Text(if (renameDialog) "Rename collection" else "New collection") }, text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text("Name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (renameDialog && collection != null) onRename(collection.id, name) else onCreate(name); createDialog = false; renameDialog = false }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton({ createDialog = false; renameDialog = false }) { Text("Cancel") } })
    if (confirmDelete && collection != null) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${collection.name}?") }, text = { Text("Your saved artwork and offline copies are kept.") },
        confirmButton = { TextButton({ onDelete(collection.id); selectedCollection = null; confirmDelete = false }) { Text("Delete collection") } }, dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } })
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text("Unsave ${selected.size} artworks?") }, text = { Text("They will also be removed from collections. Downloaded files are kept.") },
        confirmButton = { TextButton({ onRemove(selected.map { it.libraryKey }.toSet()); selection = emptySet(); confirmRemove = false }) { Text("Unsave") } }, dismissButton = { TextButton({ confirmRemove = false }) { Text("Cancel") } })
    if (addDialog) AlertDialog(onDismissRequest = { addDialog = false }, title = { Text("Add ${selected.size} artworks") },
        text = { Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            if (collections.isEmpty()) Text("Create a collection first.")
            collections.forEach { col -> TextButton({ onAdd(col.id, selected); addDialog = false; selection = emptySet() }) { Text(col.name) } }
        } }, confirmButton = { TextButton({ addDialog = false }) { Text("Close") } })
}

@Composable
private fun LibraryEmpty(title: String, message: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
