package de.hauptmann.sunshinealarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SleepClimateTest {
    private val bed = 23 * 60
    private val wake = 7 * 60

    @Test fun `night spans midnight`() {
        val n = nightOf(LocalDate.of(2026, 11, 8), bed, wake)
        assertEquals(LocalDateTime.of(2026, 11, 8, 23, 0), n.start)
        assertEquals(LocalDateTime.of(2026, 11, 9, 7, 0), n.end)
    }

    @Test fun `last night in the afternoon is the one that ended this morning`() {
        val n = lastNight(LocalDateTime.of(2026, 11, 9, 15, 0), bed, wake)
        assertEquals(LocalDateTime.of(2026, 11, 8, 23, 0), n.start)
    }

    @Test fun `last night after bedtime is the running one`() {
        val n = lastNight(LocalDateTime.of(2026, 11, 9, 23, 30), bed, wake)
        assertEquals(LocalDateTime.of(2026, 11, 9, 23, 0), n.start)
    }

    @Test fun `nap-like window within one day`() {
        val n = nightOf(LocalDate.of(2026, 11, 8), 1 * 60, 9 * 60)
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), n.end)
    }

    @Test fun `nights between covers a week`() {
        val z = ZoneOffset.UTC
        val from = LocalDateTime.of(2026, 11, 1, 12, 0).toInstant(z).toEpochMilli()
        val to = LocalDateTime.of(2026, 11, 8, 12, 0).toInstant(z).toEpochMilli()
        assertEquals(7, nightsBetween(from, to, bed, wake, z).size)
    }

    @Test fun `targets and verdicts`() {
        assertTrue(Target.TEMPERATURE.contains(17.5))
        assertEquals(Verdict.LOW, Target.TEMPERATURE.verdict(15.7))
        assertEquals(Verdict.HIGH, Target.HUMIDITY.verdict(75.0))
        assertEquals(Verdict.OK, Target.NOISE.verdict(28.0))
        assertFalse(Target.LIGHT.contains(7.6))
    }

    @Test fun `share in range`() {
        val s = summarize(listOf(15.0, 16.5, 17.0, 20.0), Target.TEMPERATURE)!!
        assertEquals(0.5, s.shareInRange, 1e-9)
    }
}
