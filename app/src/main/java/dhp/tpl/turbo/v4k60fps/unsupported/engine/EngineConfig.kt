package dhp.tpl.turbo.v4k60fps.unsupported.engine

/** How hard we ask the hardware decoder to clock up, via MediaFormat.KEY_OPERATING_RATE. */
enum class RateMode(val label: String, val detail: String) {
    MAX("Max", "Asks for the decoder's highest clock (operating rate 32767)."),
    BOOST("Boost", "Asks for twice the video's frame rate."),
    MATCH("Match", "Asks for exactly the video's frame rate, like normal players."),
    OFF("Off", "Sends no rate and lets the driver decide, like mpv."),
}

data class EngineConfig(
    /** Drop Android's software decoders (c2.android.*, OMX.google.*) for video. */
    val hardwareOnly: Boolean = true,
    /**
     * Start the decoder the way mpv does (no frame rate, rate or priority), so Qualcomm's
     * capacity check at startup doesn't reject 4K60, then ask for the clock once it runs.
     */
    val stealthStart: Boolean = true,
    /** Report 4K60 as playable even when the codec's advertised limits say otherwise. */
    val ignoreCapabilityCheck: Boolean = true,
    val rateMode: RateMode = RateMode.MAX,
    /** MediaFormat.KEY_PRIORITY = 0 (realtime). */
    val realtimePriority: Boolean = true,
    /** MediaFormat.KEY_LOW_LATENCY plus Qualcomm's vendor low-latency switch. */
    val lowLatency: Boolean = false,
    /** If the decoder refuses a setting, retry with the next safer one instead of failing. */
    val autoFallback: Boolean = true,
    /** Window.setSustainedPerformanceMode, on phones that support it. */
    val sustainedPerformance: Boolean = false,
) {
    /** The next, safer config to try after the decoder refused this one; null when nothing is left. */
    fun fallback(): EngineConfig? = when {
        !stealthStart -> copy(stealthStart = true)
        lowLatency -> copy(lowLatency = false)
        rateMode == RateMode.MAX -> copy(rateMode = RateMode.BOOST)
        rateMode == RateMode.BOOST -> copy(rateMode = RateMode.MATCH)
        rateMode == RateMode.MATCH -> copy(rateMode = RateMode.OFF)
        realtimePriority -> copy(realtimePriority = false)
        hardwareOnly -> copy(hardwareOnly = false)
        else -> null
    }

    fun summary(): String = buildList {
        add(if (hardwareOnly) "HW only" else "HW + SW")
        add("${rateMode.label} clock")
        if (stealthStart) add("mpv-style start")
        if (ignoreCapabilityCheck) add("limits ignored")
        if (lowLatency) add("low latency")
    }.joinToString(" · ")
}
