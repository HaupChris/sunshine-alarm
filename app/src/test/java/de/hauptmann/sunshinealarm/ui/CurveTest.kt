package de.hauptmann.sunshinealarm.ui

import de.hauptmann.sunshinealarm.api.SomneoClient
import org.junit.Assert.assertEquals
import org.junit.Test

class CurveTest {
    @Test fun `sunrise starts dim and ends at the chosen level`() {
        assertEquals(1, curveLevel(20, 0f, descending = false))
        assertEquals(20, curveLevel(20, 1f, descending = false))
    }

    @Test fun `sunset starts at the chosen level and fades out`() {
        assertEquals(18, curveLevel(18, 0f, descending = true))
        assertEquals(1, curveLevel(18, 1f, descending = true))
    }

    @Test fun `frequencies are sent with a dot and two decimals`() {
        assertEquals("95.40", SomneoClient.formatMhz(95.4))
    }
}
