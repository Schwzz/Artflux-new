package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthParam
import com.example.model.AuthType
import com.example.ui.theme.RoseBadge

@Composable
fun ManualSetupForm(
    sourceName: String,
    onSourceNameChange: (String) -> Unit,
    apiUrl: String,
    onApiUrlChange: (String) -> Unit,
    searchParam: String,
    onSearchParamChange: (String) -> Unit,
    pageParam: String,
    onPageParamChange: (String) -> Unit,
    pageStartsAt: Int,
    onPageStartsAtChange: (Int) -> Unit,
    pageSizeParam: String,
    onPageSizeParamChange: (String) -> Unit,
    defaultPageSize: Int,
    onDefaultPageSizeChange: (Int) -> Unit,
    itemsPath: String,
    onItemsPathChange: (String) -> Unit,
    imageUrlField: String,
    onImageUrlFieldChange: (String) -> Unit,
    thumbUrlField: String,
    onThumbUrlFieldChange: (String) -> Unit,
    sampleUrlField: String,
    onSampleUrlFieldChange: (String) -> Unit,
    postUrlField: String,
    onPostUrlFieldChange: (String) -> Unit,
    tagsField: String,
    onTagsFieldChange: (String) -> Unit,
    ratingField: String,
    onRatingFieldChange: (String) -> Unit,
    mediaTypeField: String,
    onMediaTypeFieldChange: (String) -> Unit,
    titleField: String,
    onTitleFieldChange: (String) -> Unit,
    authorField: String,
    onAuthorFieldChange: (String) -> Unit,
    gifQueryTag: String,
    onGifQueryTagChange: (String) -> Unit,
    videoQueryTag: String,
    onVideoQueryTagChange: (String) -> Unit,
    safeRatingTag: String,
    onSafeRatingTagChange: (String) -> Unit,
    suggestiveRatingTag: String,
    onSuggestiveRatingTagChange: (String) -> Unit,
    adultRatingTag: String,
    onAdultRatingTagChange: (String) -> Unit,
    authType: AuthType,
    onAuthTypeChange: (AuthType) -> Unit,
    authHeaderName: String,
    onAuthHeaderNameChange: (String) -> Unit,
    authHeaderValue: String,
    onAuthHeaderValueChange: (String) -> Unit,
    authQueryParams: List<AuthParam>,
    onAuthQueryParamsChange: (List<AuthParam>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Basic Information
        FormSectionTitle("1. Basic Information")
        FormTextField(
            label = "Source Name *",
            value = sourceName,
            onValueChange = onSourceNameChange,
            placeholder = "e.g., Wallhaven, Danbooru",
            testTag = "input_source_name"
        )
        FormTextField(
            label = "API Base / Endpoint URL *",
            value = apiUrl,
            onValueChange = onApiUrlChange,
            placeholder = "https://api.example.com/v1/posts.json",
            testTag = "input_api_url"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 2.dp))

        // 2. Request Parameters
        FormSectionTitle("2. Request & Pagination")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Search Query Param",
                    value = searchParam,
                    onValueChange = onSearchParamChange,
                    placeholder = "tags or q",
                    testTag = "input_search_param"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Page Number Param",
                    value = pageParam,
                    onValueChange = onPageParamChange,
                    placeholder = "page or pid",
                    testTag = "input_page_param"
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Page Starts At (0 or 1)",
                    value = pageStartsAt.toString(),
                    onValueChange = { onPageStartsAtChange(it.toIntOrNull() ?: 1) },
                    placeholder = "1",
                    testTag = "input_page_starts_at"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Page Size Param",
                    value = pageSizeParam,
                    onValueChange = onPageSizeParamChange,
                    placeholder = "limit or count",
                    testTag = "input_page_size_param"
                )
            }
        }

        FormTextField(
            label = "Results JSON Array Path",
            value = itemsPath,
            onValueChange = onItemsPathChange,
            placeholder = "e.g. data or post (leave blank if root is array)",
            testTag = "input_items_path"
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 2.dp))

        // 3. Response Mapping
        FormSectionTitle("3. JSON Field Mappings")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Image URL Field *",
                    value = imageUrlField,
                    onValueChange = onImageUrlFieldChange,
                    placeholder = "file_url or url",
                    testTag = "input_image_field"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Preview / Thumbnail Field",
                    value = thumbUrlField,
                    onValueChange = onThumbUrlFieldChange,
                    placeholder = "preview_url or thumb",
                    testTag = "input_thumb_field"
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Sample / Medium Field",
                    value = sampleUrlField,
                    onValueChange = onSampleUrlFieldChange,
                    placeholder = "sample_url or large_file_url",
                    testTag = "input_sample_field"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Post Webpage URL / ID",
                    value = postUrlField,
                    onValueChange = onPostUrlFieldChange,
                    placeholder = "id or link",
                    testTag = "input_post_field"
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Tags Field",
                    value = tagsField,
                    onValueChange = onTagsFieldChange,
                    placeholder = "tags or tag_string",
                    testTag = "input_tags_field"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Rating Field",
                    value = ratingField,
                    onValueChange = onRatingFieldChange,
                    placeholder = "rating or purity",
                    testTag = "input_rating_field"
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 2.dp))

        // 4. Media Discovery & Ratings
        FormSectionTitle("4. Media Discovery Tags (Optional)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "GIF Query Tag",
                    value = gifQueryTag,
                    onValueChange = onGifQueryTagChange,
                    placeholder = "e.g. animated",
                    testTag = "input_gif_query_tag"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Video Query Tag",
                    value = videoQueryTag,
                    onValueChange = onVideoQueryTagChange,
                    placeholder = "e.g. webm or video",
                    testTag = "input_video_query_tag"
                )
            }
        }

        FormSectionTitle("5. Server-Side Rating Tags (Optional)")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Safe Tag",
                    value = safeRatingTag,
                    onValueChange = onSafeRatingTagChange,
                    placeholder = "rating:g",
                    testTag = "input_safe_rating_tag"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Suggestive Tag",
                    value = suggestiveRatingTag,
                    onValueChange = onSuggestiveRatingTagChange,
                    placeholder = "rating:s,q",
                    testTag = "input_suggestive_rating_tag"
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                FormTextField(
                    label = "Adult Tag",
                    value = adultRatingTag,
                    onValueChange = onAdultRatingTagChange,
                    placeholder = "rating:e",
                    testTag = "input_adult_rating_tag"
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 2.dp))

        // 6. Authentication Section
        FormSectionTitle("6. Authentication")
        Text(
            text = "Select the authentication method required by this API:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )

        // Auth Type Selector Chips (2x2 grid so Query Parameters is centered and does not wrap awkwardly)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AuthType.values().toList().chunked(2).forEach { rowTypes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTypes.forEach { type ->
                        val isSelected = authType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { onAuthTypeChange(type) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = type.label,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        when (authType) {
            AuthType.NONE -> {
                Text(
                    text = "No authentication credentials will be sent with requests.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            AuthType.BEARER_TOKEN -> {
                FormTextField(
                    label = "Bearer Token *",
                    value = authHeaderValue,
                    onValueChange = onAuthHeaderValueChange,
                    placeholder = "eyJhbGciOi...",
                    testTag = "input_bearer_token"
                )
            }

            AuthType.CUSTOM_HEADER -> {
                FormTextField(
                    label = "Header Name *",
                    value = authHeaderName,
                    onValueChange = onAuthHeaderNameChange,
                    placeholder = "e.g. X-API-KEY or Client-ID",
                    testTag = "input_custom_header_name"
                )
                FormTextField(
                    label = "Header Value *",
                    value = authHeaderValue,
                    onValueChange = onAuthHeaderValueChange,
                    placeholder = "Your API Key or Token",
                    testTag = "input_custom_header_value"
                )
            }

            AuthType.QUERY_PARAMS -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Query Parameters (e.g. api_key, user_id):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )

                    authQueryParams.forEachIndexed { index, param ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FormTextField(
                                    label = "Param Key",
                                    value = param.key,
                                    onValueChange = { newKey ->
                                        val updated = authQueryParams.toMutableList()
                                        updated[index] = param.copy(key = newKey)
                                        onAuthQueryParamsChange(updated)
                                    },
                                    placeholder = "api_key",
                                    testTag = "input_query_param_key_$index"
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FormTextField(
                                    label = "Param Value",
                                    value = param.value,
                                    onValueChange = { newVal ->
                                        val updated = authQueryParams.toMutableList()
                                        updated[index] = param.copy(value = newVal)
                                        onAuthQueryParamsChange(updated)
                                    },
                                    placeholder = "secret_value",
                                    testTag = "input_query_param_value_$index"
                                )
                            }
                            IconButton(
                                onClick = {
                                    val updated = authQueryParams.toMutableList()
                                    updated.removeAt(index)
                                    onAuthQueryParamsChange(updated)
                                },
                                modifier = Modifier
                                    .padding(top = 18.dp)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove parameter",
                                    tint = RoseBadge,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val updated = authQueryParams.toMutableList()
                            updated.add(AuthParam("", ""))
                            onAuthQueryParamsChange(updated)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("add_auth_param_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Parameter", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FormSectionTitle(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
