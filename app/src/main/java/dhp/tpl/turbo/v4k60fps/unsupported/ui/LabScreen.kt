@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dhp.tpl.turbo.v4k60fps.unsupported.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dhp.tpl.turbo.v4k60fps.unsupported.engine.RateMode
import dhp.tpl.turbo.v4k60fps.unsupported.lab.BenchResult
import dhp.tpl.turbo.v4k60fps.unsupported.lab.CodecProbe
import dhp.tpl.turbo.v4k60fps.unsupported.lab.DecodeBenchmark
import dhp.tpl.turbo.v4k60fps.unsupported.lab.DecoderReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BENCH_MODES = listOf(RateMode.MAX, RateMode.MATCH, RateMode.OFF)
private const val BENCH_SECONDS = 8

@Composable
fun LabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decoders = remember { CodecProbe.videoDecoders() }
    val vendorParams = remember { mutableStateMapOf<String, List<String>>() }
    val results = remember { mutableStateListOf<BenchResult>() }
    var running by remember { mutableStateOf<RateMode?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && running == null) {
            scope.launch {
                results.clear()
                for (mode in BENCH_MODES) {
                    running = mode
                    progress = 0f
                    results += DecodeBenchmark.run(context, uri, mode, BENCH_SECONDS) { progress = it }
                }
                running = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decoder lab") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Speed, null)
                            Spacer(Modifier.width(10.dp))
                            Text("Real decode test", style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pick a 4K60 video. The hardware decoder decodes it flat out for " +
                                "$BENCH_SECONDS s with each clock setting (no screen, no sync), " +
                                "so you see its real top speed for that file.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        val current = running
                        if (current != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingIndicator(Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Testing \"${current.label}\" clock…", style = MaterialTheme.typography.titleSmall)
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        } else {
                            Button(onClick = { picker.launch(arrayOf("video/*")) }) {
                                Text(if (results.isEmpty()) "Pick video and test" else "Test another video")
                            }
                        }
                    }
                }
            }

            items(results) { BenchCard(it) }

            item {
                Text(
                    "${Build.MANUFACTURER} ${Build.MODEL}" +
                        (if (Build.VERSION.SDK_INT >= 31) " · ${Build.SOC_MODEL}" else "") +
                        " · Android ${Build.VERSION.RELEASE}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
                Text(
                    "What each decoder advertises",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            items(decoders, key = { it.name + it.mime }) { report ->
                DecoderCard(
                    report = report,
                    vendorParams = vendorParams[report.name],
                    onLoadVendor = {
                        scope.launch {
                            vendorParams[report.name] = withContext(Dispatchers.IO) {
                                CodecProbe.vendorParameters(report.name)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BenchCard(result: BenchResult) {
    val keepsUp = result.error == null && result.averageFps >= result.videoFps * 0.98f
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("\"${result.mode.label}\" clock", style = MaterialTheme.typography.titleMedium)
            val error = result.error
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%.1f".format(result.averageFps), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    "fps decoded · video is %.0f fps".format(result.videoFps),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                if (keepsUp) "Fast enough: this file can play smoothly on this setting."
                else "Too slow: %.0f%% of real time, so playback will stutter.".format(
                    100 * result.averageFps / result.videoFps.coerceAtLeast(1f),
                ),
                color = if (keepsUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            FpsBars(result.perSecond, result.videoFps)
            Spacer(Modifier.height(6.dp))
            Text(
                "${result.decoder} · ${result.resolution} · each bar = 1 s, dashed line = video fps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FpsBars(perSecond: List<Float>, target: Float) {
    val good = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error
    val line = MaterialTheme.colorScheme.onSurface
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        if (perSecond.isEmpty()) return@Canvas
        val top = maxOf(perSecond.max(), target) * 1.1f
        val gap = 6.dp.toPx()
        val barWidth = (size.width - gap * (perSecond.size - 1)) / perSecond.size
        perSecond.forEachIndexed { i, fps ->
            val h = size.height * fps / top
            drawRoundRect(
                color = if (fps >= target * 0.98f) good else bad,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
        }
        val y = size.height - size.height * target / top
        drawLine(
            color = line,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        )
    }
}

@Composable
private fun DecoderCard(report: DecoderReport, vendorParams: List<String>?, onLoadVendor: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(report.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Badge(if (report.hardware) "HW" else "SW", if (report.hardware) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.height(6.dp))
            val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            Text("type      ${report.mime}", style = mono)
            Text("max size  ${report.maxSize}", style = mono)
            Text("4K limit  ${report.limit4k}", style = mono)
            Text("4K tested ${report.measured4k}", style = mono)
            report.performancePoints.take(6).forEach { Text("perf      $it", style = mono) }
            if (vendorParams == null) {
                TextButton(onClick = onLoadVendor) { Text("Show vendor parameters") }
            } else {
                Spacer(Modifier.height(4.dp))
                if (vendorParams.isEmpty()) Text("No vendor parameters", style = mono)
                vendorParams.forEach { Text(it, style = mono) }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .background(color, CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.surface)
    }
}
