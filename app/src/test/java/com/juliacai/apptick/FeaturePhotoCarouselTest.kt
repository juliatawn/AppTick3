package com.juliacai.apptick

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FeaturePhotoCarouselTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        prefs = mock()
        editor = mock()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)
    }

    @Test
    fun shouldShowFeaturePhotosOnLaunch_returnsFalse_whenNoPhotosExist() {
        val shouldShow = shouldShowFeaturePhotosOnLaunch(
            prefs = prefs,
            versionCode = 30L,
            photoResIds = emptyList()
        )

        assertThat(shouldShow).isFalse()
    }

    @Test
    fun shouldShowFeaturePhotosOnLaunch_returnsTrue_whenVersionNeverSeen() {
        whenever(prefs.getLong("last_seen_feature_photos_version", -1L)).thenReturn(-1L)

        val shouldShow = shouldShowFeaturePhotosOnLaunch(
            prefs = prefs,
            versionCode = 30L,
            photoResIds = listOf(1, 2)
        )

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun shouldShowFeaturePhotosOnLaunch_returnsFalse_whenVersionAlreadySeen() {
        whenever(prefs.getLong("last_seen_feature_photos_version", -1L)).thenReturn(30L)

        val shouldShow = shouldShowFeaturePhotosOnLaunch(
            prefs = prefs,
            versionCode = 30L,
            photoResIds = listOf(1, 2)
        )

        assertThat(shouldShow).isFalse()
    }

    @Test
    fun markFeaturePhotosSeen_persistsCurrentVersion() {
        markFeaturePhotosSeen(prefs, versionCode = 30L)

        verify(editor).putLong("last_seen_feature_photos_version", 30L)
        verify(editor).apply()
    }

    @Test
    fun featurePhotoResIds_returnsAllCarouselDrawablesInOrder() {
        val photoResIds = featurePhotoResIds()

        assertThat(photoResIds).containsExactly(
            R.drawable.apptick_1,
            R.drawable.apptick_2,
            R.drawable.apptick_3,
            R.drawable.apptick_4,
            R.drawable.apptick_5,
            R.drawable.apptick_6,
            R.drawable.apptick_7
        ).inOrder()
    }

    @Test
    fun featurePhotoResIds_containsNoDuplicates() {
        val photoResIds = featurePhotoResIds()

        assertThat(photoResIds.distinct()).hasSize(photoResIds.size)
    }

    @Test
    fun startupDestinationRoute_returnsFeaturePhotos_whenFeaturePhotosShouldShow() {
        val route = startupDestinationRoute(
            shouldShowFeaturePhotos = true,
            needsPermissions = true
        )

        assertThat(route).isEqualTo(STARTUP_ROUTE_FEATURE_PHOTOS)
    }

    @Test
    fun startupDestinationRoute_returnsPermissionOnboarding_whenNoFeaturePhotosAndPermissionsNeeded() {
        val route = startupDestinationRoute(
            shouldShowFeaturePhotos = false,
            needsPermissions = true
        )

        assertThat(route).isEqualTo(STARTUP_ROUTE_PERMISSION_ONBOARDING)
    }

    @Test
    fun startupDestinationRoute_returnsMain_whenNoFeaturePhotosAndNoPermissionsNeeded() {
        val route = startupDestinationRoute(
            shouldShowFeaturePhotos = false,
            needsPermissions = false
        )

        assertThat(route).isEqualTo(STARTUP_ROUTE_MAIN)
    }

    @Test
    fun postFeaturePhotosRoute_returnsPermissionOnboarding_whenPermissionsNeeded() {
        assertThat(postFeaturePhotosRoute(needsPermissions = true))
            .isEqualTo(STARTUP_ROUTE_PERMISSION_ONBOARDING)
    }

    @Test
    fun postFeaturePhotosRoute_returnsMain_whenPermissionsAlreadyGranted() {
        assertThat(postFeaturePhotosRoute(needsPermissions = false))
            .isEqualTo(STARTUP_ROUTE_MAIN)
    }
}
