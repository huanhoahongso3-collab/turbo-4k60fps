@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dhp.tpl.turbo.v4k60fps.unsupported.ui

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dhp.tpl.turbo.v4k60fps.unsupported.engine.EngineConfig
import dhp.tpl.turbo.v4k60fps.unsupported.engine.RateMode

@Composable
fun TurboTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            primaryContainer = Color(0xFF4F378B),
            tertiary = Color(0xFFFFB4AB),
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Engine switches, shared by the home screen and the in-player sheet. */
@Composable
fun EngineSettings(config: EngineConfig, onChange: (EngineConfig) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sustainedSupported = remember {
        context.getSystemService(PowerManager::class.java)?.isSustainedPerformanceModeSupported == true
    }
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Decoder engine", style = MaterialTheme.typography.headlineSmall)
            Text(
                "How hard Turbo4K pushes your phone's hardware decoder.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text("Clock request", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RateMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = config.rateMode == mode,
                        onClick = { onChange(config.copy(rateMode = mode)) },
                        shape = SegmentedButtonDefaults.itemShape(index, RateMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                config.rateMode.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        SettingSwitch(
            "Force hardware decoder",
            "Never use Android's slow software decoders for video",
            config.hardwareOnly,
        ) { onChange(config.copy(hardwareOnly = it)) }
        SettingSwitch(
            "Ignore advertised limits",
            "Play 4K60 even when the phone says its decoder tops out at 4K30",
            config.ignoreCapabilityCheck,
        ) { onChange(config.copy(ignoreCapabilityCheck = it)) }
        SettingSwitch(
            "Realtime priority",
            "Mark the decoder session as realtime",
            config.realtimePriority,
        ) { onChange(config.copy(realtimePriority = it)) }
        SettingSwitch(
            "Low-latency decode",
            "Experimental: Android and Qualcomm low-latency mode",
            config.lowLatency,
        ) { onChange(config.copy(lowLatency = it)) }
        SettingSwitch(
            "Auto-fallback",
            "If the decoder refuses a setting, step down until it plays",
            config.autoFallback,
        ) { onChange(config.copy(autoFallback = it)) }
        if (sustainedSupported) {
            SettingSwitch(
                "Sustained performance",
                "Steadier clocks that throttle less over time",
                config.sustainedPerformance,
            ) { onChange(config.copy(sustainedPerformance = it)) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp),
    )
}
