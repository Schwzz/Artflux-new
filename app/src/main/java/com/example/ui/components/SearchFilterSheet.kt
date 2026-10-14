package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

val PRESET_TAGS = listOf("1girl", "landscape", "scenery", "original", "cat_ears", "solo", "monochrome", "cyberpunk", "aesthetic", "sky")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    sources: List<MediaSourceConfig>,
    activeSource: MediaSourceConfig,
    filterState: FilterState,
    currentQuery: String,
    onApply: (MediaSourceConfig, FilterState, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSource by remember { mutableStateOf(activeSource) }
    val supportedTypes = remember(selectedSource) { selectedSource.getSupportedMediaTypes() }
    var selectedType by remember(selectedSource) {
        mutableStateOf(if (filterState.mediaType in supportedTypes) filterState.mediaType else MediaType.ALL)
    }
    var selectedRating by remember { mutableStateOf(filterState.rating) }
    var selectedSort by remember { mutableStateOf(filterState.sort) }
    var selectedQuery by remember { mutableStateOf(currentQuery) }

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
                    text = "Search Filters",
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
                        selectedQuery = ""
                    }
                ) {
                    Text("Reset All", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Source Selector Section inside Search Filter Sheet
            FilterSectionHeader(title = "Source")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sources.forEach { source ->
                    val isSelected = selectedSource.id == source.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedSource = source
                                val newSupported = source.getSupportedMediaTypes()
                                if (selectedType !in newSupported) {
                                    selectedType = MediaType.ALL
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("filter_source_${source.id}")
                    ) {
                        Text(
                            text = source.name,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isWaifu = selectedSource.apiUrl.contains("waifu.im") || selectedSource.id.contains("waifu")

            // Media Type Section (hidden if source supports only ALL media type)
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
                                .testTag("filter_type_${type.name}")
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

            // Content Rating Section
            FilterSectionHeader(title = "Content Rating")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val availableRatings = if (isWaifu) listOf(MediaRating.ALL, MediaRating.SAFE, MediaRating.ADULT) else MediaRating.values().filter { it != MediaRating.UNKNOWN }
                availableRatings.forEach { rating ->
                    val isSelected = selectedRating == rating
                    val ratingDisplay = if (isWaifu) {
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
                            .testTag("filter_rating_${rating.name}")
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

            // Tag Presets
            FilterSectionHeader(title = "Tag Presets")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PRESET_TAGS.forEach { tag ->
                    val isSelected = selectedQuery.equals(tag, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedQuery = if (isSelected) "" else tag
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("filter_tag_$tag")
                    ) {
                        Text(
                            text = "#$tag",
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                val availableSorts: List<SortOption> = if (isWaifu) listOf(SortOption.LATEST, SortOption.RANDOM) else SortOption.values().toList()
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
                        filterState.copy(
                            mediaType = selectedType,
                            rating = selectedRating,
                            sort = selectedSort
                        ),
                        selectedQuery
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_search_filters_button")
            ) {
                Text("Apply Filters", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
