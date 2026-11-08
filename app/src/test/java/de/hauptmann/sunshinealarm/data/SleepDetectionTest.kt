package de.hauptmann.sunshinealarm.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class SleepDetectionTest {
    private val z = ZoneOffset.UTC
    private val night = LocalDate.of(2026, 11, 8)
    private fun at(day: Int, h: Int, m: Int) = LocalDateTime.of(2026, 11, day, h, m).toInstant(z).toEpochMilli()

    /** One reading every 15 min from 20:00 to 10:00 with the given lux per time */
    private fun series(lux: (Long) -> Double): List<SensorReading> =
        generateSequence(at(8, 20, 0)) { it + 15 * 60_000 }.takeWhile { it <= at(9, 10, 0) }
            .map { SensorReading(timestamp = it, lux = lux(it), temperatureC = 17.0, humidityPercent = 50.0, noiseDb = 28) }.toList()

    @Test fun `detects lights off until it gets bright`() {
        val r = series { t -> if (t in at(8, 23, 30) until at(9, 6, 45)) 0.4 else 150.0 }
        assertEquals(at(8, 23, 30) to at(9, 6, 45), detectSleep(r, night, z))
    }

    @Test fun `short dark phases are ignored, the longest wins`() {
        val r = series { t ->
            when (t) {
                in at(8, 21, 0) until at(8, 21, 45) -> 0.5 // dozed off on the sofa
                in at(9, 0, 15) until at(9, 7, 0) -> 0.5
                else -> 80.0
            }
        }
        assertEquals(at(9, 0, 15) to at(9, 7, 0), detectSleep(r, night, z))
    }

    @Test fun `a short gap in data is bridged`() {
        val r = series { t -> if (t in at(8, 23, 0) until at(9, 7, 0)) 0.5 else 120.0 }
            .filterNot { it.timestamp in at(9, 2, 0)..at(9, 2, 30) } // phone lost Wi-Fi for 30 min
        assertEquals(at(8, 23, 0) to at(9, 7, 0), detectSleep(r, night, z))
    }

    @Test fun `no long darkness means no detection`() {
        val r = series { t -> if (t in at(9, 1, 0) until at(9, 2, 0)) 0.5 else 50.0 }
        assertNull(detectSleep(r, night, z))
    }
}
