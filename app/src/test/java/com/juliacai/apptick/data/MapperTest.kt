package com.juliacai.apptick.data

import com.google.common.truth.Truth.assertThat
import com.juliacai.apptick.appLimit.AppInGroup
import com.juliacai.apptick.groups.AppLimitGroup
import org.junit.Test

class MapperTest {

    @Test
    fun `toEntity maps all fields`() {
        val domain = AppLimitGroup(
            id = 7L,
            name = "Focus",
            timeHrLimit = 1,
            timeMinLimit = 45,
            limitEach = true,
            resetHours = 6,
            weekDays = listOf(1, 3, 5),
            apps = listOf(AppInGroup("Instagram", "com.instagram.android", "ic")),
            paused = true,
            useTimeRange = true,
            startHour = 9,
            startMinute = 30,
            endHour = 18,
            endMinute = 0,
            cumulativeTime = true,
            timeRemaining = 10_000L,
            nextResetTime = 20_000L,
            nextAddTime = 30_000L,
            dwm = "Weekly"
        )

        val entity = domain.toEntity()

        assertThat(entity.id).isEqualTo(domain.id)
        assertThat(entity.name).isEqualTo(domain.name)
        assertThat(entity.timeHrLimit).isEqualTo(domain.timeHrLimit)
        assertThat(entity.timeMinLimit).isEqualTo(domain.timeMinLimit)
        assertThat(entity.limitEach).isEqualTo(domain.limitEach)
        assertThat(entity.resetHours).isEqualTo(domain.resetHours)
        assertThat(entity.weekDays).isEqualTo(domain.weekDays)
        assertThat(entity.apps).isEqualTo(domain.apps)
        assertThat(entity.paused).isEqualTo(domain.paused)
        assertThat(entity.useTimeRange).isEqualTo(domain.useTimeRange)
        assertThat(entity.startHour).isEqualTo(domain.startHour)
        assertThat(entity.startMinute).isEqualTo(domain.startMinute)
        assertThat(entity.endHour).isEqualTo(domain.endHour)
        assertThat(entity.endMinute).isEqualTo(domain.endMinute)
        assertThat(entity.cumulativeTime).isEqualTo(domain.cumulativeTime)
        assertThat(entity.timeRemaining).isEqualTo(domain.timeRemaining)
        assertThat(entity.nextResetTime).isEqualTo(domain.nextResetTime)
        assertThat(entity.nextAddTime).isEqualTo(domain.nextAddTime)
        assertThat(entity.dwm).isEqualTo(domain.dwm)
    }

    @Test
    fun `entity toDomainModel round trip preserves values`() {
        val entity = AppLimitGroupEntity(
            id = 3L,
            name = "RoundTrip",
            timeHrLimit = 2,
            timeMinLimit = 15,
            limitEach = false,
            resetHours = 8,
            weekDays = listOf(2, 4, 6),
            apps = listOf(AppInGroup("YouTube", "com.google.android.youtube", "ic")),
            paused = false,
            useTimeRange = true,
            startHour = 8,
            startMinute = 0,
            endHour = 22,
            endMinute = 0,
            cumulativeTime = true,
            timeRemaining = 55_000L,
            nextResetTime = 100_000L,
            nextAddTime = 120_000L,
            dwm = "Daily"
        )

        val roundTrip = entity.toDomainModel().toEntity()
        assertThat(roundTrip).isEqualTo(entity)
    }
}
