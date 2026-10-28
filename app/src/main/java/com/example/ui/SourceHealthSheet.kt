package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

internal fun checkedTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceHealthSheet(sources: List<MediaSourceConfig>, health: Map<String, SourceHealth>, checking: Set<String>,
    onCheck: (MediaSourceConfig) -> Unit, onDiagnostics: (MediaSourceConfig) -> Unit,
    onEdit: (MediaSourceConfig) -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).testTag("source_health_sheet"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Source health", style = MaterialTheme.typography.headlineSmall)
                Text("Status comes from your latest request or manual check. Checks use a small API request.", style = MaterialTheme.typography.bodyMedium)
            }
            items(sources, key = { it.id }) { source ->
                val status = health[source.id]
                val busy = source.id in checking
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(source.name, style = MaterialTheme.typography.titleMedium)
                        Text(if (busy) "Checking…" else status?.condition?.label ?: "Not checked", color = if (status != null && status.condition != SourceCondition.HEALTHY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        if (status != null) {
                            Text(status.condition.guidance, style = MaterialTheme.typography.bodySmall)
                            Text("Last checked: ${checkedTime(status.checkedAt)}", style = MaterialTheme.typography.labelSmall)
                            if (status.lastSuccessAt > 0 && status.condition != SourceCondition.HEALTHY) Text("Last connected: ${checkedTime(status.lastSuccessAt)}", style = MaterialTheme.typography.labelSmall)
                            if (!status.canRetry(now)) Text("Retry after ${checkedTime(status.retryAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton({ onCheck(source) }, enabled = !busy && status?.canRetry(now) != false,
                                modifier = Modifier.testTag("check_health_${source.id}")) { Text("Check") }
                            TextButton({ onDiagnostics(source) }, enabled = !busy && status?.canRetry(now) != false) { Text("Diagnostics") }
                            TextButton({ onEdit(source) }) { Text(if (source.isBuiltIn) "Customize" else "Edit") }
                        }
                    }
                }
            }
            item { TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Close") } }
        }
    }
}

@Composable
fun SourceHealthNotice(health: SourceHealth?, onDetails: () -> Unit) {
    if (health != null && health.condition != SourceCondition.HEALTHY) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("source_health_notice")) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(health.condition.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onDetails) { Text("Source health") }
            }
        }
    }
}
