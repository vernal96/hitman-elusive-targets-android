package com.example.hitmapstargets

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class TargetsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns current API results and only caches fully parsed successful responses. */
    suspend fun fetch(): List<ElusiveTarget> = withContext(Dispatchers.IO) {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "HitmapsTargets/1.0 Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Hitmaps API: HTTP ${connection.responseCode}")
            }
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val result = parseTargets(raw)
            prefs.edit()
                .putString(KEY_CACHE, raw)
                .putLong(KEY_SYNC_TIME, System.currentTimeMillis())
                .apply()
            result
        } finally {
            connection.disconnect()
        }
    }

    fun cached(): List<ElusiveTarget> = runCatching {
        parseTargets(prefs.getString(KEY_CACHE, null) ?: return emptyList())
    }.getOrDefault(emptyList())

    fun lastSync(): Long = prefs.getLong(KEY_SYNC_TIME, 0)

    companion object {
        const val API_URL = "https://api.hitmaps.com/api/web/home"
        const val PREFS_NAME = "hitmaps_targets"
        const val KEY_CACHE = "targets_json"
        const val KEY_SYNC_TIME = "last_sync"

        fun parseTargets(raw: String): List<ElusiveTarget> {
            val array = JSONObject(raw).getJSONArray("elusiveTargets")
            return buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val start = Instant.parse(item.getString("beginningTime"))
                    val end = Instant.parse(item.getString("endingTime"))
                    if (!end.isAfter(start)) continue
                    add(
                        ElusiveTarget(
                            id = item.getLong("id"),
                            name = item.getString("name"),
                            missionUrl = item.optString("missionUrl"),
                            imageUrl = item.optString("tileUrl").takeIf { it.startsWith("https://") },
                            startsAt = start,
                            endsAt = end,
                        )
                    )
                }
            }.sortedWith(compareBy<ElusiveTarget> { it.startsAt }.thenBy { it.id })
        }
    }
}
