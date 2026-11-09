package com.osglass.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [banner] = arte 16:9 de app de TV; quando não existe, o card usa [icon] sobre vidro. */
@Immutable
data class AppEntry(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

object AppLoader {
    suspend fun load(ctx: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, AppEntry>()

        fun query(category: String): List<ResolveInfo> =
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

        fun add(ri: ResolveInfo, tv: Boolean) {
            val ai = ri.activityInfo
            if (ai.packageName == ctx.packageName || ai.packageName in result) return
            val banner = if (tv) (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)) else null
            result[ai.packageName] = AppEntry(
                pkg = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                banner = banner?.toBitmap(320, 180)?.asImageBitmap(),
                icon = if (banner == null) ai.loadIcon(pm)?.toBitmap(160, 160)?.asImageBitmap() else null,
            )
        }

        query(Intent.CATEGORY_LEANBACK_LAUNCHER).forEach { add(it, tv = true) }
        query(Intent.CATEGORY_LAUNCHER).forEach { add(it, tv = false) }
        result.values.sortedBy { it.label.lowercase() }
    }

    fun launchIntent(ctx: Context, pkg: String): Intent? {
        val pm = ctx.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private fun Drawable.toBitmap(w: Int, h: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null && bitmap.width >= w / 2) {
            return Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, w, h)
        draw(c)
        return bmp
    }
}
