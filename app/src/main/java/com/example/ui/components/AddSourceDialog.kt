package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.MediaApiClient
import com.example.model.AuthParam
import com.example.model.AuthType
import com.example.model.MediaSourceConfig
import com.example.model.SourceDiagnosticReport
import kotlinx.coroutines.launch

enum class AddSourceStep {
    ENTRY_CHOICE,
    TEMPLATES,
    IMPORT_JSON,
    MANUAL_FORM,
    REVIEW_AND_TEST
}

@Composable
fun AddSourceDialog(
    initialConfig: MediaSourceConfig? = null,
    onDismiss: () -> Unit,
    onSave: (MediaSourceConfig) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { MediaApiClient() }

    var currentStep by remember {
        mutableStateOf(
            if (initialConfig != null) AddSourceStep.MANUAL_FORM else AddSourceStep.ENTRY_CHOICE
        )
    }

    // Form fields state
    var sourceName by remember { mutableStateOf(initialConfig?.name ?: "") }
    var apiUrl by remember { mutableStateOf(initialConfig?.apiUrl ?: "") }
    var searchParam by remember { mutableStateOf(initialConfig?.searchParam ?: "tags") }
    var pageParam by remember { mutableStateOf(initialConfig?.pageParam ?: "page") }
    var pageStartsAt by remember { mutableIntStateOf(initialConfig?.pageStartsAt ?: 1) }
    var pageSizeParam by remember { mutableStateOf(initialConfig?.pageSizeParam ?: "limit") }
    var defaultPageSize by remember { mutableIntStateOf(initialConfig?.defaultPageSize ?: 25) }
    var itemsPath by remember { mutableStateOf(initialConfig?.itemsPath ?: "") }
    var imageUrlField by remember { mutableStateOf(initialConfig?.imageUrlField ?: "file_url") }
    var thumbUrlField by remember { mutableStateOf(initialConfig?.thumbUrlField ?: "preview_url") }
    var sampleUrlField by remember { mutableStateOf(initialConfig?.sampleUrlField ?: "sample_url") }
    var postUrlField by remember { mutableStateOf(initialConfig?.postUrlField ?: "id") }
    var tagsField by remember { mutableStateOf(initialConfig?.tagsField ?: "tags") }
    var ratingField by remember { mutableStateOf(initialConfig?.ratingField ?: "rating") }
    var mediaTypeField by remember { mutableStateOf(initialConfig?.mediaTypeField ?: "") }
    var titleField by remember { mutableStateOf(initialConfig?.titleField ?: "tags") }
    var authorField by remember { mutableStateOf(initialConfig?.authorField ?: "owner") }
    var description by remember { mutableStateOf(initialConfig?.description ?: "") }

    var gifQueryTag by remember { mutableStateOf(initialConfig?.gifQueryTag ?: "") }
    var videoQueryTag by remember { mutableStateOf(initialConfig?.videoQueryTag ?: "") }
    var safeRatingTag by remember { mutableStateOf(initialConfig?.safeRatingTag ?: "") }
    var suggestiveRatingTag by remember { mutableStateOf(initialConfig?.suggestiveRatingTag ?: "") }
    var adultRatingTag by remember { mutableStateOf(initialConfig?.adultRatingTag ?: "") }

    // Authentication State
    var authType by remember { mutableStateOf(initialConfig?.authType ?: AuthType.NONE) }
    var authHeaderName by remember { mutableStateOf(initialConfig?.authHeaderName ?: "") }
    var authHeaderValue by remember { mutableStateOf(initialConfig?.authHeaderValue ?: "") }
    var authQueryParams by remember {
        mutableStateOf(
            if (initialConfig != null && initialConfig.authQueryParams.isNotEmpty()) {
                initialConfig.authQueryParams
            } else if (initialConfig != null && initialConfig.apiKey.isNotBlank() && initialConfig.apiKeyInQuery) {
                listOf(AuthParam(initialConfig.apiKeyQueryParam.ifBlank { "api_key" }, initialConfig.apiKey))
            } else {
                listOf(AuthParam("api_key", ""))
            }
        )
    }

    // Import JSON State
    var importJsonText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var promptCopiedFeedback by remember { mutableStateOf(false) }

    // Testing / Diagnostics State
    var isTestingSource by remember { mutableStateOf(false) }
    var diagnosticReport by remember { mutableStateOf<SourceDiagnosticReport?>(null) }

    fun populateFromConfig(config: MediaSourceConfig) {
        sourceName = config.name
        apiUrl = config.apiUrl
        searchParam = config.searchParam
        pageParam = config.pageParam
        pageStartsAt = config.pageStartsAt
        pageSizeParam = config.pageSizeParam
        defaultPageSize = config.defaultPageSize
        itemsPath = config.itemsPath
        imageUrlField = config.imageUrlField
        thumbUrlField = config.thumbUrlField
        sampleUrlField = config.sampleUrlField
        postUrlField = config.postUrlField
        tagsField = config.tagsField
        ratingField = config.ratingField
        mediaTypeField = config.mediaTypeField
        titleField = config.titleField
        authorField = config.authorField
        description = config.description
        gifQueryTag = config.gifQueryTag
        videoQueryTag = config.videoQueryTag
        safeRatingTag = config.safeRatingTag
        suggestiveRatingTag = config.suggestiveRatingTag
        adultRatingTag = config.adultRatingTag
        authType = config.authType
        authHeaderName = config.authHeaderName
        authHeaderValue = config.authHeaderValue
        authQueryParams = if (config.authQueryParams.isNotEmpty()) {
            config.authQueryParams
        } else if (config.apiKey.isNotBlank() && config.apiKeyInQuery) {
            listOf(AuthParam(config.apiKeyQueryParam.ifBlank { "api_key" }, config.apiKey))
        } else {
            listOf(AuthParam("api_key", ""))
        }
    }

    fun buildCurrentConfig(): MediaSourceConfig {
        return MediaSourceConfig(
            id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
            name = sourceName.trim().ifBlank { "Custom Source" },
            apiUrl = apiUrl.trim(),
            searchParam = searchParam.trim(),
            pageParam = pageParam.trim(),
            pageStartsAt = pageStartsAt,
            pageSizeParam = pageSizeParam.trim(),
            defaultPageSize = defaultPageSize,
            itemsPath = itemsPath.trim(),
            imageUrlField = imageUrlField.trim(),
            thumbUrlField = thumbUrlField.trim(),
            sampleUrlField = sampleUrlField.trim(),
            postUrlField = postUrlField.trim(),
            tagsField = tagsField.trim(),
            ratingField = ratingField.trim(),
            mediaTypeField = mediaTypeField.trim(),
            titleField = titleField.trim(),
            authorField = authorField.trim(),
            description = description.trim(),
            gifQueryTag = gifQueryTag.trim(),
            videoQueryTag = videoQueryTag.trim(),
            safeRatingTag = safeRatingTag.trim(),
            suggestiveRatingTag = suggestiveRatingTag.trim(),
            adultRatingTag = adultRatingTag.trim(),
            authType = authType,
            authHeaderName = authHeaderName.trim(),
            authHeaderValue = authHeaderValue.trim(),
            authQueryParams = authQueryParams.filter { it.key.isNotBlank() },
            isBuiltIn = false
        )
    }

    fun runDiagnostics(targetConfig: MediaSourceConfig = buildCurrentConfig()) {
        coroutineScope.launch {
            isTestingSource = true
            diagnosticReport = null
            val report = apiClient.diagnoseSource(targetConfig)
            diagnosticReport = report
            isTestingSource = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Top App Bar
                AddSourceTopAppBar(
                    currentStep = currentStep,
                    isEditMode = initialConfig != null,
                    onBack = {
                        currentStep = when (currentStep) {
                            AddSourceStep.REVIEW_AND_TEST -> AddSourceStep.MANUAL_FORM
                            else -> AddSourceStep.ENTRY_CHOICE
                        }
                    },
                    onClose = onDismiss
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Step Content
                Box(modifier = Modifier.weight(1f)) {
                    when (currentStep) {
                        AddSourceStep.ENTRY_CHOICE -> {
                            EntryChoiceScreen(
                                onSelectTemplates = { currentStep = AddSourceStep.TEMPLATES },
                                onSelectImport = { currentStep = AddSourceStep.IMPORT_JSON },
                                onSelectManual = { currentStep = AddSourceStep.MANUAL_FORM }
                            )
                        }

                        AddSourceStep.TEMPLATES -> {
                            TemplatesSelectionScreen(
                                onSelectTemplate = { template ->
                                    populateFromConfig(template)
                                    currentStep = AddSourceStep.REVIEW_AND_TEST
                                    runDiagnostics(template)
                                }
                            )
                        }

                        AddSourceStep.IMPORT_JSON -> {
                            ImportJsonScreen(
                                jsonText = importJsonText,
                                onJsonTextChange = {
                                    importJsonText = it
                                    importError = null
                                },
                                errorMessage = importError,
                                promptCopied = promptCopiedFeedback,
                                onCopyPrompt = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Artflux AI Prompt", MediaSourceConfig.AI_PROMPT_TEMPLATE)
                                    clipboard.setPrimaryClip(clip)
                                    promptCopiedFeedback = true
                                },
                                onPasteFromClipboard = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text?.toString() ?: ""
                                        importJsonText = text
                                        importError = null
                                    }
                                },
                                onValidateAndImport = {
                                    val parseResult = MediaSourceConfig.parseJson(importJsonText)
                                    parseResult.onSuccess { parsedConfig ->
                                        populateFromConfig(parsedConfig)
                                        currentStep = AddSourceStep.REVIEW_AND_TEST
                                        runDiagnostics(parsedConfig)
                                    }.onFailure { error ->
                                        importError = error.message ?: "Failed to parse JSON"
                                    }
                                }
                            )
                        }

                        AddSourceStep.MANUAL_FORM -> {
                            ManualSetupForm(
                                sourceName = sourceName,
                                onSourceNameChange = { sourceName = it },
                                apiUrl = apiUrl,
                                onApiUrlChange = { apiUrl = it },
                                searchParam = searchParam,
                                onSearchParamChange = { searchParam = it },
                                pageParam = pageParam,
                                onPageParamChange = { pageParam = it },
                                pageStartsAt = pageStartsAt,
                                onPageStartsAtChange = { pageStartsAt = it },
                                pageSizeParam = pageSizeParam,
                                onPageSizeParamChange = { pageSizeParam = it },
                                defaultPageSize = defaultPageSize,
                                onDefaultPageSizeChange = { defaultPageSize = it },
                                itemsPath = itemsPath,
                                onItemsPathChange = { itemsPath = it },
                                imageUrlField = imageUrlField,
                                onImageUrlFieldChange = { imageUrlField = it },
                                thumbUrlField = thumbUrlField,
                                onThumbUrlFieldChange = { thumbUrlField = it },
                                sampleUrlField = sampleUrlField,
                                onSampleUrlFieldChange = { sampleUrlField = it },
                                postUrlField = postUrlField,
                                onPostUrlFieldChange = { postUrlField = it },
                                tagsField = tagsField,
                                onTagsFieldChange = { tagsField = it },
                                ratingField = ratingField,
                                onRatingFieldChange = { ratingField = it },
                                mediaTypeField = mediaTypeField,
                                onMediaTypeFieldChange = { mediaTypeField = it },
                                titleField = titleField,
                                onTitleFieldChange = { titleField = it },
                                authorField = authorField,
                                onAuthorFieldChange = { authorField = it },
                                gifQueryTag = gifQueryTag,
                                onGifQueryTagChange = { gifQueryTag = it },
                                videoQueryTag = videoQueryTag,
                                onVideoQueryTagChange = { videoQueryTag = it },
                                safeRatingTag = safeRatingTag,
                                onSafeRatingTagChange = { safeRatingTag = it },
                                suggestiveRatingTag = suggestiveRatingTag,
                                onSuggestiveRatingTagChange = { suggestiveRatingTag = it },
                                adultRatingTag = adultRatingTag,
                                onAdultRatingTagChange = { adultRatingTag = it },
                                authType = authType,
                                onAuthTypeChange = { authType = it },
                                authHeaderName = authHeaderName,
                                onAuthHeaderNameChange = { authHeaderName = it },
                                authHeaderValue = authHeaderValue,
                                onAuthHeaderValueChange = { authHeaderValue = it },
                                authQueryParams = authQueryParams,
                                onAuthQueryParamsChange = { authQueryParams = it }
                            )
                        }

                        AddSourceStep.REVIEW_AND_TEST -> {
                            ReviewAndTestScreen(
                                config = buildCurrentConfig(),
                                report = diagnosticReport,
                                isTesting = isTestingSource,
                                onRunTest = { runDiagnostics() },
                                onEditDetails = { currentStep = AddSourceStep.MANUAL_FORM }
                            )
                        }
                    }
                }

                // Bottom Action Footer
                if (currentStep == AddSourceStep.MANUAL_FORM || currentStep == AddSourceStep.REVIEW_AND_TEST) {
                    AddSourceBottomBar(
                        isEditMode = initialConfig != null,
                        isTestingSource = isTestingSource,
                        canTest = apiUrl.isNotBlank(),
                        canSave = sourceName.isNotBlank() && apiUrl.isNotBlank(),
                        onTestClick = {
                            if (currentStep == AddSourceStep.MANUAL_FORM) {
                                val config = buildCurrentConfig()
                                currentStep = AddSourceStep.REVIEW_AND_TEST
                                runDiagnostics(config)
                            } else {
                                runDiagnostics()
                            }
                        },
                        onSaveClick = {
                            val validation = MediaSourceConfig.validateConfig(buildCurrentConfig())
                            if (validation.isEmpty()) {
                                onSave(buildCurrentConfig())
                            }
                        }
                    )
                }
            }
        }
    }
}
