package de.hauptmann.sunshinealarm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.BuildConfig
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Radius
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.Type

@Composable
fun ConnectionScreen(vm: MainViewModel) {
    val host by vm.host.collectAsStateWithLifecycle()
    val found by vm.found.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var input by remember(host) { mutableStateOf(host.orEmpty()) }
    if (host != null) BackHandler(onBack = vm::closeConnection)

    Column(Modifier.fillMaxSize().background(Palette.background)) {
    EditorBar("Verbindung", if (host != null) vm::closeConnection else null)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.page)) {
        Text(
            "Die App spricht direkt mit dem Wecker in deinem WLAN. Es werden keine Daten an Philips oder andere Dienste gesendet.",
            style = Type.secondary,
            color = Palette.textSecondary,
        )

        Spacer(Modifier.height(Space.xxl))
        SecondaryButton("Im WLAN suchen", { vm.search() }, Modifier.fillMaxWidth(), icon = Line.wifi, pending = searching)
        found?.let { list ->
            Spacer(Modifier.height(Space.m))
            if (list.isEmpty()) {
                Text("Kein Wecker gefunden. Ist das Handy im selben WLAN?", style = Type.secondary, color = Palette.textMuted)
            }
            list.forEach { d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(Space.row)
                        .clickable { vm.connect(d.host) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = Type.body, color = Palette.text)
                        Text(d.host, style = Type.meta, color = Palette.textMuted)
                    }
                    Icon(Line.chevron, null, tint = Palette.textMuted, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(Modifier.height(Space.section))
        SectionLabel("Oder Adresse eingeben")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("192.168.178.105", style = Type.value, color = Palette.textMuted) },
                textStyle = Type.value.copy(color = Palette.text),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                shape = RoundedCornerShape(Radius.control),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.textMuted,
                    unfocusedBorderColor = Palette.hairline,
                    cursorColor = Palette.accent,
                    focusedContainerColor = Palette.deepest,
                    unfocusedContainerColor = Palette.deepest,
                ),
                modifier = Modifier.weight(1f),
            )
            PrimaryButton("Verbinden", { vm.connect(input) }, enabled = input.isNotBlank())
        }

        vm.fingerprint?.let { fp ->
            Spacer(Modifier.height(Space.section))
            SectionLabel("Zertifikat des Weckers")
            // 32 bytes in three even lines that fit the phone width
            fp.split(":").chunked(11).forEach { Text(it.joinToString(":"), style = Type.meta, color = Palette.textMuted) }
            Spacer(Modifier.height(Space.s))
            Text(
                "Beim ersten Kontakt gespeichert. Ändert es sich, verweigert die App die Verbindung, damit sich kein anderes Gerät als Wecker ausgeben kann.",
                style = Type.secondary,
                color = Palette.textSecondary,
            )
            TextAction("Zertifikat zurücksetzen", vm::forgetCertificate)
        }

        Spacer(Modifier.height(Space.section))
        Text(
            "Version ${BuildConfig.VERSION_NAME} · Design nach unslop von Damir Sokolovsky",
            style = Type.meta,
            color = Palette.textMuted,
        )
        Spacer(Modifier.height(Space.section))
        Spacer(Modifier.height(Space.section).background(Palette.background))
    }
    }
}
