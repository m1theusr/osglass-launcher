package com.osglass.launcher.ui

import android.app.ActivityManager
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent.KEYCODE_DPAD_UP
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.osglass.launcher.data.AppEntry
import com.osglass.launcher.data.Folder
import com.osglass.launcher.data.NowPlaying
import com.osglass.launcher.data.Recents
import com.osglass.launcher.data.TvSystem
import com.osglass.launcher.data.Weather
import com.osglass.launcher.data.WeatherRepo
import com.osglass.launcher.glass.liquidGlass

private val Dark = Color(0xFF111114)

/* ------------------------------------------------------------------ Clima */

@Composable
fun WeatherChip(w: Weather) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.liquidGlass(shape, rim = 0.9f).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val fg = com.osglass.launcher.glass.LocalGlassTheme.current.content
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val night = hour >= 19 || hour < 6
        GlyphIcon(if (night && w.code <= 2) Icons.MOON else WeatherRepo.icon(w.code), fg, 17.dp, filled = night && w.code <= 2)
        BasicText("${w.temp}°", style = LabelStyle.copy(color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null))
    }
}

fun greetingFor(name: String): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val g = when (h) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    return if (name.isBlank()) g else "$g, $name"
}

/* ------------------------------------------------------------------ Ilha dinâmica */

@Composable
fun Island(np: NowPlaying, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(0.6f, 450f), label = "island")
    val shape = RoundedCornerShape(50)
    val fg = if (focused) Dark else Color.White
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 6.dp, shape)
            .liquidGlass(shape, tint = Color.Black.copy(alpha = 0.45f), rim = 0.7f)
            .background(if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onToggle)
            .focusable()
            .padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            np.art?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.width(150.dp)) {
            BasicText(np.title, style = LabelStyle.copy(color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
            np.artist?.let { BasicText(it, style = LabelStyle.copy(color = fg.copy(alpha = 0.65f), fontSize = 11.sp, fontWeight = FontWeight.Normal, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        GlyphIcon(if (np.playing) PAUSE_ICON else Icons.PLAY, fg, 16.dp, filled = true)
    }
}

private const val PAUSE_ICON = "M7 5h3v14H7zM14 5h3v14h-3z"

/* ------------------------------------------------------------------ Pastas */

@Composable
fun FolderCard(folder: Folder, apps: List<AppEntry>, focusRequester: FocusRequester, onOpen: () -> Unit, glassIcons: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(0.62f, 420f), label = "folder")
    val sheen = remember { Animatable(-0.5f) }
    LaunchedEffect(focused) { if (focused) { sheen.snapTo(-0.5f); sheen.animateTo(1.5f, tween(950, easing = FastOutSlowInEasing)) } }
    Column(Modifier.width(CardW).zIndex(if (focused) 1f else 0f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(CardW, CardH)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (focused) 24.dp.toPx() else 2.dp.toPx()
                    shape = CardShape; clip = true
                }
                .liquidGlass(CardShape, tint = Color.White.copy(alpha = 0.14f), rim = if (glassIcons) 1f else 0.5f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onOpen)
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (focused) {
                        drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                apps.take(6).chunked(3).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.forEach { app ->
                            Box(Modifier.size(38.dp, 27.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                (app.banner ?: app.icon)?.let { img ->
                                    Image(img, null, if (app.banner != null) Modifier.fillMaxSize() else Modifier.size(20.dp), contentScale = ContentScale.Crop)
                                }
                            }
                        }
                    }
                }
            }
        }
        BasicText(folder.name, Modifier.padding(top = 12.dp), style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Pasta aberta: painel de vidro com os apps dela. */
@Composable
fun FolderOverlay(
    folder: Folder?,
    visible: Boolean,
    apps: List<AppEntry>,
    glassIcons: Boolean,
    onLaunch: (AppEntry, Rect) -> Unit,
    onMenu: (AppEntry) -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(160)) + scaleIn(spring(0.75f, 500f), initialScale = 0.92f), exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.96f)) {
        val first = remember { FocusRequester() }
        LaunchedEffect(folder?.name) { withFrameNanos { }; runCatching { first.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(34.dp)
            Column(
                Modifier
                    .shadow(40.dp, shape)
                    .liquidGlass(shape, tint = Color.White.copy(alpha = 0.12f), lens = 1.08f)
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup()
                    .padding(horizontal = 26.dp, vertical = 22.dp),
            ) {
                BasicText(folder?.name.orEmpty(), Modifier.padding(start = 4.dp, bottom = 16.dp), style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                apps.chunked(COLUMNS).forEachIndexed { r, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap), modifier = Modifier.padding(bottom = 6.dp)) {
                        line.forEachIndexed { c, app ->
                            AppCard(
                                app = app,
                                focusRequester = if (r == 0 && c == 0) first else remember(app.pkg) { FocusRequester() },
                                moving = false,
                                onClick = { onLaunch(app, it) },
                                onMenu = { onMenu(app) },
                                onMove = {},
                                onMoveDone = {},
                                glassIcons = glassIcons,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Apps recentes */

@Composable
fun RecentsOverlay(visible: Boolean, state: GlassState, onClose: () -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
        val ctx = LocalContext.current
        val hasAccess = remember { Recents.hasAccess(ctx) }
        val list = remember { mutableStateListOf<String>().apply { addAll(Recents.load(ctx, state.byPkg.keys)) } }
        var notice by remember { mutableStateOf<String?>(null) }
        var memTick by remember { mutableStateOf(0) }
        val total = remember { TvSystem.totalMemoryMb(ctx) }
        val free = remember(memTick) { state.availableMemoryMb() }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
            Row(Modifier.fillMaxWidth().padding(start = SafeX, end = SafeX, top = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Apps recentes", Modifier.weight(1f), style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold))
                val shape = RoundedCornerShape(50)
                Row(
                    Modifier.liquidGlass(shape).padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText("RAM %.1f GB usados de %.1f GB".format((total - free) / 1024f, total / 1024f), style = LabelStyle.copy(fontSize = 13.sp))
                    Box(Modifier.width(90.dp).height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(1f - free.toFloat() / total).height(5.dp).background(Color.White))
                    }
                }
            }
            if (!hasAccess) {
                Column(Modifier.align(Alignment.Center).width(560.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Para ver os apps recentes, libere o “Acesso ao uso” para o OSGlass.", style = LabelStyle.copy(fontSize = 16.sp))
                    GlassPill("Abrir acesso ao uso", { state.start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, Modifier.focusRequester(first), primary = true)
                    BasicText(
                        Recents.ADB_COMMAND,
                        Modifier.clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(10.dp),
                        style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    )
                }
            } else if (list.isEmpty()) {
                BasicText("Nenhum app aberto recentemente.", Modifier.align(Alignment.Center), style = LabelStyle.copy(fontSize = 16.sp))
            } else {
                LazyRow(
                    Modifier.align(Alignment.Center).focusProperties { onExit = { cancelFocusChange() } }.focusGroup(),
                    contentPadding = PaddingValues(horizontal = SafeX),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    items(list.toList(), key = { it }) { pkg ->
                        val app = state.byPkg[pkg] ?: return@items
                        RecentCard(
                            app,
                            if (pkg == list.first()) first else remember(pkg) { FocusRequester() },
                            onOpen = { onClose(); state.launchApp(pkg) },
                            onCloseApp = {
                                runCatching { ctx.getSystemService(ActivityManager::class.java).killBackgroundProcesses(pkg) }
                                list.remove(pkg)
                                memTick++
                                notice = "${app.label} fechado"
                            },
                        )
                    }
                }
            }
            BasicText(
                notice ?: "Aperte ▲ em um app para fechá-lo",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun RecentCard(app: AppEntry, requester: FocusRequester, onOpen: () -> Unit, onCloseApp: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 0.92f, spring(0.62f, 400f), label = "recent")
    val shape = RoundedCornerShape(22.dp)
    Column(Modifier.zIndex(if (focused) 1f else 0f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.padding(bottom = 12.dp).graphicsLayer { alpha = if (focused) 1f else 0.6f }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (app.banner ?: app.icon)?.let { Image(it, null, Modifier.size(30.dp, 18.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop) }
            BasicText(app.label, style = LabelStyle.copy(fontSize = 14.sp))
        }
        Box(
            Modifier
                .size(250.dp, 141.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (focused) 30.dp.toPx() else 6.dp.toPx(); this.shape = shape; clip = true }
                .liquidGlass(shape, tint = Color.White.copy(alpha = 0.12f))
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    if (e.nativeKeyEvent.keyCode == KEYCODE_DPAD_UP) { if (e.type == KeyEventType.KeyDown) onCloseApp(); true } else false
                }
                .onSelect(onOpen)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                app.banner != null -> Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app.icon != null -> Image(app.icon, null, Modifier.size(64.dp))
            }
        }
    }
}

@Composable
fun DotIndicator(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateFloatAsState(if (i == index) 16f else 6f, label = "dot")
            val c by animateColorAsState(Color.White.copy(alpha = if (i == index) 1f else 0.4f), label = "dotC")
            Box(Modifier.size(w.dp, 6.dp).clip(CircleShape).background(c))
        }
    }
}
