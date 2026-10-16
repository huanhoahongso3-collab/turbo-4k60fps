package dhp.tpl.turbo.v4k60fps.unsupported.engine

import android.content.Context
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCrypto
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

private const val FORMAT_SUPPORT_MASK = 0b111
private const val QTI_LOW_LATENCY = "vendor.qti-ext-dec-low-latency.enable"

fun isHardwareDecoder(name: String): Boolean =
    !name.startsWith("c2.android.") &&
        !name.startsWith("OMX.google.") &&
        !name.contains("ffmpeg", ignoreCase = true)

/**
 * Lists only hardware decoders for video, so ExoPlayer can't quietly pick c2.android.* just
 * because the software decoder claims 4K60 while the hardware one claims 4K30.
 */
class HardwareFirstSelector(private val hardwareOnly: Boolean) : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<MediaCodecInfo> {
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType, requiresSecureDecoder, requiresTunnelingDecoder,
        )
        if (!hardwareOnly || !MimeTypes.isVideo(mimeType)) return all
        val hardware = all.filter {
            it.hardwareAccelerated && !it.softwareOnly && isHardwareDecoder(it.name)
        }
        // No hardware decoder for this codec (e.g. AV1 on Snapdragon 778G): use software rather than fail.
        return hardware.ifEmpty { all }
    }
}

/**
 * The video renderer with the decoder limits removed:
 *  - formats that "exceed capabilities" are reported as handled, so the track is always played;
 *  - the operating rate / priority / low-latency keys are rewritten from [EngineConfig].
 */
class TurboVideoRenderer(
    builder: Builder,
    private val config: EngineConfig,
    private val onConfigured: (String) -> Unit,
) : MediaCodecVideoRenderer(builder) {

    override fun supportsFormat(mediaCodecSelector: MediaCodecSelector, format: Format): Int {
        val capabilities = super.supportsFormat(mediaCodecSelector, format)
        val support = RendererCapabilities.getFormatSupport(capabilities)
        if (config.ignoreCapabilityCheck && support == C.FORMAT_EXCEEDS_CAPABILITIES) {
            return (capabilities and FORMAT_SUPPORT_MASK.inv()) or C.FORMAT_HANDLED
        }
        return capabilities
    }

    override fun getCodecOperatingRateV23(
        targetPlaybackSpeed: Float,
        format: Format,
        streamFormats: Array<Format>,
    ): Float {
        val matched = super.getCodecOperatingRateV23(targetPlaybackSpeed, format, streamFormats)
        val fps = if (matched > 0) matched else 60f * targetPlaybackSpeed
        return when (config.rateMode) {
            RateMode.MAX -> Short.MAX_VALUE.toFloat()
            RateMode.BOOST -> fps * 2
            RateMode.MATCH -> fps
            RateMode.OFF -> -1f // CODEC_OPERATING_RATE_UNSET
        }
    }

    override fun getMediaCodecConfiguration(
        codecInfo: MediaCodecInfo,
        format: Format,
        crypto: MediaCrypto?,
        codecOperatingRate: Float,
    ): MediaCodecAdapter.Configuration {
        val configuration =
            super.getMediaCodecConfiguration(codecInfo, format, crypto, codecOperatingRate)
        val mediaFormat = configuration.mediaFormat

        if (!config.realtimePriority) mediaFormat.removeKey(MediaFormat.KEY_PRIORITY)
        if (config.rateMode == RateMode.OFF) mediaFormat.removeKey(MediaFormat.KEY_OPERATING_RATE)
        if (config.lowLatency) {
            if (Build.VERSION.SDK_INT >= 30 &&
                codecInfo.capabilities?.isFeatureSupported(CodecCapabilities.FEATURE_LowLatency) == true
            ) {
                mediaFormat.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            }
            if (codecInfo.name.startsWith("c2.qti.")) mediaFormat.setInteger(QTI_LOW_LATENCY, 1)
        }

        onConfigured(describe(mediaFormat))
        return configuration
    }

    private fun describe(mediaFormat: MediaFormat): String {
        fun valueOf(key: String): String = if (!mediaFormat.containsKey(key)) "unset" else
            runCatching { "%.0f".format(mediaFormat.getFloat(key)) }
                .getOrElse { runCatching { mediaFormat.getInteger(key).toString() }.getOrDefault("?") }
        val priority = when (valueOf(MediaFormat.KEY_PRIORITY)) {
            "0" -> "realtime"
            "unset" -> "default"
            else -> "best-effort"
        }
        val lowLatency = if (Build.VERSION.SDK_INT >= 30 && mediaFormat.containsKey(MediaFormat.KEY_LOW_LATENCY)) "on" else "off"
        return "rate=${valueOf(MediaFormat.KEY_OPERATING_RATE)} · prio=$priority · lowlat=$lowLatency"
    }
}

class TurboRenderersFactory(
    context: Context,
    private val config: EngineConfig,
    private val onConfigured: (String) -> Unit,
) : DefaultRenderersFactory(context) {

    init {
        // With fallback off, a refused hardware decoder surfaces as an error (and our own,
        // smarter fallback in TurboPlayer) instead of silently dropping to software.
        setEnableDecoderFallback(false)
        setMediaCodecSelector(HardwareFirstSelector(config.hardwareOnly))
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        val builder = MediaCodecVideoRenderer.Builder(context)
            .setMediaCodecSelector(mediaCodecSelector)
            .setCodecAdapterFactory(DefaultMediaCodecAdapterFactory(context))
            .setAllowedJoiningTimeMs(allowedVideoJoiningTimeMs)
            .setEnableDecoderFallback(enableDecoderFallback)
            .setEventHandler(eventHandler)
            .setEventListener(eventListener)
            .setMaxDroppedFramesToNotify(50)
        out.add(TurboVideoRenderer(builder, config, onConfigured))
    }
}
