package com.osglass.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Um item de conteúdo publicado por um app (canal de destaques ou "Continuar assistindo"). */
@Immutable
data class ContentItem(
    val key: String,
    val pkg: String,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val image: String?,
    val aspect: Float,
    val intentUri: String?,
    val progress: Float?,
)

/**
 * Lê o conteúdo que os apps publicam no TvProvider (o mesmo que o Projectivy mostra).
 * Precisa de READ_TV_LISTINGS (o usuário concede numa janela do sistema).
 * As recomendações personalizadas do Google TV ("Para você") não ficam aqui — são do servidor do Google.
 */
object TvContent {
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    private const val MAX_PER_APP = 15

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    private val PROGRAM_COLUMNS = arrayOf(
        TvContract.PreviewPrograms._ID,
        TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME,
        TvContract.PreviewPrograms.COLUMN_TITLE,
        TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE,
        TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION,
        TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI,
        TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI,
        TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
        TvContract.PreviewPrograms.COLUMN_INTENT_URI,
        TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS,
        TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
    )

    /** Canais de destaque agrupados por pacote, na ordem em que os canais aparecem. */
    suspend fun loadChannels(ctx: Context): Map<String, List<ContentItem>> = withContext(Dispatchers.IO) {
        if (!hasPermission(ctx)) return@withContext emptyMap()
        val cr = ctx.contentResolver
        val out = LinkedHashMap<String, MutableList<ContentItem>>()
        runCatching {
            cr.query(
                TvContract.Channels.CONTENT_URI,
                arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val pkg = c.getString(1) ?: continue
                    if (c.getString(2) != TvContract.Channels.TYPE_PREVIEW) continue
                    val list = out.getOrPut(pkg) { mutableListOf() }
                    if (list.size >= MAX_PER_APP) continue
                    list += queryPrograms(cr, TvContract.buildPreviewProgramsUriForChannel(id), pkg, null)
                        .take(MAX_PER_APP - list.size)
                }
            }
        }
        out.filterValues { it.isNotEmpty() }
    }

    suspend fun loadWatchNext(ctx: Context): List<ContentItem> = withContext(Dispatchers.IO) {
        if (!hasPermission(ctx)) return@withContext emptyList()
        runCatching {
            queryPrograms(
                ctx.contentResolver, TvContract.WatchNextPrograms.CONTENT_URI, null,
                "${TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS} DESC",
            ).take(20)
        }.getOrDefault(emptyList())
    }

    private fun queryPrograms(cr: ContentResolver, uri: Uri, fallbackPkg: String?, sort: String?): List<ContentItem> {
        val items = mutableListOf<ContentItem>()
        runCatching {
            cr.query(uri, PROGRAM_COLUMNS, null, null, sort)?.use { c ->
                while (c.moveToNext()) {
                    val title = c.str(TvContract.PreviewPrograms.COLUMN_TITLE) ?: continue
                    val pkg = c.str(TvContract.PreviewPrograms.COLUMN_PACKAGE_NAME) ?: fallbackPkg ?: continue
                    val duration = c.long(TvContract.PreviewPrograms.COLUMN_DURATION_MILLIS)
                    val position = c.long(TvContract.PreviewPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS)
                    items += ContentItem(
                        key = "$uri#${c.long(TvContract.PreviewPrograms._ID)}",
                        pkg = pkg,
                        title = title,
                        subtitle = listOfNotNull(
                            c.str(TvContract.PreviewPrograms.COLUMN_SEASON_DISPLAY_NUMBER)?.let { "T$it" },
                            c.str(TvContract.PreviewPrograms.COLUMN_EPISODE_DISPLAY_NUMBER)?.let { "E$it" },
                            c.str(TvContract.PreviewPrograms.COLUMN_EPISODE_TITLE),
                        ).joinToString(" · ").ifBlank { null },
                        description = c.str(TvContract.PreviewPrograms.COLUMN_SHORT_DESCRIPTION),
                        image = c.str(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI)
                            ?: c.str(TvContract.PreviewPrograms.COLUMN_THUMBNAIL_URI),
                        aspect = aspectOf(c.int(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO)),
                        intentUri = c.str(TvContract.PreviewPrograms.COLUMN_INTENT_URI),
                        progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
                    )
                }
            }
        }
        return items
    }

    private fun aspectOf(code: Int?): Float = when (code) {
        1 /* ASPECT_RATIO_3_2 */ -> 3f / 2f
        2 /* ASPECT_RATIO_4_3 */ -> 4f / 3f
        3 /* ASPECT_RATIO_1_1 */ -> 1f
        4 /* ASPECT_RATIO_2_3 */ -> 2f / 3f
        5 /* ASPECT_RATIO_MOVIE_POSTER */ -> 1f / 1.441f
        else -> 16f / 9f
    }

    fun open(ctx: Context, item: ContentItem) {
        val intent = item.intentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
            ?: AppLoader.launchIntent(ctx, item.pkg) ?: return
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { AppLoader.launchIntent(ctx, item.pkg)?.let { i -> runCatching { ctx.startActivity(i) } } }
    }

    private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }
    private fun Cursor.long(col: String): Long = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
    private fun Cursor.int(col: String): Int? = getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getInt(it) }
}

@Immutable
data class TvInputEntry(val id: String, val label: String)

/** Entradas HDMI físicas, pra virar atalhos no topo da tela. */
object TvInputs {
    fun list(ctx: Context): List<TvInputEntry> = runCatching {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        tim.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(ctx) }
            .map { info ->
                val custom = info.loadCustomLabel(ctx)?.toString()?.takeIf { it.isNotBlank() }
                TvInputEntry(info.id, custom ?: info.loadLabel(ctx).toString())
            }
            .sortedBy { it.label }
    }.getOrDefault(emptyList())

    fun open(ctx: Context, id: String) {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { AppLoader.launchIntent(ctx, "com.tcl.tv")?.let { runCatching { ctx.startActivity(it) } } }
    }
}

/** Carregador de imagens mínimo: memória (LRU) + disco (cacheDir) + rede/content://. */
object Images {
    private val memory = LruCache<String, ImageBitmap>(48)
    private val large = LruCache<String, ImageBitmap>(3)

    /** Imagens de tela cheia (destaque): cache separado e pequeno pra não estourar a RAM. */
    suspend fun loadLarge(ctx: Context, uri: String, maxWidthPx: Int): ImageBitmap? {
        large.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { readBytes(ctx, uri)?.let { decode(it, maxWidthPx)?.asImageBitmap() } }.getOrNull()
        }?.also { large.put(uri, it) }
    }

    suspend fun load(ctx: Context, uri: String, maxWidthPx: Int): ImageBitmap? {
        memory.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = readBytes(ctx, uri) ?: return@runCatching null
                decode(bytes, maxWidthPx)?.asImageBitmap()
            }.getOrNull()?.also { memory.put(uri, it) }
        }
    }

    private fun readBytes(ctx: Context, uri: String): ByteArray? {
        if (!uri.startsWith("http")) {
            return ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        }
        val dir = File(ctx.cacheDir, "img").apply { mkdirs() }
        val file = File(dir, sha1(uri))
        if (file.exists()) return file.readBytes()
        val conn = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            instanceFollowRedirects = true
        }
        val bytes = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        file.writeBytes(bytes)
        trim(dir)
        return bytes
    }

    private fun decode(bytes: ByteArray, maxW: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        )
    }

    private fun trim(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= 400) return
        files.sortedBy { it.lastModified() }.take(files.size - 300).forEach { it.delete() }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
