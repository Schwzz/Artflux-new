package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.BrowsingPreferences
import com.example.model.GridDensity

@Composable
fun PersonalSettings(browsing: BrowsingPreferences, onDensity: (GridDensity) -> Unit, onBlocks: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make it yours", style = MaterialTheme.typography.titleMedium)
            Text("Artwork size", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GridDensity.entries.forEach { density -> FilterChip(browsing.density == density, { onDensity(density) }, label = { Text(density.label, style = MaterialTheme.typography.labelSmall) }) }
            }
            OutlinedButton(onBlocks, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Block, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Blocked tags · ${browsing.blockedTags.count { it.enabled }} active")
            }
            Text("Backup your library", style = MaterialTheme.typography.titleSmall)
            Text("Collections, searches, filters, themes, and source configurations. API keys and offline files are excluded.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onExport, Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onImport, Modifier.weight(1f)) { Text("Restore") }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedTagsSheet(browsing: BrowsingPreferences, onAdd: (String) -> Unit, onToggle: (String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var tag by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Blocked tags", style = MaterialTheme.typography.headlineSmall)
            Text("Hide artwork with an exact matching tag, ignoring capitalization. Your saved artwork is kept. Long-press a tag in the viewer to block it.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(tag, { tag = it.take(200) }, label = { Text("Tag to block") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAdd(tag); tag = "" }, enabled = tag.isNotBlank()) { Text("Block") }
            }
            if (browsing.blockedTags.isEmpty()) Text("Your block list is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            browsing.blockedTags.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Switch(entry.enabled, { onToggle(entry.tag) }); Spacer(Modifier.width(10.dp))
                    Text(entry.tag, Modifier.weight(1f))
                    IconButton({ onRemove(entry.tag) }) { Icon(Icons.Default.Close, "Remove blocked tag ${entry.tag}") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
