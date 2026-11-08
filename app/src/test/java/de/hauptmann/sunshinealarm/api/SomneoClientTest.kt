package de.hauptmann.sunshinealarm.api

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs against the real responses captured from the HF367x (JSON files in samples/) */
class SomneoClientTest {
    private val server = MockWebServer()
    private lateinit var client: SomneoClient

    private fun sample(name: String) =
        javaClass.classLoader!!.getResource("$name.json")!!.readText()

    private fun enqueue(name: String) = server.enqueue(MockResponse().setBody(sample(name)))

    @Before fun setUp() {
        server.start()
        client = SomneoClient(server.url("/di/v1/products/1/"), OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `parses sensors`() = runTest {
        enqueue("wusrd")
        val s = client.sensors()
        assertEquals(15.7, s.mstmp, 0.001)
        assertEquals(74.1, s.msrhu, 0.001)
        assertEquals(31, s.mssnd)
        assertEquals("/di/v1/products/1/wusrd", server.takeRequest().path)
    }

    @Test fun `parses status`() = runTest {
        enqueue("wusts")
        assertEquals(DeviceState.LIGHT_ON, DeviceState.of(client.status().wusts))
    }

    @Test fun `combines 16 alarm slots`() = runTest {
        enqueue("wualm_aenvs")
        enqueue("wualm_aalms")
        val alarms = client.alarms()
        assertEquals(16, alarms.size)
        val second = alarms[1]
        assertEquals(2, second.slot)
        assertTrue(second.enabled)
        assertEquals(9, second.hour)
        assertEquals(45, second.minute)
        assertEquals(Weekday.entries.toSet(), second.days)
        assertFalse(alarms[0].enabled)
        assertEquals(2, alarms.count { it.inUse })
    }

    @Test fun `parses themes`() = runTest {
        enqueue("files_lightthemes")
        assertEquals("Sunny day", client.themes("files/lightthemes")[1])
    }

    @Test fun `light write sends full object without curve`() = runTest {
        enqueue("wulgt")
        server.enqueue(MockResponse().setBody(sample("wulgt")))
        client.setLight(on = true, level = 40)
        server.takeRequest() // GET
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        val body = Json.parseToJsonElement(put.body.readUtf8()).jsonObject
        assertTrue(body["onoff"]!!.jsonPrimitive.boolean)
        assertFalse(body["ngtlt"]!!.jsonPrimitive.boolean)
        assertEquals(25, body["ltlvl"]!!.jsonPrimitive.int) // clamped
        assertFalse("wucrv" in body)
    }

    @Test fun `save alarm sends name and date`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        val a = AlarmSettings.newAlarm(5).copy(name = "Ein sehr langer Name fuer Wecker", dayMask = 0, date = java.time.LocalDate.of(2026, 11, 9))
        client.saveAlarm(a)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("Ein sehr langer ", body["pname"]!!.jsonPrimitive.content)
        assertEquals(2026, body["ayear"]!!.jsonPrimitive.int)
        assertEquals(11, body["amnth"]!!.jsonPrimitive.int)
        assertEquals(9, body["alday"]!!.jsonPrimitive.int)
        assertEquals(0, body["daynm"]!!.jsonPrimitive.int)
    }

    /** Regression: date fields = 0 make the device shift the alarm by +6 h 28 min */
    @Test fun `save alarm without date sends no date fields`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        client.saveAlarm(AlarmSettings.newAlarm(1).copy(hour = 14, minute = 25))
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse("ayear" in body || "amnth" in body || "alday" in body)
        assertEquals(14, body["almhr"]!!.jsonPrimitive.int)
    }

    @Test fun `remove alarm sends no date fields`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        client.removeAlarm(3)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse("ayear" in body)
    }

    @Test fun `weekday mask roundtrip`() {
        assertEquals(Weekday.WORKDAYS, Weekday.toMask(setOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI)))
        assertEquals(setOf(Weekday.SAT, Weekday.SUN), Weekday.fromMask(Weekday.WEEKEND))
    }
}
