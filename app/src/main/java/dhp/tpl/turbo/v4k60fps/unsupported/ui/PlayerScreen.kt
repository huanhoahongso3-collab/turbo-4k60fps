@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package dhp.tpl.turbo.v4k60fps.unsupported.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.SurfaceView
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import dhp.tpl.turbo.v4k60fps.unsupported.data.formatTime
import dhp.tpl.turbo.v4k60fps.unsupported.engine.PlaybackStats
import dhp.tpl.turbo.v4k60fps.unsupported.engine.TurboPlayer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class Sheet { NONE, TRACKS, SPEED, ENGINE }
private enum class Aspect(val label: String) { FIT("Fit"), CROP("Crop"), STRETCH("Stretch") }
private enum class Drag { NONE, SEEK, BRIGHTNESS, VOLUME, PINCH }

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f)

@Composable
fun PlayerScreen(
    turbo: TurboPlayer,
    title: String,
    showStatsInitially: Boolean,
    onShowStatsChanged: (Boolean) -> Unit,
    rotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onPickSubtitle: () -> Unit,
    onBack: () -> Unit,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val player = turbo.player

    var controlsVisible by remember { mutableStateOf(true) }
    var showStats by remember { mutableStateOf(showStatsInitially) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var aspect by remember { mutableStateOf(Aspect.FIT) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudVersion by remember { mutableLongStateOf(0L) }
    var interaction by remember { mutableLongStateOf(0L) }

    fun showHud(text: String) {
        hud = text
        hudVersion++
    }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                position = it.currentPosition
                duration = it.duration.coerceAtLeast(0)
            }
            delay(250)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            turbo.sampleStats()
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, turbo.isPlaying, interaction, sheet) {
        if (controlsVisible && turbo.isPlaying && sheet == Sheet.NONE) {
            delay(3500)
            controlsVisible = false
        }
    }
    LaunchedEffect(hudVersion) {
        if (hud != null) {
            delay(900)
            hud = null
        }
    }
    LaunchedEffect(turbo.notice) {
        if (turbo.notice != null) {
            delay(5000)
            turbo.notice = null
        }
    }
    LaunchedEffect(turbo.videoSize, rotationLocked) {
        val size = turbo.videoSize
        if (!rotationLocked && size.width > 0 && size.height > 0) {
            activity?.requestedOrientation = if (size.width * size.pixelWidthHeightRatio >= size.height) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }
    LaunchedEffect(turbo.config.sustainedPerformance) {
        runCatching { activity?.window?.setSustainedPerformanceMode(turbo.config.sustainedPerformance) }
    }

    // Brightness and volume, adjusted by vertical swipes.
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember {
        mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 }
                ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Video surface. SurfaceView goes straight to the display compositor, with no extra GPU pass.
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val video = turbo.videoSize
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val surfaceModifier = if (video.width > 0 && video.height > 0) {
                val vw = video.width * video.pixelWidthHeightRatio
                val vh = video.height.toFloat()
                val (w, h) = when (aspect) {
                    Aspect.FIT -> min(boxW / vw, boxH / vh).let { vw * it to vh * it }
                    Aspect.CROP -> max(boxW / vw, boxH / vh).let { vw * it to vh * it }
                    Aspect.STRETCH -> boxW to boxH
                }
                with(density) { Modifier.requiredSize((w * zoom).toDp(), (h * zoom).toDp()) }
            } else {
                Modifier.fillMaxSize()
            }
            AndroidView(
                factory = { ctx -> SurfaceView(ctx).also(turbo::attachSurface) },
                onRelease = { turbo.detachSurface(it) },
                modifier = surfaceModifier,
            )
        }

        // Subtitles
        if (turbo.cues.isNotEmpty()) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 120.dp else 32.dp, start = 48.dp, end = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                turbo.cues.mapNotNull { it.text?.toString() }.forEach { line ->
                    Text(
                        line,
                        textAlign = TextAlign.Center,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            shadow = Shadow(Color.Black, Offset(0f, 2f), 6f),
                        ),
                    )
                }
            }
        }

        // Gestures
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            when {
                                offset.x < third -> { turbo.seekBy(-10_000); showHud("−10 s") }
                                offset.x > third * 2 -> { turbo.seekBy(10_000); showHud("+10 s") }
                                else -> turbo.togglePlay()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var mode = Drag.NONE
                        var total = Offset.Zero
                        val seekStart = turbo.player?.currentPosition ?: 0L
                        val seekMax = turbo.player?.duration?.takeIf { it > 0 } ?: 0L
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                mode = Drag.PINCH
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                showHud("Zoom ${(zoom * 100).roundToInt()}%")
                                event.changes.forEach { it.consume() }
                            } else if (pressed.size == 1 && mode != Drag.PINCH) {
                                val change = pressed[0]
                                val delta = change.positionChange()
                                total += delta
                                if (mode == Drag.NONE && total.getDistance() > viewConfiguration.touchSlop) {
                                    mode = when {
                                        abs(total.x) > abs(total.y) -> Drag.SEEK
                                        down.position.x < size.width / 2f -> Drag.BRIGHTNESS
                                        else -> Drag.VOLUME
                                    }
                                }
                                when (mode) {
                                    Drag.SEEK -> if (seekMax > 0) {
                                        // A full-width swipe moves 90 seconds.
                                        val offsetMs = (total.x / size.width * 90_000).toLong()
                                        val target = (seekStart + offsetMs).coerceIn(0, seekMax)
                                        scrubTarget = target
                                        val sign = if (offsetMs >= 0) "+" else "−"
                                        showHud("$sign${abs(offsetMs) / 1000} s · ${formatTime(target)}")
                                    }
                                    Drag.BRIGHTNESS -> {
                                        brightness = (brightness - delta.y / size.height).coerceIn(0.01f, 1f)
                                        activity?.window?.let { w ->
                                            w.attributes = w.attributes.apply { screenBrightness = brightness }
                                        }
                                        showHud("Brightness ${(brightness * 100).roundToInt()}%")
                                    }
                                    Drag.VOLUME -> {
                                        volume = (volume - delta.y / size.height).coerceIn(0f, 1f)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).roundToInt(), 0)
                                        showHud("Volume ${(volume * 100).roundToInt()}%")
                                    }
                                    else -> Unit
                                }
                                if (mode != Drag.NONE) change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                        if (mode == Drag.SEEK) {
                            scrubTarget?.let { turbo.seekTo(it) }
                            scrubTarget = null
                        }
                    }
                },
        )

        // Stats
        AnimatedVisibility(
            visible = showStats,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = if (controlsVisible) 72.dp else 12.dp, start = 12.dp),
        ) {
            StatsPanel(turbo.stats)
        }

        // Gesture feedback
        hud?.let {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.65f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
            }
        }

        // Controls
        AnimatedVisibility(
            visible = controlsVisible || scrubTarget != null,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                turbo = turbo,
                title = title,
                position = scrubTarget ?: position,
                duration = duration,
                aspect = aspect,
                rotationLocked = rotationLocked,
                showStats = showStats,
                onBack = onBack,
                onScrub = { scrubTarget = it; interaction++ },
                onScrubEnd = { scrubTarget?.let { turbo.seekTo(it) }; scrubTarget = null },
                onAspect = {
                    aspect = Aspect.entries[(aspect.ordinal + 1) % Aspect.entries.size]
                    zoom = 1f
                    showHud(aspect.label)
                    interaction++
                },
                onToggleStats = {
                    showStats = !showStats
                    onShowStatsChanged(showStats)
                    interaction++
                },
                onToggleRotationLock = { onToggleRotationLock(); interaction++ },
                onSheet = { sheet = it },
            )
        }

        // Fallback notices and errors
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 72.dp, start = 24.dp, end = 24.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            turbo.notice?.let { MessageCard(it, MaterialTheme.colorScheme.secondaryContainer, null, null) }
            turbo.error?.let {
                MessageCard(it, MaterialTheme.colorScheme.errorContainer, "Engine settings") { sheet = Sheet.ENGINE }
            }
        }

        if (turbo.isBuffering && !controlsVisible) {
            LoadingIndicator(
                Modifier
                    .align(Alignment.Center)
                    .size(72.dp),
            )
        }
    }

    if (sheet != Sheet.NONE) {
        ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                when (sheet) {
                    Sheet.TRACKS -> TracksSheet(turbo, onPickSubtitle = { sheet = Sheet.NONE; onPickSubtitle() })
                    Sheet.SPEED -> SpeedSheet(turbo.speed, turbo::setPlaybackSpeed)
                    Sheet.ENGINE -> EngineSettings(turbo.config, onChange = turbo::applyConfig)
                    Sheet.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun Controls(
    turbo: TurboPlayer,
    title: String,
    position: Long,
    duration: Long,
    aspect: Aspect,
    rotationLocked: Boolean,
    showStats: Boolean,
    onBack: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    onAspect: () -> Unit,
    onToggleStats: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onSheet: (Sheet) -> Unit,
) {
    val stats = turbo.stats
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        // Top bar
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (stats.hardware != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DecoderPill(stats.hardware)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOf(stats.resolution, stats.codec, stats.dynamicRange).joinToString(" · "),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
            IconButton(onClick = { onSheet(Sheet.TRACKS) }) { Icon(Icons.Rounded.Subtitles, "Audio and subtitles", tint = Color.White) }
            IconButton(onClick = { onSheet(Sheet.SPEED) }) { Icon(Icons.Rounded.Speed, "Speed", tint = Color.White) }
            IconButton(onClick = { onSheet(Sheet.ENGINE) }) { Icon(Icons.Rounded.Bolt, "Decoder engine", tint = Color.White) }
            IconButton(onClick = onToggleStats) {
                Icon(Icons.Rounded.Insights, "Stats", tint = if (showStats) MaterialTheme.colorScheme.primary else Color.White)
            }
        }

        // Center
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            FilledTonalIconButton(onClick = { turbo.seekBy(-10_000) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Replay10, "Back 10 seconds")
            }
            PlayButton(turbo)
            FilledTonalIconButton(onClick = { turbo.seekBy(10_000) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Forward10, "Forward 10 seconds")
            }
        }

        // Bottom bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { onScrub(it.toLong()) },
                    onValueChangeFinished = onScrubEnd,
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAspect) {
                    Icon(Icons.Rounded.AspectRatio, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(aspect.label, color = Color.White)
                }
                TextButton(onClick = onToggleRotationLock) {
                    Icon(
                        if (rotationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (rotationLocked) "Locked" else "Auto-rotate", color = Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (turbo.speed != 1f) {
                    Text("${turbo.speed}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PlayButton(turbo: TurboPlayer) {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val rotation by animateFloatAsState(
        targetValue = if (turbo.playWhenReady) 0f else 40f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "playRotation",
    )
    val scale by animateFloatAsState(
        targetValue = if (turbo.playWhenReady) 1f else 1.08f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playScale",
    )
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { turbo.togglePlay() },
        )
        if (turbo.isBuffering) {
            LoadingIndicator(Modifier.size(56.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            Icon(
                if (turbo.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (turbo.playWhenReady) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun DecoderPill(hardware: Boolean) {
    Box(
        Modifier
            .background(
                if (hardware) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                CircleShape,
            )
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        Text(
            if (hardware) "HW" else "SW",
            style = MaterialTheme.typography.labelSmall,
            color = if (hardware) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError,
        )
    }
}

@Composable
private fun StatsPanel(stats: PlaybackStats) {
    val keepsUp = stats.videoFps > 0 && stats.renderedFps >= stats.videoFps * 0.95f
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color.White, lineHeight = 15.sp)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.widthIn(max = 420.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("DECODER  ${stats.decoder} ${stats.hardware?.let { if (it) "[HW]" else "[SW]" } ?: ""}", style = mono)
            Text(
                "VIDEO    ${stats.resolution} · %.2f fps · ${stats.codec} · ${stats.dynamicRange}".format(stats.videoFps) +
                    if (stats.bitrateMbps > 0) " · %.0f Mbps".format(stats.bitrateMbps) else "",
                style = mono,
            )
            Text(
                "LIMIT    ${stats.advertised}" + if (stats.forced) "  → FORCED" else "",
                style = mono.copy(color = if (stats.forced) Color(0xFFFFD180) else Color.White),
            )
            Text(
                "OUTPUT   %.1f fps · dropped ${stats.dropped} (${stats.droppedLast5s} in 5 s)".format(stats.renderedFps),
                style = mono.copy(color = if (keepsUp) Color(0xFFA5D6A7) else Color(0xFFEF9A9A)),
            )
            Text("ENGINE   ${stats.engine}", style = mono)
            Text("THERMAL  ${stats.thermal}", style = mono)
        }
    }
}

@Composable
private fun MessageCard(text: String, color: Color, action: String?, onAction: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(20.dp), color = color) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) { Text(action) }
            }
        }
    }
}

@Composable
private fun TracksSheet(turbo: TurboPlayer, onPickSubtitle: () -> Unit) {
    val groups = turbo.tracks.groups
    SheetTitle("Audio")
    val audio = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (audio.isEmpty()) SheetHint("No audio tracks")
    audio.forEach { group ->
        for (i in 0 until group.length) {
            TrackRow(trackLabel(group.getTrackFormat(i), i), group.isTrackSelected(i)) { turbo.selectTrack(group, i) }
        }
    }
    Spacer(Modifier.height(12.dp))
    SheetTitle("Subtitles")
    val text = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val textOff = turbo.isTrackTypeDisabled(C.TRACK_TYPE_TEXT) || text.none { it.isSelected }
    TrackRow("Off", textOff) { turbo.disableTrackType(C.TRACK_TYPE_TEXT) }
    text.forEach { group ->
        for (i in 0 until group.length) {
            TrackRow(trackLabel(group.getTrackFormat(i), i), !textOff && group.isTrackSelected(i)) {
                turbo.selectTrack(group, i)
            }
        }
    }
    FilledTonalButton(onClick = onPickSubtitle, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Icon(Icons.Rounded.Subtitles, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(turbo.subtitleName?.let { "Loaded: $it" } ?: "Load subtitle file…", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun SpeedSheet(speed: Float, onSpeed: (Float) -> Unit) {
    SheetTitle("Playback speed")
    Text(
        "%.2f×".format(speed),
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Slider(
        value = speed,
        onValueChange = { onSpeed((it * 20).roundToInt() / 20f) },
        valueRange = 0.25f..4f,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    FlowRow(
        Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SPEEDS.forEach { value ->
            FilterChip(
                selected = speed == value,
                onClick = { onSpeed(value) },
                label = { Text("${value}×") },
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
}

private fun trackLabel(format: Format, index: Int): String {
    val parts = listOfNotNull(
        format.label,
        format.language?.takeIf { it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage },
        format.channelCount.takeIf { it > 0 }?.let { "${it}ch" },
        format.sampleMimeType?.substringAfter('/'),
    ).distinct()
    return parts.joinToString(" · ").ifEmpty { "Track ${index + 1}" }
}

