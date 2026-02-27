package com.juliacai.apptick

import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.edit
import kotlinx.coroutines.launch

private const val PREF_LAST_SEEN_FEATURE_PHOTOS_VERSION = "last_seen_feature_photos_version"
const val STARTUP_ROUTE_FEATURE_PHOTOS = "featurePhotos"
const val STARTUP_ROUTE_PERMISSION_ONBOARDING = "permissionOnboarding"
const val STARTUP_ROUTE_MAIN = "main"

fun startupDestinationRoute(
    shouldShowFeaturePhotos: Boolean,
    needsPermissions: Boolean
): String {
    return when {
        shouldShowFeaturePhotos -> STARTUP_ROUTE_FEATURE_PHOTOS
        needsPermissions -> STARTUP_ROUTE_PERMISSION_ONBOARDING
        else -> STARTUP_ROUTE_MAIN
    }
}

fun postFeaturePhotosRoute(needsPermissions: Boolean): String {
    return if (needsPermissions) {
        STARTUP_ROUTE_PERMISSION_ONBOARDING
    } else {
        STARTUP_ROUTE_MAIN
    }
}

fun featurePhotoResIds(): List<Int> {
    return listOf(
        R.drawable.apptick_1,
        R.drawable.apptick_2,
        R.drawable.apptick_3,
        R.drawable.apptick_4,
        R.drawable.apptick_5,
        R.drawable.apptick_6,
        R.drawable.apptick_7
    )
}

fun shouldShowFeaturePhotosOnLaunch(
    prefs: SharedPreferences,
    versionCode: Long,
    photoResIds: List<Int>
): Boolean {
    if (photoResIds.isEmpty()) return false
    val lastSeenVersion = prefs.getLong(PREF_LAST_SEEN_FEATURE_PHOTOS_VERSION, -1L)
    return lastSeenVersion != versionCode
}

fun markFeaturePhotosSeen(prefs: SharedPreferences, versionCode: Long) {
    prefs.edit { putLong(PREF_LAST_SEEN_FEATURE_PHOTOS_VERSION, versionCode) }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FeaturePhotoCarouselScreen(
    photoResIds: List<Int>,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    if (photoResIds.isEmpty()) {
        LaunchedEffect(Unit) {
            onComplete()
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { photoResIds.size }
    )
    val scope = rememberCoroutineScope()
    val currentIndex = pagerState.currentPage
    val isLastImage = currentIndex == photoResIds.lastIndex

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    Image(
                        painter = painterResource(id = photoResIds[page]),
                        contentDescription = "AppTick feature ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(photoResIds.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentIndex) 10.dp else 8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(
                                if (index == currentIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    TextButton(onClick = onSkip) {
                        Text(text = "SKIP")
                    }
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Button(
                        onClick = {
                            if (isLastImage) {
                                onComplete()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentIndex + 1)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLastImage) "LET'S GO" else "NEXT")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeaturePhotoCarouselScreenPreview() {
    val previewPhotos = featurePhotoResIds().ifEmpty { listOf(R.drawable.ic_launcher_foreground) }
    AppTheme {
        FeaturePhotoCarouselScreen(
            photoResIds = previewPhotos,
            onSkip = {},
            onComplete = {}
        )
    }
}
