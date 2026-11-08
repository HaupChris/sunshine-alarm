package de.hauptmann.sunshinealarm.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.nap.NapPlan
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

private val NAP_PRESETS = listOf(10, 20, 30, 45, 60, 90)

/**
 * A nap is timed by the phone. When it ends, the lamp rises within about 10 s in
 * the chosen theme while the sound starts, and the phone shows that it is over.
 */
@Composable
fun NapScreen(vm: MainViewModel, plan: NapPlan) {
    val now by vm.now.collectAsStateWithLifecycle()
    val themes by vm.themes.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val wake = now.plusMinutes(plan.minutes.toLong())
    // The "nap over" notice needs the notification permission (Android 13+)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startNap() }
    BackHandler(onBack = vm::closeNap)

    Column(Modifier.fillMaxSize().background(Palette.background)) {
        EditorBar("Nap", vm::closeNap, preview = preview, onPreview = vm::setPreview)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            Text("${plan.minutes} min", style = Type.hero, color = Palette.text, maxLines = 1, modifier = Modifier.padding(top = Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wecken um", style = Type.body, color = Palette.textSecondary, modifier = Modifier.weight(1f))
                Text(hhmm(wake.hour, wake.minute), style = Type.value, color = Palette.accent)
            }

            Spacer(Modifier.height(Space.xxl))
            ChipRow(
                labels = NAP_PRESETS.map { "$it" },
                isOn = { i -> NAP_PRESETS[i] == plan.minutes },
                onToggle = { i -> vm.updateNap { it.copy(minutes = NAP_PRESETS[i]) } },
            )
            Spacer(Modifier.height(Space.l))
            LabeledSlider("Dauer", plan.minutes, 1..120, format = { "$it min" }) { v -> vm.updateNap { it.copy(minutes = v) } }

            Spacer(Modifier.height(Space.section))
            SectionLabel("Lampe beim Wecken")
            ToggleRow("Sonnenaufgang in 10 Sekunden", plan.withLight, { on -> vm.updateNap { it.copy(withLight = on) } })
            if (plan.withLight) {
                LabeledSlider("Helligkeit", plan.level, 1..25, format = { "$it von 25" }, onDone = vm::showCurrentLight) { v ->
                    vm.updateNap { it.copy(level = v) }
                }
                ChoiceList(
                    options = themes?.light ?: listOf("Sunny day"),
                    selected = plan.theme,
                    onSelect = { i ->
                        vm.updateNap { it.copy(theme = i) }
                        vm.showCurrentLight()
                    },
                    leading = { i -> ThemeSwatch(i) },
                )
            }

            Spacer(Modifier.height(Space.section))
            SectionLabel("Ton beim Wecken")
            ToggleRow("Ton", plan.withSound, { on ->
                vm.updateNap { it.copy(withSound = on) }
                vm.playCurrentPreview()
            })
            if (plan.withSound) {
                ChoiceList(
                    options = themes?.wakeSounds ?: listOf("Forest Birds"),
                    selected = plan.channel - 1,
                    onSelect = { i ->
                        vm.updateNap { it.copy(channel = i + 1) }
                        vm.playCurrentPreview()
                    },
                )
                LabeledSlider("Lautstärke", plan.volume, 1..25, format = { "$it von 25" }, onDone = vm::playCurrentPreview) { v ->
                    vm.updateNap { it.copy(volume = v) }
                }
            }
            Spacer(Modifier.height(Space.l))
            Hint("Das Handy zählt die Zeit und muss dafür im WLAN des Weckers sein. Am Ende erscheint auf dem Handy eine Meldung zum Stoppen.")
            Spacer(Modifier.height(Space.section))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.page)) {
            PrimaryButton(
                "Nap starten · ${hhmm(wake.hour, wake.minute)}",
                { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.startNap() },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
