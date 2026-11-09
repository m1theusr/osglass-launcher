package com.osglass.launcher.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.osglass.launcher.data.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Papel de parede já recortado no tamanho exato da tela ([sharp]) e uma cópia pequena,
 * desfocada e saturada ([frosted]) que os painéis de vidro amostram.
 *
 * O desfoque é feito UMA vez, ao trocar de papel de parede. Cada painel de vidro só desenha
 * o pedaço de [frosted] que fica atrás dele — custo por quadro praticamente zero, o que é o
 * que permite ter "vidro" numa TV com 2 GB de RAM e Android 12 (sem RuntimeShader/AGSL).
 */
class Backdrop(
    val id: String,
    val sharp: ImageBitmap,
    val frosted: ImageBitmap,
    val screenWidth: Int,
    val screenHeight: Int,
)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

object Wallpapers {
    private const val ASSET_DIR = "wallpapers"
    const val DEFAULT = "asset:$ASSET_DIR/glass-aurora.jpg"

    /** Pasta onde dá pra jogar imagens via `adb push` sem precisar de permissão. */
    fun userDir(ctx: Context): File? = ctx.getExternalFilesDir("wallpapers")?.apply { mkdirs() }

    fun list(ctx: Context): List<String> {
        val assets = ctx.assets.list(ASSET_DIR).orEmpty().sorted().map { "asset:$ASSET_DIR/$it" }
        val files = userDir(ctx)?.listFiles { f ->
            f.isFile && f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }.orEmpty().sortedBy { it.name }.map { "file:${it.absolutePath}" }
        return assets + files
    }

    fun displayName(id: String): String =
        id.substringAfterLast('/').substringBeforeLast('.').removePrefix("glass-")
            .replace('-', ' ').replaceFirstChar { it.uppercase() }

    suspend fun load(ctx: Context, id: String, width: Int, height: Int): Backdrop =
        withContext(Dispatchers.Default) {
            val src = decode(ctx, id, width, height) ?: decode(ctx, DEFAULT, width, height)!!
            val sharp = centerCrop(src, width, height)
            if (sharp !== src) src.recycle()
            val frosted = frost(sharp)
            Backdrop(id, sharp.asImageBitmap(), frosted.asImageBitmap(), width, height)
        }

    private val thumbs = HashMap<String, ImageBitmap>()

    /** Miniatura 16:9 para os Ajustes e as boas-vindas. */
    suspend fun thumbnail(ctx: Context, id: String): ImageBitmap? {
        thumbs[id]?.let { return it }
        return withContext(Dispatchers.IO) {
            decode(ctx, id, 320, 180)?.let { src ->
                centerCrop(src, 256, 144).also { if (it !== src) src.recycle() }.asImageBitmap()
            }
        }?.also { thumbs[id] = it }
    }

    /** Destaque em tela cheia: a imagem do app vira o fundo e o vidro passa a desfocar ela. */
    suspend fun hero(ctx: Context, uri: String, width: Int, height: Int): Backdrop? {
        val img = Images.loadLarge(ctx, uri, width) ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val frosted = frost(cropToAspect(img.asAndroidBitmap(), width, height))
                Backdrop(uri, img, frosted.asImageBitmap(), width, height)
            }.getOrNull()
        }
    }

    private fun cropToAspect(src: Bitmap, w: Int, h: Int): Bitmap {
        val target = w.toFloat() / h
        val cur = src.width.toFloat() / src.height
        return if (cur > target) {
            val cw = (src.height * target).toInt()
            Bitmap.createBitmap(src, (src.width - cw) / 2, 0, cw, src.height)
        } else {
            val ch = (src.width / target).toInt()
            Bitmap.createBitmap(src, 0, (src.height - ch) / 2, src.width, ch)
        }
    }

    private fun decode(ctx: Context, id: String, reqW: Int, reqH: Int): Bitmap? = runCatching {
        fun open() = when {
            id.startsWith("asset:") -> ctx.assets.open(id.removePrefix("asset:"))
            id.startsWith("file:") -> File(id.removePrefix("file:")).inputStream()
            else -> error("id inválido: $id")
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqW && bounds.outHeight / (sample * 2) >= reqH) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        open().use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val cw = (w / scale).toInt()
        val ch = (h / scale).toInt()
        val left = (src.width - cw) / 2
        val top = (src.height - ch) / 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            src, Rect(left, top, left + cw, top + ch), Rect(0, 0, w, h),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return out
    }

    /** Reduz pra 1/6, aplica 3 passadas de box blur (≈ gaussiano) e aumenta a saturação. */
    private fun frost(sharp: Bitmap): Bitmap {
        val w = (sharp.width / 6).coerceAtLeast(16)
        val h = (sharp.height / 6).coerceAtLeast(9)
        val small = Bitmap.createScaledBitmap(sharp, w, h, true)
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        small.getPixels(a, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlur(a, b, w, h, radius = 5, horizontal = true)
            boxBlur(b, a, w, h, radius = 5, horizontal = false)
        }
        small.setPixels(a, 0, w, 0, 0, w, h)

        // Vidro da Apple "puxa" a cor do fundo: saturação maior e um pouco mais de brilho.
        val cm = ColorMatrix().apply { setSaturation(1.3f) }
        cm.postConcat(ColorMatrix().apply { setScale(1.0f, 1.0f, 1.0f, 1f) })
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(small, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        small.recycle()
        return out
    }

    private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * radius + 1
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (i in -radius..radius) {
                val p = src[idx(i)]
                sa += p ushr 24; sr += (p shr 16) and 0xff; sg += (p shr 8) and 0xff; sb += p and 0xff
            }
            for (i in 0 until len) {
                dst[idx(i)] = ((sa / div) shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val pOut = src[idx(i - radius)]
                val pIn = src[idx(i + radius + 1)]
                sa += (pIn ushr 24) - (pOut ushr 24)
                sr += ((pIn shr 16) and 0xff) - ((pOut shr 16) and 0xff)
                sg += ((pIn shr 8) and 0xff) - ((pOut shr 8) and 0xff)
                sb += (pIn and 0xff) - (pOut and 0xff)
            }
        }
    }
}
