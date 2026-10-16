package dhp.tpl.turbo.v4k60fps.unsupported.engine

import android.content.Context
import android.media.MediaCodecList
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dhp.tpl.turbo.v4k60fps.unsupported.data.Store

data class PlaybackStats(
    val decoder: String = "—",
    val hardware: Boolean? = null,
    val resolution: String = "—",
    val codec: String = "—",
    val dynamicRange: String = "SDR",
    val videoFps: Float = 0f,
    val bitrateMbps: Float = 0f,
    val renderedFps: Float = 0f,
    val dropped: Int = 0,
    val droppedLast5s: Int = 0,
    val advertised: String = "—",
    val forced: Boolean = false,
    val engine: String = "—",
    val thermal: String = "—",
)

private val DECODER_ERRORS = setOf(
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
)

/**
 * Owns the ExoPlayer instance. Engine changes rebuild the player at the same position, and a
 * decoder that refuses a setting is retried with [EngineConfig.fallback].
 */
class TurboPlayer(private val context: Context, private val store: Store) {
    var player by mutableStateOf<ExoPlayer?>(null)
        private set
    var config by mutableStateOf(store.loadConfig())
        private set
    var stats by mutableStateOf(PlaybackStats())
        private set
    var notice by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var playWhenReady by mutableStateOf(true)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)
        private set
    var cues by mutableStateOf<List<Cue>>(emptyList())
        private set
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var subtitleName by mutableStateOf<String?>(null)
        private set

    private val main = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var surfaceView: SurfaceView? = null
    private var mediaUri: Uri? = null
    private var subtitleUri: Uri? = null
    private var decoderName: String? = null
    private var engineApplied = "—"
    private var lastRendered = 0
    private var lastSampleAt = 0L
    private val dropHistory = ArrayDeque<Pair<Long, Int>>()
    private var advertisedKey: String? = null
    private var advertised = "—"
    private var forced = false

    val currentUri: Uri? get() = mediaUri

    fun attachSurface(view: SurfaceView) {
        surfaceView = view
        player?.setVideoSurfaceView(view)
    }

    fun detachSurface(view: SurfaceView) {
        player?.clearVideoSurfaceView(view)
        if (surfaceView === view) surfaceView = null
    }

    fun open(
        uri: Uri,
        startMs: Long = 0,
        playWhenReady: Boolean = true,
        engine: EngineConfig = config,
    ) {
        val keptSelection = player?.trackSelectionParameters
        releasePlayer()
        if (uri != mediaUri) {
            subtitleUri = null
            subtitleName = null
        }
        mediaUri = uri
        config = engine
        error = null
        decoderName = null
        engineApplied = "—"
        lastRendered = 0
        lastSampleAt = 0
        dropHistory.clear()
        advertisedKey = null

        val factory = TurboRenderersFactory(context, engine) { applied ->
            main.post { engineApplied = applied }
        }
        val p = ExoPlayer.Builder(context, factory)
            .setTrackSelector(DefaultTrackSelector(context))
            .build()
        keptSelection?.let { p.trackSelectionParameters = it }
        p.addListener(listener)
        p.addAnalyticsListener(analytics)
        surfaceView?.let { p.setVideoSurfaceView(it) }
        p.setMediaItem(buildItem(uri), startMs)
        p.setPlaybackSpeed(speed)
        p.playWhenReady = playWhenReady
        p.prepare()
        player = p
        this.playWhenReady = playWhenReady
    }

    /** Saves [engine] and rebuilds the player with it, keeping the position. */
    fun applyConfig(engine: EngineConfig) {
        store.saveConfig(engine)
        reopen(engine)
    }

    private fun reopen(engine: EngineConfig) {
        val uri = mediaUri ?: run { config = engine; return }
        val p = player
        open(uri, p?.currentPosition ?: 0, p?.playWhenReady ?: true, engine)
    }

    fun loadSubtitle(uri: Uri, name: String) {
        subtitleUri = uri
        subtitleName = name
        reopen(config)
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
        p.playWhenReady = !p.playWhenReady
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0))
    }

    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val duration = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, duration))
    }

    fun setPlaybackSpeed(value: Float) {
        speed = value
        player?.setPlaybackSpeed(value)
    }

    fun selectTrack(group: Tracks.Group, index: Int) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    fun disableTrackType(type: Int) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, true)
            .build()
    }

    fun isTrackTypeDisabled(type: Int): Boolean =
        player?.trackSelectionParameters?.disabledTrackTypes?.contains(type) == true

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        player?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analytics)
            it.release()
        }
        player = null
        isPlaying = false
    }

    private fun buildItem(uri: Uri): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        subtitleUri?.let { sub ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sub)
                        .setMimeType(subtitleMime(subtitleName.orEmpty()))
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setLabel(subtitleName)
                        .build(),
                ),
            )
        }
        return builder.build()
    }

    private fun subtitleMime(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "vtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "xml", "dfxp" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    private fun handleError(e: PlaybackException) {
        val next = if (e.errorCode in DECODER_ERRORS && config.autoFallback) config.fallback() else null
        val uri = mediaUri
        if (next != null && uri != null) {
            val position = player?.currentPosition ?: 0
            notice = "Decoder refused \"${config.summary()}\". Retrying with \"${next.summary()}\"."
            main.post { open(uri, position, true, next) }
        } else {
            error = "${e.errorCodeName}: ${e.cause?.message ?: e.message}"
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@TurboPlayer.isPlaying = isPlaying
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            this@TurboPlayer.playWhenReady = playWhenReady
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            isBuffering = playbackState == Player.STATE_BUFFERING
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            this@TurboPlayer.videoSize = videoSize
        }

        override fun onCues(cueGroup: CueGroup) {
            cues = cueGroup.cues
        }

        override fun onTracksChanged(tracks: Tracks) {
            this@TurboPlayer.tracks = tracks
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            this@TurboPlayer.decoderName = decoderName
        }
    }

    /** Called about twice a second by the player screen. */
    fun sampleStats() {
        val p = player ?: return
        val now = SystemClock.elapsedRealtime()
        val counters = p.videoDecoderCounters
        counters?.ensureUpdated()
        val rendered = counters?.renderedOutputBufferCount ?: 0
        val dropped = counters?.droppedBufferCount ?: 0
        val elapsed = now - lastSampleAt
        val fps = if (lastSampleAt > 0 && elapsed > 0 && rendered >= lastRendered) {
            (rendered - lastRendered) * 1000f / elapsed
        } else stats.renderedFps
        lastRendered = rendered
        lastSampleAt = now

        dropHistory.addLast(now to dropped)
        while (dropHistory.size > 1 && now - dropHistory.first().first > 5000) dropHistory.removeFirst()
        val droppedRecent = dropped - (dropHistory.firstOrNull()?.second ?: dropped)

        val format = p.videoFormat
        val name = decoderName
        if (format != null && name != null) updateAdvertised(name, format)

        stats = PlaybackStats(
            decoder = name ?: "—",
            hardware = name?.let(::isHardwareDecoder),
            resolution = format?.let { "${it.width}×${it.height}" } ?: "—",
            codec = format?.let(::codecLabel) ?: "—",
            dynamicRange = format?.let(::rangeLabel) ?: "—",
            videoFps = format?.frameRate?.takeIf { it > 0 } ?: 0f,
            bitrateMbps = format?.let { maxOf(it.bitrate, it.averageBitrate, it.peakBitrate) }
                ?.takeIf { it > 0 }?.let { it / 1_000_000f } ?: 0f,
            renderedFps = fps,
            dropped = dropped,
            droppedLast5s = droppedRecent,
            advertised = advertised,
            forced = forced,
            engine = engineApplied,
            thermal = thermalLabel(),
        )
    }

    private fun updateAdvertised(decoder: String, format: Format) {
        val key = "$decoder ${format.width}x${format.height}@${format.frameRate}"
        if (key == advertisedKey) return
        advertisedKey = key
        val mime = format.sampleMimeType ?: return
        val result = runCatching {
            val info = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.first { it.name == decoder }
            val video = info.getCapabilitiesForType(mime).videoCapabilities!!
            val fps = format.frameRate.takeIf { it > 0 }?.toDouble() ?: 30.0
            val ok = video.areSizeAndRateSupported(format.width, format.height, fps)
            val measured = runCatching { video.getAchievableFrameRatesFor(format.width, format.height) }
                .getOrNull()
            val limit = if (video.isSizeSupported(format.width, format.height)) {
                "≤%.0f fps".format(video.getSupportedFrameRatesFor(format.width, format.height).upper)
            } else "size not listed"
            val measuredText = measured?.let { " (measured %.0f–%.0f)".format(it.lower, it.upper) } ?: ""
            (if (ok) "within limit: $limit$measuredText" else "$limit$measuredText") to !ok
        }.getOrNull()
        advertised = result?.first ?: "unknown"
        forced = result?.second ?: false
    }

    private fun codecLabel(format: Format): String {
        val base = when (format.sampleMimeType) {
            MimeTypes.VIDEO_H265 -> "HEVC"
            MimeTypes.VIDEO_H264 -> "AVC"
            MimeTypes.VIDEO_VP9 -> "VP9"
            MimeTypes.VIDEO_AV1 -> "AV1"
            MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
            else -> format.sampleMimeType?.substringAfter('/') ?: "?"
        }
        val bits = format.colorInfo?.lumaBitdepth?.takeIf { it > 0 }
        return if (bits != null) "$base $bits-bit" else base
    }

    private fun rangeLabel(format: Format): String = when (format.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10/PQ"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> "SDR"
    }

    private fun thermalLabel(): String = when (powerManager?.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "cool"
        PowerManager.THERMAL_STATUS_LIGHT -> "light"
        PowerManager.THERMAL_STATUS_MODERATE -> "moderate (may throttle)"
        PowerManager.THERMAL_STATUS_SEVERE -> "severe (throttling)"
        PowerManager.THERMAL_STATUS_CRITICAL,
        PowerManager.THERMAL_STATUS_EMERGENCY,
        PowerManager.THERMAL_STATUS_SHUTDOWN,
        -> "critical"
        else -> "—"
    }
}
