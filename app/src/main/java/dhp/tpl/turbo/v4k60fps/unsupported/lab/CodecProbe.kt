package dhp.tpl.turbo.v4k60fps.unsupported.lab

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build

data class DecoderReport(
    val name: String,
    val mime: String,
    val hardware: Boolean,
    val maxSize: String,
    val limit4k: String,
    val measured4k: String,
    val performancePoints: List<String>,
)

/** Reads what each video decoder advertises (the "lock" lives in these numbers). */
object CodecProbe {
    private val videoMimes = listOf(
        "video/hevc", "video/avc", "video/x-vnd.on2.vp9", "video/av01", "video/dolby-vision",
    )

    fun videoDecoders(): List<DecoderReport> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder }
            .flatMap { info ->
                info.supportedTypes
                    .filter { type -> videoMimes.any { it.equals(type, ignoreCase = true) } }
                    .mapNotNull { mime -> runCatching { describe(info, mime) }.getOrNull() }
            }
            .sortedWith(compareBy({ !it.hardware }, { videoMimes.indexOf(it.mime.lowercase()) }))

    private fun describe(info: MediaCodecInfo, mime: String): DecoderReport {
        val video = info.getCapabilitiesForType(mime).videoCapabilities!!
        val sizeOk = video.isSizeSupported(3840, 2160)
        val limit = if (sizeOk) {
            "up to %.0f fps".format(video.getSupportedFrameRatesFor(3840, 2160).upper)
        } else "4K not listed"
        val measured = runCatching { video.getAchievableFrameRatesFor(3840, 2160) }.getOrNull()
            ?.let { "%.0f–%.0f fps".format(it.lower, it.upper) } ?: "not published"
        val points = video.supportedPerformancePoints?.map { it.toString() } ?: emptyList()
        return DecoderReport(
            name = info.name,
            mime = mime,
            hardware = info.isHardwareAccelerated && !info.isSoftwareOnly,
            maxSize = "${video.supportedWidths.upper}×${video.supportedHeights.upper}",
            limit4k = limit,
            measured4k = measured,
            performancePoints = points,
        )
    }

    fun hardwareDecoderFor(mime: String): String? =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            !info.isEncoder &&
                info.isHardwareAccelerated &&
                !info.isSoftwareOnly &&
                !info.name.endsWith(".secure") &&
                info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }?.name

    /** Vendor-specific knobs the decoder exposes (Android 12+). */
    fun vendorParameters(name: String): List<String> {
        if (Build.VERSION.SDK_INT < 31) return listOf("Needs Android 12+")
        return runCatching {
            val codec = MediaCodec.createByCodecName(name)
            try {
                codec.supportedVendorParameters.sorted()
            } finally {
                codec.release()
            }
        }.getOrElse { listOf("Could not open: ${it.message}") }
    }
}
