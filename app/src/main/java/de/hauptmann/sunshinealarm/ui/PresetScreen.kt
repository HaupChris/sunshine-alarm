package de.hauptmann.sunshinealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type
import java.util.Locale

/** One radio preset: a name (kept in the app) and the frequency (stored on the device) */
@Composable
fun PresetScreen(vm: MainViewModel, d: PresetDraft) {
    var askDiscard by remember { mutableStateOf(false) }
    val back = { if (d.dirty) askDiscard = true else vm.discardPreset() }
    BackHandler(onBack = back)

    Column(Modifier.fillMaxSize().background(Palette.background)) {
        EditorBar("Sender ${d.number}", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
            Row(Modifier.padding(top = Space.s)) {
                Text(String.format(Locale.GERMAN, "%.1f", d.mhz), style = Type.hero, color = Palette.text, maxLines = 1)
                Text(" MHz", style = Type.figure, color = Palette.textMuted, modifier = Modifier.padding(top = Space.l))
            }
            Text(
                if (d.seeking) "Suchlauf läuft" else "Läuft am Wecker, damit du hörst, was eingestellt ist",
                style = Type.secondary,
                color = if (d.seeking) Palette.accent else Palette.textMuted,
            )

            Spacer(Modifier.height(Space.xxl))
            SectionLabel("Sender suchen")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton("Zurück", { vm.seekPreset(up = false) }, Modifier.weight(1f), pending = d.seeking)
                SecondaryButton("Weiter", { vm.seekPreset(up = true) }, Modifier.weight(1f), pending = d.seeking)
            }
            Spacer(Modifier.height(Space.l))
            SectionLabel("Feineinstellung")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                SecondaryButton("− 1", { vm.stepPreset(-10) }, Modifier.weight(1f))
                SecondaryButton("− 0,1", { vm.stepPreset(-1) }, Modifier.weight(1f))
                SecondaryButton("+ 0,1", { vm.stepPreset(1) }, Modifier.weight(1f))
                SecondaryButton("+ 1", { vm.stepPreset(10) }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(Space.section))
            SectionLabel("Name")
            NameField(d.name, placeholder = "z. B. Deutschlandfunk", maxLength = 24, onChange = vm::renamePreset)
            Hint("Der Name wird nur in der App gespeichert, der Wecker kennt keine Sendernamen.")
            Spacer(Modifier.height(Space.section))
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(Space.page)) {
            PrimaryButton("Speichern", vm::savePreset, Modifier.fillMaxWidth(), enabled = !d.seeking)
        }
    }
    if (askDiscard) DiscardDialog(onDiscard = { askDiscard = false; vm.discardPreset() }, onKeep = { askDiscard = false })
}
