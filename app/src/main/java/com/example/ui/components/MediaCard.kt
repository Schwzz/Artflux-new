package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.MediaApiClient
import com.example.model.MediaItem
import com.example.model.MediaRating
import com.example.model.MediaType
import com.example.model.ThumbnailQuality
import com.example.util.ArtfluxBlurTransformation
import com.example.util.ArtfluxImageLoader

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    quality: ThumbnailQuality = ThumbnailQuality.DEFAULT,
    blurNsfw: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = ArtfluxImageLoader.get(context)

    val isProtected = item.rating == MediaRating.ADULT || item.rating == MediaRating.SUGGESTIVE || item.rating == MediaRating.UNKNOWN
    val shouldBlur = blurNsfw && isProtected

    // Primary URL selection respecting deterministic preview contract:
    // Feed strictly uses static previewUrl; never loads raw video media
    val primaryDisplayUrl = if (!MediaApiClient.isVideoUrl(item.previewUrl) && item.previewUrl.isNotBlank()) {
        item.previewUrl
    } else {
        item.getThumbnailForQuality(quality).takeIf { !MediaApiClient.isVideoUrl(it) }.orEmpty()
    }
    val fallbackDisplayUrl = item.getThumbnailFallbackUrl(quality).takeIf { !MediaApiClient.isVideoUrl(it) }.orEmpty()

    var isPrimaryFailed by remember(item.id, quality) { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("media_item_card_${item.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(item.aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val effectiveDisplayUrl = if (isPrimaryFailed) fallbackDisplayUrl else primaryDisplayUrl

            if (effectiveDisplayUrl.isNotBlank() && !MediaApiClient.isVideoUrl(effectiveDisplayUrl)) {
                // Media Image with real bitmap/Compose blur (NSFW media remains visible underneath real blur)
                val imageRequestBuilder = ImageRequest.Builder(context)
                    .data(effectiveDisplayUrl)
                    .crossfade(true)

                if (shouldBlur) {
                    imageRequestBuilder.transformations(ArtfluxBlurTransformation(radius = 22, sampling = 4f))
                }

                SubcomposeAsyncImage(
                    model = imageRequestBuilder.build(),
                    imageLoader = imageLoader,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (shouldBlur) Modifier.blur(24.dp) else Modifier),
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        if (!isPrimaryFailed && primaryDisplayUrl != fallbackDisplayUrl && fallbackDisplayUrl.isNotBlank()) {
                            isPrimaryFailed = true
                        } else {
                            // Clean, polished error state
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayArrow else Icons.Default.Image,
                                        contentDescription = "Preview unavailable",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.sourceName,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                )
            } else {
                // Clean placeholder state when no image preview is available (never plays video in feed)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (item.mediaType == MediaType.VIDEO) Icons.Default.PlayArrow else Icons.Default.Image,
                            contentDescription = item.title,
                            tint = if (item.mediaType == MediaType.VIDEO) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.sourceName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Centered Play Button overlay for Videos (Only if not blurred)
            if (item.mediaType == MediaType.VIDEO && !shouldBlur) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Subtle Media Indicator Badge (GIF or VIDEO) - Only if not blurred
            if (!shouldBlur) {
                when (item.mediaType) {
                    MediaType.GIF -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "GIF",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    MediaType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "VIDEO",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {}
                }
            }

        }
    }
}
