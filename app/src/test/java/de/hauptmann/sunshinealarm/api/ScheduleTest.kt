package de.hauptmann.sunshinealarm.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    // Sunday 8 Nov 2026, 20:00
    private val now = LocalDateTime.of(2026, 11, 8, 20, 0)

    @Test fun `daily alarm rings tomorrow morning`() =
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 45), nextOccurrence(9, 45, Weekday.EVERY_DAY, now))

    @Test fun `later today if time not passed yet`() =
        assertEquals(LocalDateTime.of(2026, 11, 8, 22, 0), nextOccurrence(22, 0, Weekday.EVERY_DAY, now))

    @Test fun `workdays skip the weekend`() =
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), nextOccurrence(7, 0, Weekday.WORKDAYS, now.minusDays(1)))

    @Test fun `weekend alarm on friday evening goes to saturday`() =
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 0), nextOccurrence(8, 0, Weekday.WEEKEND, LocalDateTime.of(2026, 11, 13, 20, 0)))

    @Test fun `once means next occurrence`() =
        assertEquals(LocalDateTime.of(2026, 11, 9, 6, 30), nextOccurrence(6, 30, 0, now))

    @Test fun `alarm settings parse from device profile`() {
        val s = AlarmSettings.from(
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(
                AlarmDetailPayload.serializer(),
                javaClass.classLoader!!.getResource("wualm_prfwu.json")!!.readText(),
            ),
        )
        assertEquals(3, s.lightTheme)
        assertEquals(10, s.sunriseMinutes)
        assertEquals(SoundSource.WAKE_UP, s.soundSource)
        assertEquals(1, s.soundChannel)
    }

    @Test fun `no day selected never rings within a week except once`() =
        assertNull(nextOccurrence(7, 0, 1, now)) // bit 1 is unused by the device
}
