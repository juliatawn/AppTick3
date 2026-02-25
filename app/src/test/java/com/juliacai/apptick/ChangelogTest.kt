package com.juliacai.apptick

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ChangelogTest {

    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Before
    fun setup() {
        prefs = mock()
        editor = mock()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putLong(org.mockito.kotlin.any(), org.mockito.kotlin.any())).thenReturn(editor)
    }

    @Test
    fun shouldShowChangelogOnLaunch_returnsTrue_whenVersionNeverSeen() {
        whenever(prefs.getLong("last_seen_changelog_version", -1L)).thenReturn(-1L)

        val shouldShow = shouldShowChangelogOnLaunch(prefs, versionCode = 30L)

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun shouldShowChangelogOnLaunch_returnsFalse_whenVersionAlreadySeen() {
        whenever(prefs.getLong("last_seen_changelog_version", -1L)).thenReturn(30L)

        val shouldShow = shouldShowChangelogOnLaunch(prefs, versionCode = 30L)

        assertThat(shouldShow).isFalse()
    }

    @Test
    fun shouldShowChangelogOnLaunch_returnsTrue_whenAppUpdated() {
        whenever(prefs.getLong("last_seen_changelog_version", -1L)).thenReturn(29L)

        val shouldShow = shouldShowChangelogOnLaunch(prefs, versionCode = 30L)

        assertThat(shouldShow).isTrue()
    }

    @Test
    fun markChangelogSeen_persistsCurrentVersion() {
        markChangelogSeen(prefs, versionCode = 30L)

        verify(editor).putLong("last_seen_changelog_version", 30L)
        verify(editor).apply()
    }
}
