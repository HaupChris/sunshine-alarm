package de.hauptmann.sunshinealarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hauptmann.sunshinealarm.SunshineApp
import de.hauptmann.sunshinealarm.api.AlarmSettings
import de.hauptmann.sunshinealarm.api.Discovery
import de.hauptmann.sunshinealarm.api.FoundDevice
import de.hauptmann.sunshinealarm.api.LightPayload
import de.hauptmann.sunshinealarm.api.PlayerPayload
import de.hauptmann.sunshinealarm.api.SomneoClient
import de.hauptmann.sunshinealarm.api.SomneoException
import de.hauptmann.sunshinealarm.api.SoundSource
import de.hauptmann.sunshinealarm.api.SunsetSettings
import de.hauptmann.sunshinealarm.api.SunsetSound
import de.hauptmann.sunshinealarm.api.nextOccurrence
import de.hauptmann.sunshinealarm.data.SensorReading
import de.hauptmann.sunshinealarm.data.Target
import de.hauptmann.sunshinealarm.data.Themes
import de.hauptmann.sunshinealarm.data.toSettings
import de.hauptmann.sunshinealarm.nap.NapPlan
import de.hauptmann.sunshinealarm.nap.NapState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

enum class Tab { HOME, ALARMS, CONTROL, HISTORY }
enum class HistoryView { NIGHTS, CLIMATE }
enum class HistoryRange(val label: String, val hours: Long) { DAY("24 h", 24), WEEK("7 Tage", 24 * 7), MONTH("30 Tage", 24 * 30) }
enum class Metric(val label: String, val unit: String, val target: Target) {
    TEMPERATURE("Temperatur", "°C", Target.TEMPERATURE),
    HUMIDITY("Feuchte", "%", Target.HUMIDITY),
    LIGHT("Licht", "lx", Target.LIGHT),
    NOISE("Geräusch", "dB", Target.NOISE);

    fun of(r: SensorReading): Double = when (this) {
        TEMPERATURE -> r.temperatureC
        HUMIDITY -> r.humidityPercent
        LIGHT -> r.lux
        NOISE -> r.noiseDb.toDouble()
    }
}

sealed interface Connection {
    data object Unknown : Connection
    data object Loading : Connection
    data object Ok : Connection
    data class Failed(val message: String) : Connection
}

/** A short message at the bottom, optionally with one action ("Rückgängig", "Erneut versuchen") */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null, val id: Long = System.nanoTime())

/** Keys of things that are being sent to the device right now, for feedback at the element */
object Pending {
    const val LIGHT = "light"
    const val NIGHT_LIGHT = "nightlight"
    const val PLAYER = "player"
    const val SUNSET = "sunset"
    const val SAVE = "save"
    fun alarm(slot: Int) = "alarm:$slot"
}

data class Editing(val settings: AlarmSettings, val original: AlarmSettings, val isNew: Boolean) {
    val dirty get() = settings != original
}

data class SunsetDraft(val settings: SunsetSettings, val original: SunsetSettings) {
    val dirty get() = settings != original
}

data class RadioPreset(val number: Int, val mhz: Double?, val name: String?) {
    val label: String get() = name ?: "Sender $number"
    val frequency: String? get() = mhz?.let { String.format(java.util.Locale.GERMAN, "%.1f MHz", it) }
}

/** Editing one radio preset; frequency changes play right away so you can listen */
data class PresetDraft(
    val number: Int,
    val name: String,
    val originalName: String,
    val mhz: Double,
    val originalMhz: Double,
    val playerWasOn: Boolean,
    val seeking: Boolean = false,
) {
    val dirty get() = name != originalName || mhz != originalMhz
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SunshineApp
    private val repo = app.repository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val alarms = repo.alarms.state(emptyList())
    val latest = repo.latestReading.state(null)
    val readingCount = repo.readingCount.state(0)
    val nights = repo.recentNights(14).state(emptyList())

    /** Ticks every 30 s so countdowns and the horizon move */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000)
        }
    }.state(LocalDateTime.now())

    /** Ticks every second, for the nap countdown */
    val clock: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.state(System.currentTimeMillis())

    private val _host = MutableStateFlow(app.settings.host)
    val host = _host.asStateFlow()
    val fingerprint get() = app.settings.fingerprint

    private val _deviceName = MutableStateFlow(app.settings.deviceName)
    val deviceName = _deviceName.asStateFlow()

    private val _connection = MutableStateFlow<Connection>(Connection.Unknown)
    val connection = _connection.asStateFlow()

    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending = _pending.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message = _message.asStateFlow()

    private val _light = MutableStateFlow<LightPayload?>(null)
    val light = _light.asStateFlow()

    private val _player = MutableStateFlow<PlayerPayload?>(null)
    val player = _player.asStateFlow()
    private val _presets = MutableStateFlow<List<RadioPreset>>(emptyList())
    val presets = _presets.asStateFlow()
    private val _presetDraft = MutableStateFlow<PresetDraft?>(null)
    val presetDraft = _presetDraft.asStateFlow()

    private val _sunset = MutableStateFlow<SunsetSettings?>(null)
    val sunset = _sunset.asStateFlow()
    private val _sunsetRemaining = MutableStateFlow(0)
    val sunsetRemaining = _sunsetRemaining.asStateFlow()
    private var sunsetPoll: Job? = null

    private val _themes = MutableStateFlow<Themes?>(null)
    val themes = _themes.asStateFlow()

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab = _tab.asStateFlow()

    private val _showConnection = MutableStateFlow(app.settings.host == null)
    val showConnection = _showConnection.asStateFlow()

    private val _editing = MutableStateFlow<Editing?>(null)
    val editing = _editing.asStateFlow()
    private val _napDraft = MutableStateFlow<NapPlan?>(null)
    val napDraft = _napDraft.asStateFlow()
    val napState: StateFlow<NapState> = app.nap.state
    private val _sunsetDraft = MutableStateFlow<SunsetDraft?>(null)
    val sunsetDraft = _sunsetDraft.asStateFlow()

    /** One preview switch per editor: shows light and plays sound on the device */
    private val _preview = MutableStateFlow(false)
    val preview = _preview.asStateFlow()
    private var lightBefore: LightPayload? = null
    /** Light level to show while dragging the bar in a curve; sent conflated, the device needs ~0.5 s per request */
    private val scrubTarget = MutableStateFlow<Pair<Int, Int>?>(null)

    private val _found = MutableStateFlow<List<FoundDevice>?>(null)
    val found = _found.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _historyView = MutableStateFlow(HistoryView.NIGHTS)
    val historyView = _historyView.asStateFlow()
    private val _range = MutableStateFlow(HistoryRange.DAY)
    val range = _range.asStateFlow()
    private val _metric = MutableStateFlow(Metric.TEMPERATURE)
    val metric = _metric.asStateFlow()

    private val _sleepWindow = MutableStateFlow(app.settings.bedtimeMinutes to app.settings.wakeMinutes)
    val sleepWindow = _sleepWindow.asStateFlow()

    /** Hints for gestures disappear once the gesture was used */
    private val _hints = MutableStateFlow(app.settings.seenHints)
    val hints = _hints.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<SensorReading>> = _range.flatMapLatest { r ->
        repo.readingsSince(System.currentTimeMillis() - r.hours * 3_600_000)
    }.state(emptyList())

    init {
        viewModelScope.launch {
            scrubTarget.filterNotNull().distinctUntilChanged().conflate().collect { (theme, level) ->
                if (_preview.value) {
                    try {
                        repo.client().lightPreview(theme, level)
                    } catch (e: SomneoException) {
                        _message.value = UiMessage(e.message ?: "Vorschau nicht möglich")
                    }
                }
            }
        }
    }

    val homeState = combine(alarms, now) { list, t ->
        list.filter { it.inUse && it.enabled }
            .mapNotNull { a -> nextOccurrence(a.hour, a.minute, a.dayMask, t)?.let { a to it } }
            .minByOrNull { it.second }
    }.state(null)

    // ---- navigation and small settings ----

    fun select(tab: Tab) { _tab.value = tab }
    fun openConnection() { _showConnection.value = true }
    fun closeConnection() { if (_host.value != null) _showConnection.value = false }
    fun setHistoryView(v: HistoryView) { _historyView.value = v }
    fun setRange(r: HistoryRange) { _range.value = r }
    fun setMetric(m: Metric) { _metric.value = m }
    fun dismissMessage() { _message.value = null }

    fun hintSeen(key: String) {
        if (key in _hints.value) return
        app.settings.markHintSeen(key)
        _hints.value = app.settings.seenHints
    }

    fun setSleepWindow(bedtime: Int, wake: Int) {
        app.settings.bedtimeMinutes = bedtime
        app.settings.wakeMinutes = wake
        _sleepWindow.value = bedtime to wake
    }

    // ---- connection ----

    fun connect(host: String) {
        app.settings.host = host.trim()
        _host.value = app.settings.host
        _deviceName.value = app.settings.deviceName
        _showConnection.value = false
        refresh()
    }

    fun forgetCertificate() { app.settings.fingerprint = null }

    fun search() = viewModelScope.launch {
        _searching.value = true
        _found.value = runCatching { Discovery.search(app) }.getOrDefault(emptyList())
        _searching.value = false
    }

    fun refresh(): Job = device(retry = ::refresh) {
        repo.recordSensors()
        _light.value = repo.light()
        _player.value = repo.player()
        applySunset(repo.sunset())
        repo.syncAlarms()
        if (_themes.value == null) _themes.value = repo.themes()
        if (_presets.value.isEmpty()) loadPresets()
        repo.updateSleepNights()
        if (_deviceName.value == null) {
            repo.client().friendlyName()?.let {
                app.settings.deviceName = it
                _deviceName.value = it
            }
        }
    }

    // ---- lamp ----

    fun setLight(on: Boolean): Job = device(Pending.LIGHT, retry = { setLight(on) }) {
        _light.update { it?.copy(onoff = on, ngtlt = false) }
        repo.client().setLight(on)
        _light.value = repo.light()
    }

    fun previewLightLevel(level: Int) { _light.update { it?.copy(ltlvl = level) } }

    fun commitLightLevel(): Job = device(Pending.LIGHT, retry = ::commitLightLevel) {
        _light.value?.let { repo.client().setLightLevel(it.ltlvl) }
        _light.value = repo.light()
    }

    fun setNightLight(on: Boolean): Job = device(Pending.NIGHT_LIGHT, retry = { setNightLight(on) }) {
        _light.update { it?.copy(ngtlt = on, onoff = false) }
        repo.client().setNightLight(on)
        _light.value = repo.light()
    }

    // ---- player: radio and AUX ----

    /** Playback on/off; switching on resumes the last source */
    fun setPlayer(on: Boolean) {
        val p = _player.value ?: return
        if (!on) {
            stopPlayer()
            return
        }
        if (p.snddv == "aux") playAux() else playRadio(p.sndch.toIntOrNull()?.takeIf { it in 1..5 } ?: 1)
    }

    fun playRadio(preset: Int): Job = device(Pending.PLAYER, retry = { playRadio(preset) }) {
        _player.update { it?.copy(onoff = true, tempy = false, snddv = "fmr", sndch = preset.toString()) }
        repo.client().playRadio(preset, _player.value?.sdvol ?: 8)
        _player.value = repo.player()
    }

    fun playAux(): Job = device(Pending.PLAYER, retry = ::playAux) {
        _player.update { it?.copy(onoff = true, tempy = false, snddv = "aux") }
        repo.client().playAux(_player.value?.sdvol ?: 8)
        _player.value = repo.player()
    }

    fun stopPlayer(): Job = device(Pending.PLAYER, retry = ::stopPlayer) {
        _player.update { it?.copy(onoff = false) }
        repo.client().stopPlayer()
        _player.value = repo.player()
    }

    fun previewPlayerVolume(v: Int) { _player.update { it?.copy(sdvol = v) } }

    fun commitPlayerVolume(): Job = device(Pending.PLAYER, retry = ::commitPlayerVolume) {
        _player.value?.let { repo.client().setPlayerVolume(it.sdvol) }
    }

    // ---- radio presets ----

    private suspend fun loadPresets() {
        val freqs = repo.radioPresets()
        _presets.value = (1..5).map { n -> RadioPreset(n, freqs[n]?.toDoubleOrNull(), app.settings.presetName(n)) }
    }

    fun openPreset(n: Int) {
        val p = _presets.value.firstOrNull { it.number == n } ?: return
        val mhz = p.mhz ?: SomneoClient.FM_MIN
        val wasOn = _player.value?.let { it.onoff && !it.tempy } == true
        _presetDraft.value = PresetDraft(n, p.name.orEmpty(), p.name.orEmpty(), mhz, mhz, wasOn)
        playRadio(n)
    }

    fun renamePreset(name: String) { _presetDraft.update { it?.copy(name = name.take(24)) } }

    /** Moves the preset by [steps] x 0.1 MHz and plays it */
    fun stepPreset(steps: Int) {
        val d = _presetDraft.value ?: return
        val mhz = (Math.round((d.mhz + steps * 0.1) * 10) / 10.0).coerceIn(SomneoClient.FM_MIN, SomneoClient.FM_MAX)
        _presetDraft.value = d.copy(mhz = mhz)
        device(Pending.PLAYER) {
            repo.client().setPreset(d.number, mhz)
            repo.client().playRadio(d.number, _player.value?.sdvol ?: 8)
        }
    }

    fun seekPreset(up: Boolean) {
        val d = _presetDraft.value ?: return
        _presetDraft.value = d.copy(seeking = true)
        device(Pending.PLAYER, onFail = { _presetDraft.update { it?.copy(seeking = false) } }) {
            repo.client().playRadio(d.number, _player.value?.sdvol ?: 8)
            val found = repo.client().seek(up)
            _presetDraft.update { it?.copy(mhz = found ?: d.mhz, seeking = false) }
        }
    }

    fun savePreset() {
        val d = _presetDraft.value ?: return
        app.settings.setPresetName(d.number, d.name)
        _presetDraft.value = null
        device {
            if (!d.playerWasOn) repo.client().stopPlayer()
            loadPresets()
            _player.value = repo.player()
        }
    }

    /** Leaves the editor without keeping the frequency change */
    fun discardPreset() {
        val d = _presetDraft.value ?: return
        _presetDraft.value = null
        device {
            if (d.mhz != d.originalMhz) repo.client().setPreset(d.number, d.originalMhz)
            if (!d.playerWasOn) repo.client().stopPlayer()
            loadPresets()
            _player.value = repo.player()
        }
    }

    // ---- preview in the editors: light and sound together ----

    fun setPreview(on: Boolean) {
        _preview.value = on
        if (on) {
            lightBefore = _light.value
            showCurrentLight()
            playCurrentPreview()
        } else {
            stopPreviews()
        }
    }

    /** Plays the sound selected in the open editor, if the preview is on */
    fun playCurrentPreview() {
        if (!_preview.value) return
        val e = _editing.value?.settings
        val s = _sunsetDraft.value?.settings
        val n = _napDraft.value
        val (source, channel, volume) = when {
            n != null -> if (n.withSound) Triple("wus", n.channel, n.volume) else return stopSound()
            e != null -> when (e.soundSource) {
                SoundSource.WAKE_UP -> Triple("wus", e.soundChannel, e.volume)
                SoundSource.RADIO -> Triple("fmr", e.soundChannel, e.volume)
                SoundSource.OFF -> return stopSound()
            }
            s != null -> when (s.sound) {
                SunsetSound.NATURE -> Triple("dus", s.soundChannel, s.volume)
                SunsetSound.RADIO -> Triple("fmr", s.soundChannel, s.volume)
                SunsetSound.OFF -> return stopSound()
            }
            else -> return
        }
        device { repo.client().preview(source, channel, volume) }
    }

    /** Shows the light of the open editor at its full (sunrise) or starting (sunset) level */
    fun showCurrentLight() {
        if (!_preview.value) return
        val (theme, level) = _editing.value?.settings?.let { it.lightTheme to it.sunriseLevel }
            ?: _napDraft.value?.takeIf { it.withLight }?.let { it.theme to it.level }
            ?: _sunsetDraft.value?.settings?.let { it.lightTheme to it.level }
            ?: return
        scrubTarget.value = theme to level
    }

    /** Shows the light at a point of the curve while the bar is dragged */
    fun showLightAt(theme: Int, level: Int) {
        if (_preview.value) scrubTarget.value = theme to level.coerceIn(1, 25)
    }

    private fun stopSound() {
        device { repo.client().stopPreview() }
    }

    private fun stopPreviews() {
        _preview.value = false
        scrubTarget.value = null
        val before = lightBefore ?: _light.value
        device {
            repo.client().stopPreview()
            before?.let { repo.client().endLightPreview(it) }
            _light.value = repo.light()
        }
    }

    private fun endPreview() {
        if (_preview.value) stopPreviews()
    }

    // ---- alarms ----

    /** Shows the new state right away and goes back if the device does not take it */
    fun setAlarmEnabled(slot: Int, enabled: Boolean) {
        viewModelScope.launch { repo.setAlarmEnabledLocally(slot, enabled) }
        device(Pending.alarm(slot), onFail = { viewModelScope.launch { repo.setAlarmEnabledLocally(slot, !enabled) } },
            retry = { setAlarmEnabled(slot, enabled) }) {
            repo.setAlarmEnabled(slot, enabled)
        }
    }

    fun edit(slot: Int) {
        val cached = alarms.value.firstOrNull { it.slot == slot } ?: return
        val s = cached.toSettings()
        _editing.value = Editing(s, s, isNew = false)
        // Fresh values from the device, in case they were changed on the light itself
        device {
            val fresh = repo.alarmSettings(slot)
            _editing.update { e -> e?.let { if (it.settings == it.original) Editing(fresh, fresh, false) else it.copy(original = fresh) } }
        }
    }

    fun newAlarm(): Job = device(retry = ::newAlarm) {
        val slot = repo.freeSlot() ?: throw SomneoException("Alle 16 Weckerplätze sind belegt")
        val s = AlarmSettings.newAlarm(slot)
        _editing.value = Editing(s, s, isNew = true)
    }

    fun updateEditing(change: (AlarmSettings) -> AlarmSettings) {
        _editing.update { it?.copy(settings = change(it.settings)) }
    }

    fun closeEditor() {
        endPreview()
        _editing.value = null
    }

    fun saveEditing() {
        val e = _editing.value ?: return
        endPreview()
        device(Pending.SAVE, retry = ::saveEditing) {
            repo.saveAlarm(e.settings)
            _editing.value = null
            _message.value = UiMessage("Wecker ${hhmm(e.settings.hour, e.settings.minute)} gespeichert")
        }
    }

    /** Deletes at once and offers to undo: the app knows all settings and can write them back */
    fun deleteAlarm(slot: Int) {
        viewModelScope.launch {
            val backup = repo.cachedAlarm(slot)?.toSettings() ?: _editing.value?.settings?.takeIf { it.slot == slot }
            if (_editing.value?.settings?.slot == slot) closeEditor()
            device(Pending.alarm(slot), retry = { deleteAlarm(slot) }) {
                repo.removeAlarm(slot)
                _message.value = UiMessage(
                    text = backup?.let { "Wecker ${hhmm(it.hour, it.minute)} gelöscht" } ?: "Wecker gelöscht",
                    actionLabel = backup?.let { "Rückgängig" },
                    action = backup?.let { b -> { device(Pending.alarm(slot)) { repo.saveAlarm(b) } } },
                )
            }
        }
    }

    // ---- nap: driven by the app, see NapController ----

    fun openNap() { _napDraft.value = app.nap.lastPlan }

    fun updateNap(change: (NapPlan) -> NapPlan) { _napDraft.update { it?.let(change) } }

    fun closeNap() {
        endPreview()
        _napDraft.value = null
    }

    fun startNap() {
        val plan = _napDraft.value ?: return
        endPreview()
        app.nap.start(plan)
        _napDraft.value = null
        _tab.value = Tab.HOME
    }

    fun stopNap() = app.nap.stop()

    // ---- sleep times ----

    fun adjustNight(night: String, start: Long?, end: Long?) = viewModelScope.launch { repo.adjustNight(night, start, end) }

    // ---- sunset ----

    fun openSunset() {
        val s = _sunset.value ?: return
        _sunsetDraft.value = SunsetDraft(s, s)
    }

    fun updateSunset(change: (SunsetSettings) -> SunsetSettings) {
        _sunsetDraft.update { it?.copy(settings = change(it.settings)) }
    }

    fun closeSunset() {
        endPreview()
        _sunsetDraft.value = null
    }

    fun saveSunset() {
        val d = _sunsetDraft.value ?: return
        endPreview()
        device(Pending.SAVE, retry = ::saveSunset) {
            repo.client().saveSunset(d.settings)
            applySunset(repo.sunset())
            _sunsetDraft.value = null
            _message.value = UiMessage("Sonnenuntergang gespeichert")
        }
    }

    fun setSunsetRunning(on: Boolean): Job = device(Pending.SUNSET, retry = { setSunsetRunning(on) }) {
        repo.client().setSunsetRunning(on)
        applySunset(repo.sunset())
    }

    private suspend fun applySunset(s: SunsetSettings) {
        _sunset.value = s
        if (s.running) {
            _sunsetRemaining.value = repo.client().sunsetRemaining()
            if (sunsetPoll?.isActive != true) {
                sunsetPoll = viewModelScope.launch {
                    while (_sunset.value?.running == true) {
                        delay(15_000)
                        runCatching {
                            _sunsetRemaining.value = repo.client().sunsetRemaining()
                            if (_sunsetRemaining.value == 0) _sunset.value = repo.sunset()
                        }
                    }
                }
            }
        }
    }

    // ---- plumbing ----

    /**
     * Runs a device call. While it runs, [key] is in [pending] so the element can show it.
     * On failure the message offers [retry].
     */
    private fun device(
        key: String? = null,
        onFail: () -> Unit = {},
        retry: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        if (app.settings.host == null) return@launch
        key?.let { k -> _pending.update { it + k } }
        _connection.value = Connection.Loading
        try {
            block()
            _connection.value = Connection.Ok
        } catch (e: SomneoException) {
            val text = e.message ?: "Unbekannter Fehler"
            _connection.value = Connection.Failed(text)
            onFail()
            _message.value = UiMessage(text, retry?.let { "Erneut versuchen" }, retry)
        } finally {
            key?.let { k -> _pending.update { it - k } }
        }
    }
}
