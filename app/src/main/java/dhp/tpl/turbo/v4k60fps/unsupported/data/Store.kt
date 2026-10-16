package dhp.tpl.turbo.v4k60fps.unsupported.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import dhp.tpl.turbo.v4k60fps.unsupported.engine.EngineConfig
import dhp.tpl.turbo.v4k60fps.unsupported.engine.RateMode
import org.json.JSONArray
import org.json.JSONObject

data class RecentVideo(
    val uri: String,
    val name: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayed: Long,
)

/** Settings and the recent list, in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("turbo4k", Context.MODE_PRIVATE)

    fun loadConfig(): EngineConfig = EngineConfig(
        hardwareOnly = prefs.getBoolean("hardwareOnly", true),
        stealthStart = prefs.getBoolean("stealthStart", true),
        ignoreCapabilityCheck = prefs.getBoolean("ignoreCaps", true),
        rateMode = RateMode.entries.firstOrNull { it.name == prefs.getString("rateMode", null) }
            ?: RateMode.MAX,
        realtimePriority = prefs.getBoolean("realtimePriority", true),
        lowLatency = prefs.getBoolean("lowLatency", false),
        autoFallback = prefs.getBoolean("autoFallback", true),
        sustainedPerformance = prefs.getBoolean("sustained", false),
    )

    fun saveConfig(config: EngineConfig) = prefs.edit {
        putBoolean("hardwareOnly", config.hardwareOnly)
        putBoolean("stealthStart", config.stealthStart)
        putBoolean("ignoreCaps", config.ignoreCapabilityCheck)
        putString("rateMode", config.rateMode.name)
        putBoolean("realtimePriority", config.realtimePriority)
        putBoolean("lowLatency", config.lowLatency)
        putBoolean("autoFallback", config.autoFallback)
        putBoolean("sustained", config.sustainedPerformance)
    }

    var showStats: Boolean
        get() = prefs.getBoolean("showStats", true)
        set(value) = prefs.edit { putBoolean("showStats", value) }

    fun recents(): List<RecentVideo> {
        val array = runCatching { JSONArray(prefs.getString("recents", "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            RecentVideo(
                uri = o.getString("uri"),
                name = o.optString("name"),
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                lastPlayed = o.optLong("at"),
            )
        }
    }

    fun recent(uri: String): RecentVideo? = recents().firstOrNull { it.uri == uri }

    fun upsertRecent(video: RecentVideo) {
        val list = listOf(video) + recents().filter { it.uri != video.uri }
        saveRecents(list.take(40))
    }

    fun updatePosition(uri: String, positionMs: Long, durationMs: Long) {
        saveRecents(recents().map {
            if (it.uri == uri) it.copy(positionMs = positionMs, durationMs = durationMs) else it
        })
    }

    fun removeRecent(uri: String) = saveRecents(recents().filter { it.uri != uri })

    fun clearRecents() = saveRecents(emptyList())

    private fun saveRecents(list: List<RecentVideo>) {
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("uri", it.uri)
                    .put("name", it.name)
                    .put("pos", it.positionMs)
                    .put("dur", it.durationMs)
                    .put("at", it.lastPlayed),
            )
        }
        prefs.edit { putString("recents", array.toString()) }
    }
}

fun displayName(context: Context, uri: Uri): String {
    val fromProvider = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()
    return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
