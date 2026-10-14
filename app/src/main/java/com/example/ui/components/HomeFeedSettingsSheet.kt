package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FilterState
import com.example.model.MediaRating
import com.example.model.MediaSourceConfig
import com.example.model.MediaType
import com.example.model.SortOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedSettingsSheet(
    sources: List<MediaSourceConfig>,
    currentSource: MediaSourceConfig,
    currentFilter: FilterState,
    onApply: (MediaSourceConfig, FilterState) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf(currentSource) }
    val supportedTypes = remember(selectedSource) { selectedSource.getSupportedMediaTypes() }
    var selectedType by remember(selectedSource) {
        mutableStateOf(if (currentFilter.mediaType in supportedTypes) currentFilter.mediaType else MediaType.ALL)
    }
    var selectedRating by remember { mutableStateOf(currentFilter.rating) }
    var selectedSort by remember { mutableStateOf(currentFilter.sort) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feed Settings (Home)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                TextButton(
                    onClick = {
                        selectedSource = MediaSourceConfig.BUILT_IN_SAFEBOORU
                        selectedType = MediaType.ALL
                        selectedRating = MediaRating.ALL
                        selectedSort = SortOption.LATEST
                    }
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Source Selector Dropdown Menu
            FilterSectionHeader(title = "Source")
            var isSourceDropdownExpanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .clickable { isSourceDropdownExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("home_source_dropdown_anchor"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = selectedSource.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (selectedSource.isBuiltIn) "Built-in Source" else "Custom Source (${selectedSource.apiUrl})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select source",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = isSourceDropdownExpanded,
                    onDismissRequest = { isSourceDropdownExpanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("home_source_dropdown_menu")
                ) {
                    sources.forEach { source ->
                        val isSelected = selectedSource.id == source.id
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = source.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (source.isBuiltIn) "Built-in" else "Custom",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedSource = source
                                val newSupported = source.getSupportedMediaTypes()
                                if (selectedType !in newSupported) {
                                    selectedType = MediaType.ALL
                                }
                                isSourceDropdownExpanded = false
                            },
                            modifier = Modifier.testTag("home_source_option_${source.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isHomeWaifu = selectedSource.apiUrl.contains("waifu.im") || selectedSource.id.contains("waifu")

            // Media Type Section
            if (supportedTypes.size > 1) {
                FilterSectionHeader(title = "Media Type")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    supportedTypes.forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { selectedType = type }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("home_type_option_${type.name}")
                        ) {
                            Text(
                                text = type.label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Rating Section
            FilterSectionHeader(title = "Content Rating")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableRatings = if (isHomeWaifu) listOf(MediaRating.ALL, MediaRating.SAFE, MediaRating.ADULT) else MediaRating.values().filter { it != MediaRating.UNKNOWN }
                availableRatings.forEach { rating ->
                    val isSelected = selectedRating == rating
                    val ratingDisplay = if (isHomeWaifu) {
                        when (rating) {
                            MediaRating.SAFE -> "SFW"
                            MediaRating.ADULT -> "NSFW"
                            else -> "All"
                        }
                    } else rating.label

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { selectedRating = rating }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("home_rating_option_${rating.name}")
                    ) {
                        Text(
                            text = ratingDisplay,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sort Order
            FilterSectionHeader(title = "Sort Order")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableSorts: List<SortOption> = if (isHomeWaifu) listOf(SortOption.LATEST, SortOption.RANDOM) else SortOption.values().toList()
                availableSorts.forEach { sort ->
                    val isSelected = selectedSort == sort
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { selectedSort = sort }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = sort.label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Apply Button
            Button(
                onClick = {
                    onApply(
                        selectedSource,
                        currentFilter.copy(
                            mediaType = selectedType,
                            rating = selectedRating,
                            sort = selectedSort
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_home_settings_button")
            ) {
                Text("Apply Feed Settings", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
