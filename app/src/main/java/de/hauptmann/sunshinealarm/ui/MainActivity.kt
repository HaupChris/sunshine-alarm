package de.hauptmann.sunshinealarm.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.hauptmann.sunshinealarm.ui.theme.Line
import de.hauptmann.sunshinealarm.ui.theme.Palette
import de.hauptmann.sunshinealarm.ui.theme.Space
import de.hauptmann.sunshinealarm.ui.theme.SunshineTheme
import de.hauptmann.sunshinealarm.ui.theme.Type

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent { SunshineTheme { App(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.refresh()
    }
}

@Composable
private fun App(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val showConnection by vm.showConnection.collectAsStateWithLifecycle()
    val napDraft by vm.napDraft.collectAsStateWithLifecycle()
    val sunsetDraft by vm.sunsetDraft.collectAsStateWithLifecycle()
    val presetDraft by vm.presetDraft.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Palette.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    else -> when (tab) {
                        Tab.HOME -> HomeScreen(vm)
                        Tab.ALARMS -> AlarmsScreen(vm)
                        Tab.CONTROL -> ControlScreen(vm)
                        Tab.HISTORY -> HistoryScreen(vm)
                    }
                }
            }
            message?.let { m ->
                LaunchedEffect(m.id) {
                    delay(if (m.action != null) 6_000 else 3_000)
                    vm.dismissMessage()
                }
                MessageBar(
                    m,
                    onAction = { vm.dismissMessage(); m.action?.invoke() },
                    onDismiss = vm::dismissMessage,
                    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.s),
                )
            }
            BottomBar(tab, vm::select)
        }
        editing?.let { Box(Modifier.fillMaxSize().statusBarsPadding()) { AlarmEditScreen(vm, it) } }
        napDraft?.let { Box(Modifier.fillMaxSize().statusBarsPadding()) { NapScreen(vm, it) } }
        sunsetDraft?.let { Box(Modifier.fillMaxSize().statusBarsPadding()) { SunsetScreen(vm, it) } }
        presetDraft?.let { Box(Modifier.fillMaxSize().statusBarsPadding()) { PresetScreen(vm, it) } }
        if (showConnection) Box(Modifier.fillMaxSize().statusBarsPadding()) { ConnectionScreen(vm) }
        // Messages also show above editors, which cover the bottom bar
        if (editing != null || napDraft != null || sunsetDraft != null || presetDraft != null || showConnection) {
            message?.let { m ->
                Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 80.dp), contentAlignment = Alignment.BottomCenter) {
                    MessageBar(m, onAction = { vm.dismissMessage(); m.action?.invoke() }, onDismiss = vm::dismissMessage,
                        modifier = Modifier.padding(horizontal = Space.page))
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.deepest).navigationBarsPadding()) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(Palette.hairline))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            listOf(
                Tab.HOME to ("Heute" to Line.sunrise),
                Tab.ALARMS to ("Wecker" to Line.alarm),
                Tab.CONTROL to ("Steuerung" to Line.sliders),
                Tab.HISTORY to ("Verlauf" to Line.chart),
            )
                .forEach { (tab, item) ->
                    val active = tab == selected
                    val color = if (active) Palette.text else Palette.textMuted
                    Column(
                        Modifier.weight(1f).fillMaxSize().clickable { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(item.second, null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(Space.xs))
                        Text(item.first, style = Type.secondary.copy(fontSize = 12.sp, lineHeight = 16.sp), color = color)
                    }
                }
        }
    }
}
