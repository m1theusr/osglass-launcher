package com.osglass.launcher.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Leituras e ações nativas da TV que um app comum pode fazer no Android 12. */
object TvSystem {
    private fun audio(ctx: Context) = ctx.getSystemService(AudioManager::class.java)

    fun volumePercent(ctx: Context): Int {
        val am = audio(ctx)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    fun adjustVolume(ctx: Context, up: Boolean) {
        audio(ctx).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_PLAY_SOUND,
        )
    }

    fun isMuted(ctx: Context): Boolean = audio(ctx).isStreamMute(AudioManager.STREAM_MUSIC)

    fun setMuted(ctx: Context, muted: Boolean) {
        audio(ctx).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0,
        )
    }

    fun ipAddress(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }?.address?.hostAddress
    }.getOrNull()

    /** (usado, total) em bytes da partição de dados. */
    fun storage(): Pair<Long, Long> {
        val fs = StatFs(Environment.getDataDirectory().path)
        val total = fs.totalBytes
        return (total - fs.availableBytes) to total
    }

    fun totalMemoryMb(ctx: Context): Long {
        val info = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return info.totalMem / (1024 * 1024)
    }

    fun ownMemoryMb(ctx: Context): Long = runCatching {
        val am = ctx.getSystemService(ActivityManager::class.java)
        am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss / 1024L
    }.getOrDefault(0L)

    val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    val androidVersion: String get() = "Android ${Build.VERSION.RELEASE}"
}

/** Backup dos ajustes em JSON (Android/data/com.osglass.launcher/files/backups). Guarda os 20 mais recentes. */
object Backups {
    private const val KEEP = 20
    private val nameFmt = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val labelFmt = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.getDefault())

    private fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "backups").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.extension == "json" }.orEmpty().sortedByDescending { it.name }

    fun label(f: File): String = runCatching {
        labelFmt.format(nameFmt.parse(f.nameWithoutExtension.removePrefix("osglass-"))!!)
    }.getOrDefault(f.nameWithoutExtension)

    fun create(ctx: Context): File {
        val sp = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
        val json = JSONObject()
        sp.all.forEach { (k, v) ->
            val entry = JSONObject()
            when (v) {
                is Boolean -> entry.put("t", "b").put("v", v)
                is Int -> entry.put("t", "i").put("v", v)
                is String -> entry.put("t", "s").put("v", v)
                is Set<*> -> entry.put("t", "ss").put("v", v.joinToString("\u001f"))
                else -> return@forEach
            }
            json.put(k, entry)
        }
        val file = File(dir(ctx), "osglass-${nameFmt.format(Date())}.json")
        file.writeText(json.toString(2))
        list(ctx).drop(KEEP).forEach { it.delete() }
        return file
    }

    /** Restaura e reinicia o app para todas as telas lerem os valores novos. */
    fun restoreAndRestart(ctx: Context, file: File) {
        val json = JSONObject(file.readText())
        ctx.getSharedPreferences("glass", Context.MODE_PRIVATE).edit(commit = true) {
            clear()
            json.keys().forEach { k ->
                val e = json.getJSONObject(k)
                when (e.getString("t")) {
                    "b" -> putBoolean(k, e.getBoolean("v"))
                    "i" -> putInt(k, e.getInt("v"))
                    "s" -> putString(k, e.getString("v"))
                    "ss" -> putStringSet(k, e.getString("v").split("\u001f").filter { it.isNotEmpty() }.toSet())
                }
            }
        }
        ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
            ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        Runtime.getRuntime().exit(0)
    }
}
