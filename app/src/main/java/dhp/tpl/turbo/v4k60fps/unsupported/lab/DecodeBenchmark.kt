package dhp.tpl.turbo.v4k60fps.unsupported.lab

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import dhp.tpl.turbo.v4k60fps.unsupported.engine.RateMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

data class BenchResult(
    val mode: RateMode,
    val decoder: String = "—",
    val resolution: String = "—",
    val videoFps: Float = 0f,
    val averageFps: Float = 0f,
    val perSecond: List<Float> = emptyList(),
    val error: String? = null,
)

/**
 * Decodes the file on the hardware decoder as fast as it will go (no display, no A/V sync),
 * so the result is the decoder's real ceiling for this exact file.
 */
object DecodeBenchmark {
    private const val TIMEOUT_US = 2_000L

    suspend fun run(
        context: Context,
        uri: Uri,
        mode: RateMode,
        seconds: Int,
        onProgress: (Float) -> Unit,
    ): BenchResult = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var reader: ImageReader? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return@withContext BenchResult(mode, error = "No video track in this file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val videoFps = frameRateOf(format)
            val name = CodecProbe.hardwareDecoderFor(mime)
                ?: return@withContext BenchResult(mode, error = "No hardware decoder for $mime")

            // mpv-style start: hide the fps so the capacity check lets 4K60 start,
            // then ask for the clock once the decoder runs (same as the player).
            format.removeKey(MediaFormat.KEY_FRAME_RATE)
            val rate = when (mode) {
                RateMode.MAX -> Short.MAX_VALUE.toFloat()
                RateMode.BOOST -> videoFps * 2
                RateMode.MATCH -> videoFps
                RateMode.OFF -> -1f
            }

            reader = ImageReader.newInstance(
                width, height, ImageFormat.PRIVATE, 4, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
            )
            val decoder = MediaCodec.createByCodecName(name)
            codec = decoder
            try {
                decoder.configure(format, reader.surface, null, 0)
                decoder.start()
                if (rate > 0) {
                    runCatching {
                        decoder.setParameters(android.os.Bundle().apply {
                            putFloat(MediaFormat.KEY_OPERATING_RATE, rate)
                            putInt(MediaFormat.KEY_PRIORITY, 0)
                        })
                    }
                }
            } catch (e: Exception) {
                return@withContext BenchResult(
                    mode, decoder = name, resolution = "${width}×$height", videoFps = videoFps,
                    error = "Decoder refused this setting: ${e.message ?: e.javaClass.simpleName}",
                )
            }

            val info = MediaCodec.BufferInfo()
            val buckets = IntArray(seconds)
            val start = SystemClock.elapsedRealtimeNanos()
            val end = start + seconds * 1_000_000_000L
            var frames = 0
            var inputDone = false
            var lastProgress = 0L

            while (isActive) {
                val now = SystemClock.elapsedRealtimeNanos()
                if (now >= end) break

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (!endOfStream) {
                        frames++
                        val second = ((now - start) / 1_000_000_000L).toInt().coerceIn(0, seconds - 1)
                        buckets[second]++
                    } else {
                        // Short clip: loop it until the time is up.
                        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        decoder.flush()
                        inputDone = false
                    }
                }

                if (now - lastProgress > 100_000_000L) {
                    lastProgress = now
                    onProgress((now - start).toFloat() / (end - start))
                }
            }

            val elapsedSeconds = (SystemClock.elapsedRealtimeNanos() - start) / 1e9f
            BenchResult(
                mode = mode,
                decoder = name,
                resolution = "${width}×$height",
                videoFps = videoFps,
                averageFps = frames / elapsedSeconds,
                perSecond = buckets.map { it.toFloat() },
            )
        } catch (e: Exception) {
            BenchResult(mode, error = e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { reader?.close() }
            extractor.release()
            onProgress(1f)
        }
    }

    private fun frameRateOf(format: MediaFormat): Float {
        if (!format.containsKey(MediaFormat.KEY_FRAME_RATE)) return 60f
        return runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
            .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(60f) }
    }
}
