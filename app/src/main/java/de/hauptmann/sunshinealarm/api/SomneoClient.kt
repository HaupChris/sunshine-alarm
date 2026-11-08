package de.hauptmann.sunshinealarm.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

class SomneoException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Client for the local HTTPS API of a Philips Somneo (HF367x).
 *
 * The device runs a tiny web server, so all requests are serialized through
 * one mutex and one connection, with a short pause in between (as pysomneo does).
 */
class SomneoClient(
    private val baseUrl: HttpUrl,
    private val http: OkHttpClient,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    // ---- reads ----

    suspend fun sensors(): SensorPayload = get("wusrd", SensorPayload.serializer())
    suspend fun status(): StatusPayload = get("wusts", StatusPayload.serializer())
    suspend fun light(): LightPayload = get("wulgt", LightPayload.serializer())
    suspend fun deviceTime(): DeviceTimePayload = get("wutim", DeviceTimePayload.serializer())

    suspend fun alarms(): List<Alarm> = combineAlarms(
        get("wualm/aenvs", AlarmEnabledPayload.serializer()),
        get("wualm/aalms", AlarmTimesPayload.serializer()),
    )

    /** Names of light/sound themes, endpoint e.g. "files/lightthemes" */
    suspend fun themes(endpoint: String): Map<Int, String> =
        getRaw(endpoint).jsonObject.mapNotNull { (key, value) ->
            val name = (value.jsonObject["name"] as? JsonPrimitive)?.content
            key.toIntOrNull()?.let { it to (name ?: key) }
        }.toMap()

    /** Friendly name from the UPnP description, e.g. "Wake-up Strahler" */
    suspend fun friendlyName(): String? {
        val url = baseUrl.resolve("/upnp/description.xml") ?: return null
        val xml = executeText(Request.Builder().url(url).get().build())
        return Regex("<friendlyName>(.*?)</friendlyName>").find(xml)?.groupValues?.get(1)
    }

    // ---- writes ----

    suspend fun setLight(on: Boolean, level: Int? = null) = modifyLight {
        put("onoff", JsonPrimitive(on))
        put("ngtlt", JsonPrimitive(false))
        level?.let { put("ltlvl", JsonPrimitive(it.coerceIn(1, 25))) }
    }

    suspend fun setLightLevel(level: Int) = modifyLight {
        put("ltlvl", JsonPrimitive(level.coerceIn(1, 25)))
    }

    suspend fun setNightLight(on: Boolean) = modifyLight {
        put("onoff", JsonPrimitive(false))
        put("ngtlt", JsonPrimitive(on))
    }

    suspend fun setAlarmEnabled(slot: Int, enabled: Boolean) = put(
        "wualm/prfwu",
        mapOf("prfnr" to JsonPrimitive(slot), "prfvs" to JsonPrimitive(true), "prfen" to JsonPrimitive(enabled)),
    )

    suspend fun setAlarmTime(slot: Int, hour: Int, minute: Int, dayMask: Int) = put(
        "wualm/prfwu",
        mapOf(
            "prfnr" to JsonPrimitive(slot),
            "prfvs" to JsonPrimitive(true),
            "almhr" to JsonPrimitive(hour),
            "almmn" to JsonPrimitive(minute),
            "daynm" to JsonPrimitive(dayMask),
        ),
    )

    /** Selects the slot on the device, then reads its full wake-up profile */
    suspend fun alarmSettings(slot: Int): AlarmSettings {
        put("wualm", mapOf("prfnr" to JsonPrimitive(slot)))
        return AlarmSettings.from(get("wualm/prfwu", AlarmDetailPayload.serializer()))
    }

    /*
     * Never send ayear/amnth/alday = 0: the device then shifts the alarm time by
     * +6 h 28 min on every save (verified on the HF367x). The date fields are only
     * sent for a real date.
     */
    suspend fun saveAlarm(a: AlarmSettings) = put(
        "wualm/prfwu",
        mapOf(
            "prfnr" to JsonPrimitive(a.slot),
            "prfvs" to JsonPrimitive(true),
            "prfen" to JsonPrimitive(a.enabled),
            "pname" to JsonPrimitive(a.name.take(AlarmSettings.MAX_NAME_LENGTH)),
            "almhr" to JsonPrimitive(a.hour),
            "almmn" to JsonPrimitive(a.minute),
            "daynm" to JsonPrimitive(a.dayMask),
            "durat" to JsonPrimitive(a.sunriseMinutes),
            "curve" to JsonPrimitive(a.sunriseLevel.coerceIn(1, 25)),
            "ctype" to JsonPrimitive(a.lightTheme),
            "snddv" to JsonPrimitive(a.soundSource.code),
            "sndch" to JsonPrimitive(if (a.soundSource == SoundSource.OFF) " " else a.soundChannel.toString()),
            "sndlv" to JsonPrimitive(a.volume.coerceIn(1, 25)),
        ) + (a.date?.let {
            mapOf("ayear" to JsonPrimitive(it.year), "amnth" to JsonPrimitive(it.monthValue), "alday" to JsonPrimitive(it.dayOfMonth))
        } ?: emptyMap()),
    )

    /** Frees a slot; payload mirrors pysomneo's remove_alarm (factory values, hidden) */
    suspend fun removeAlarm(slot: Int) = put(
        "wualm/prfwu",
        mapOf(
            "prfnr" to JsonPrimitive(slot), "prfen" to JsonPrimitive(false), "prfvs" to JsonPrimitive(false),
            "pname" to JsonPrimitive(""),
            "almhr" to JsonPrimitive(7), "almmn" to JsonPrimitive(30), "pwrsz" to JsonPrimitive(0),
            "pszhr" to JsonPrimitive(0), "pszmn" to JsonPrimitive(0), "ctype" to JsonPrimitive(0),
            "curve" to JsonPrimitive(20), "durat" to JsonPrimitive(30), "daynm" to JsonPrimitive(254),
            "snddv" to JsonPrimitive("wus"), "sndch" to JsonPrimitive("1"), "sndlv" to JsonPrimitive(12),
        ),
    )

    // ---- light preview ----

    /** Shows a light theme at a brightness temporarily, as the Philips app does while editing */
    suspend fun lightPreview(theme: Int, level: Int) = put(
        "wulgt",
        mapOf(
            "onoff" to JsonPrimitive(true), "tempy" to JsonPrimitive(true), "ngtlt" to JsonPrimitive(false),
            "ltlvl" to JsonPrimitive(level.coerceIn(1, 25)), "ctype" to JsonPrimitive(theme),
        ),
    )

    /** Ends a light preview and puts the light back the way it was */
    suspend fun endLightPreview(before: LightPayload) {
        put(
            "wulgt",
            mapOf(
                "onoff" to JsonPrimitive(before.onoff), "tempy" to JsonPrimitive(false),
                "ngtlt" to JsonPrimitive(before.ngtlt), "ctype" to JsonPrimitive(before.ctype),
            ),
        )
        put("wulgt", mapOf("ltlvl" to JsonPrimitive(before.ltlvl)))
    }

    // ---- player: radio, AUX and previews ----

    suspend fun player(): PlayerPayload = get("wuply", PlayerPayload.serializer())

    /** FM presets 1..5 with their frequency, e.g. 1 -> "91.30" */
    suspend fun radioPresets(): Map<Int, String> =
        getRaw("wufmp/00").jsonObject.mapNotNull { (k, v) ->
            val freq = (v as? JsonPrimitive)?.content.orEmpty()
            k.toIntOrNull()?.takeIf { it in 1..5 && freq.isNotBlank() }?.let { it to freq }
        }.toMap()

    /**
     * Plays a sound briefly as preview (tempy = temporary). Switching it off makes
     * the device restore its previous player state by itself.
     * source: "wus" wake-up sound, "dus" dusk sound, "fmr" radio preset.
     */
    suspend fun preview(source: String, channel: Int, volume: Int) = put(
        "wuply",
        mapOf(
            "onoff" to JsonPrimitive(true), "tempy" to JsonPrimitive(true),
            "sdvol" to JsonPrimitive(volume.coerceIn(1, 25)),
            "snddv" to JsonPrimitive(source), "sndch" to JsonPrimitive(channel.toString()),
        ),
    )

    suspend fun stopPreview() = put("wuply", mapOf("onoff" to JsonPrimitive(false), "tempy" to JsonPrimitive(false)))

    suspend fun playRadio(preset: Int, volume: Int) = put(
        "wuply",
        mapOf(
            "onoff" to JsonPrimitive(true), "tempy" to JsonPrimitive(false), "sdvol" to JsonPrimitive(volume.coerceIn(1, 25)),
            "snddv" to JsonPrimitive("fmr"), "sndch" to JsonPrimitive(preset.toString()),
        ),
    )

    suspend fun playAux(volume: Int) = put(
        "wuply",
        mapOf(
            "onoff" to JsonPrimitive(true), "tempy" to JsonPrimitive(false), "sdvol" to JsonPrimitive(volume.coerceIn(1, 25)),
            "snddv" to JsonPrimitive("aux"), "sndch" to JsonPrimitive("1"),
        ),
    )

    /** Stores a frequency (MHz, e.g. 95.4) on preset 1..5 */
    suspend fun setPreset(preset: Int, mhz: Double) =
        put("wufmp/00", mapOf(preset.toString() to JsonPrimitive(formatMhz(mhz))))

    /**
     * Seeks the next station up or down from the preset that plays right now.
     * The device stores the result on that preset by itself. Returns the new frequency.
     */
    suspend fun seek(up: Boolean): Double? {
        put("wufmr", mapOf("fmcmd" to JsonPrimitive(if (up) "seekup" else "seekdown")))
        repeat(15) {
            kotlinx.coroutines.delay(700)
            val r = getRaw("wufmr").jsonObject
            if ((r["fmcmd"] as? JsonPrimitive)?.content == "set") {
                return (r["fmfrq"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            }
        }
        return null
    }

    suspend fun stopPlayer() = put("wuply", mapOf("onoff" to JsonPrimitive(false)))

    suspend fun setPlayerVolume(volume: Int) = put("wuply", mapOf("sdvol" to JsonPrimitive(volume.coerceIn(1, 25))))

    // ---- sunset ----

    suspend fun sunset(): SunsetSettings = SunsetSettings.from(get("wudsk", SunsetPayload.serializer()))

    /** Remaining seconds of a running sunset */
    suspend fun sunsetRemaining(): Int = get("wutmr", TimerPayload.serializer()).let { it.dskmn * 60 + it.dsksc }

    /** The device ignores changes while the sunset runs, so it is stopped first (as pysomneo does) */
    suspend fun saveSunset(s: SunsetSettings) {
        if (sunset().running) setSunsetRunning(false)
        put(
            "wudsk",
            mapOf(
                "onoff" to JsonPrimitive(false),
                "durat" to JsonPrimitive(s.minutes),
                "curve" to JsonPrimitive(s.level.coerceIn(1, 25)),
                "ctype" to JsonPrimitive(s.lightTheme),
                "snddv" to JsonPrimitive(s.sound.code),
                "sndch" to JsonPrimitive(if (s.sound == SunsetSound.OFF) "" else s.soundChannel.toString()),
                "sndlv" to JsonPrimitive(s.volume.coerceIn(1, 25)),
            ),
        )
    }

    suspend fun setSunsetRunning(on: Boolean) = put("wudsk", mapOf("onoff" to JsonPrimitive(on)))

    suspend fun snooze() = put("wualm/alctr", mapOf("tapsz" to JsonPrimitive(true)))
    suspend fun dismiss() = put("wualm/alctr", mapOf("disms" to JsonPrimitive(true)))

    // ---- plumbing ----

    /** The device expects the full light object back, minus the read-only curve */
    private suspend fun modifyLight(change: MutableMap<String, JsonElement>.() -> Unit) {
        val current = getRaw("wulgt").jsonObject.toMutableMap()
        current.remove("wucrv")
        // A finished preview leaves tempy=true behind; normal commands must clear it
        current["tempy"] = JsonPrimitive(false)
        current.change()
        put("wulgt", current)
    }

    private suspend fun <T> get(path: String, strategy: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(strategy, getRaw(path))

    private suspend fun getRaw(path: String): JsonElement =
        execute(Request.Builder().url(url(path)).get().build())

    private suspend fun put(path: String, body: Map<String, JsonElement>): JsonElement {
        val payload = JsonObject(body).toString().toRequestBody(JSON_TYPE)
        return execute(Request.Builder().url(url(path)).put(payload).build())
    }

    private fun url(path: String) = baseUrl.newBuilder().addPathSegments(path).build()

    private suspend fun execute(request: Request): JsonElement = json.parseToJsonElement(executeText(request))

    private suspend fun executeText(request: Request): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw SomneoException("Der Wecker hat den Befehl abgelehnt (Fehler ${response.code})")
                    }
                    body
                }
            } catch (e: SomneoException) {
                throw e
            } catch (e: IOException) {
                val certChanged = generateSequence<Throwable>(e) { it.cause }.any { it is CertificateChangedException }
                throw SomneoException(
                    if (certChanged) "Das Zertifikat des Weckers hat sich geändert. Unter Verbindung kannst du es zurücksetzen."
                    else "Der Wecker antwortet nicht. Ist das Handy im selben WLAN?",
                    e,
                )
            } finally {
                delay(REQUEST_GAP_MS)
            }
        }
    }

    companion object {
        fun formatMhz(mhz: Double) = String.format(java.util.Locale.ROOT, "%.2f", mhz)
        const val FM_MIN = 87.5
        const val FM_MAX = 108.0

        private val JSON_TYPE = "application/json".toMediaType()
        private const val REQUEST_GAP_MS = 100L

        fun create(host: String, pinStore: PinStore): SomneoClient {
            val trustManager = TrustOnFirstUseManager(pinStore)
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), SecureRandom()) }
            val http = OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trustManager)
                // The certificate is not issued for the IP; identity is checked by the pin instead
                .hostnameVerifier { _, _ -> true }
                .connectionPool(ConnectionPool(1, 30, TimeUnit.SECONDS))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            return SomneoClient("https://$host/di/v1/products/1/".toHttpUrl(), http)
        }
    }
}
