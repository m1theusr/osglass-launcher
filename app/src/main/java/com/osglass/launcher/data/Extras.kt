package com.osglass.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Process
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

/* ------------------------------------------------------------------ Clima (Open-Meteo, sem chave) */

@Immutable
data class WeatherDay(val label: String, val code: Int, val max: Int, val min: Int)

@Immutable
data class Weather(val city: String, val temp: Int, val code: Int, val max: Int, val min: Int, val days: List<WeatherDay>)

object WeatherRepo {
    /** Cidade vazia = localização aproximada pelo IP (ipwho.is). */
    suspend fun load(city: String, fahrenheit: Boolean): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon, name) = if (city.isBlank()) {
                val o = JSONObject(http("https://ipwho.is/?fields=success,city,latitude,longitude"))
                Triple(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city"))
            } else {
                val q = URLEncoder.encode(city.trim(), "UTF-8")
                val r = JSONObject(http("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=pt")).getJSONArray("results").getJSONObject(0)
                Triple(r.getDouble("latitude"), r.getDouble("longitude"), r.optString("name", city))
            }
            val unit = if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val o = JSONObject(
                http("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5$unit"),
            )
            val cur = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val times = d.getJSONArray("time")
            val days = (0 until times.length()).map { i ->
                WeatherDay(
                    outFmt.format(inFmt.parse(times.getString(i))!!).trimEnd('.').replaceFirstChar { it.uppercase() },
                    d.getJSONArray("weather_code").getInt(i),
                    Math.round(d.getJSONArray("temperature_2m_max").getDouble(i)).toInt(),
                    Math.round(d.getJSONArray("temperature_2m_min").getDouble(i)).toInt(),
                )
            }
            Weather(name, Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), days[0].max, days[0].min, days.drop(1))
        }.getOrNull()
    }

    private fun http(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000; readTimeout = 8_000
            setRequestProperty("User-Agent", "OSGlass-TV")
        }
        return try { c.inputStream.use { it.readBytes().decodeToString() } } finally { c.disconnect() }
    }

    fun label(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1, 2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        in 51..57 -> "Garoa"
        in 61..67, in 80..82 -> "Chuva"
        in 71..77, 85, 86 -> "Neve"
        in 95..99 -> "Tempestade"
        else -> "—"
    }

    fun icon(code: Int): String = when (code) {
        0 -> "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
        1, 2 -> "M8 3v1.5M3.5 7.5H5M4.8 4.3l1 1M12 6.5a4 4 0 0 0-6.9 2.8M9 19h8a3.5 3.5 0 0 0 .4-7A5 5 0 0 0 8 13a3 3 0 0 0 1 6z"
        45, 48 -> "M7 12h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 5a3.5 3.5 0 0 0 1 7zM4 16h16M6 20h12"
        in 51..67, in 80..82 -> "M7 15h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 8a3.5 3.5 0 0 0 1 7zM8 18l-1 3M12 18l-1 3M16 18l-1 3"
        in 71..77, 85, 86 -> "M7 14h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 7a3.5 3.5 0 0 0 1 7zM8 18h.01M12 19h.01M16 18h.01M10 21h.01M14 21h.01"
        in 95..99 -> "M7 14h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 7a3.5 3.5 0 0 0 1 7zM12 14l-2 4h4l-2 4"
        else -> "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 11a3.5 3.5 0 0 0 1 7z"
    }
}

/* ------------------------------------------------------------------ Música tocando (ilha dinâmica) */

/** Só existe para o Android liberar a leitura das sessões de mídia ativas. */
class MediaListener : NotificationListenerService()

@Immutable
data class NowPlaying(
    val pkg: String,
    val title: String,
    val artist: String?,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

object Media {
    const val ADB_COMMAND = "adb shell cmd notification allow_listener com.osglass.launcher/com.osglass.launcher.data.MediaListener"

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun current(ctx: Context): NowPlaying? = runCatching {
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val sessions = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: return null
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART))
            ?.let { android.graphics.Bitmap.createScaledBitmap(it, 96, 96, true).asImageBitmap() }
        NowPlaying(
            c.packageName, title,
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            art, c.playbackState?.state == PlaybackState.STATE_PLAYING, c,
        )
    }.getOrNull()

    fun togglePlay(np: NowPlaying) {
        runCatching { if (np.playing) np.controller.transportControls.pause() else np.controller.transportControls.play() }
    }
}

/* ------------------------------------------------------------------ Apps recentes */

object Recents {
    const val ADB_COMMAND = "adb shell appops set com.osglass.launcher GET_USAGE_STATS allow"

    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** Pacotes usados nos últimos 3 dias, do mais recente para o mais antigo (só os que abrem). */
    fun load(ctx: Context, launchable: Set<String>): List<String> = runCatching {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3 * 24 * 3600_000L, now)
            .filter { it.lastTimeUsed > 0 && it.packageName in launchable && it.packageName != ctx.packageName }
            .groupBy { it.packageName }
            .map { (pkg, list) -> pkg to list.maxOf { it.lastTimeUsed } }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(10)
    }.getOrDefault(emptyList())
}
