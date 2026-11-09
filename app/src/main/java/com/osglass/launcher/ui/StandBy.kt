package com.osglass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.Images
import com.osglass.launcher.data.WeatherRepo
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.LocalBackdrop
import com.osglass.launcher.glass.liquidGlass
import kotlinx.coroutines.delay

private enum class SbMode(val label: String) { Clock("Relógio"), Weather("Clima"), Photos("Fotos"), Wallpaper("Papel de parede") }

/**
 * StandBy: tela de descanso do OSGlass. "Aleatório" alterna relógio, clima, fotos (artes dos destaques dos apps)
 * e papel de parede, como o protetor de tela "Aleatório" do tvOS 27.2. Qualquer botão volta.
 */
@Composable
fun StandByScreen(state: GlassState, wallpaper: Backdrop?, onExit: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() } }
    BackHandler { onExit() }

    val photos = remember { state.channels.values.flatten().mapNotNull { it.image }.distinct().shuffled().take(20) }
    val weather = state.weather
    val modes = remember(settings.standbyStyle, weather != null) {
        val all = buildList {
            add(SbMode.Clock)
            if (weather != null) add(SbMode.Weather)
            if (photos.isNotEmpty()) add(SbMode.Photos)
            add(SbMode.Wallpaper)
        }
        when (settings.standbyStyle) {
            "Aleatório" -> all
            else -> listOf(all.firstOrNull { it.label == settings.standbyStyle } ?: SbMode.Clock)
        }
    }
    var modeIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(modes) { while (modes.size > 1) { delay(25_000); modeIndex = (modeIndex + 1) % modes.size } }
    val mode = modes[modeIndex.coerceIn(0, modes.lastIndex)]

    val time by clockText(if (settings.clock24h) "HH:mm" else "h:mm")
    val date by clockText("EEEE, d 'de' MMMM")
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(-14f, 14f, infiniteRepeatable(tween(60_000), RepeatMode.Reverse), label = "driftY")

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { if (it.type == KeyEventType.KeyUp) onExit(); true }
            .focusable(),
    ) {
        Crossfade(mode, animationSpec = tween(1_200), label = "sbMode") { m ->
            Box(Modifier.fillMaxSize()) {
                when (m) {
                    SbMode.Clock -> {
                        wallpaper?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.3f) }
                        Column(Modifier.align(Alignment.Center).graphicsLayer { translationY = drift.dp.toPx() }, horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText(time, style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 150.sp, fontWeight = FontWeight.Thin, letterSpacing = (-4).sp))
                            BasicText(date.replaceFirstChar { it.uppercase() }, style = LabelStyle.copy(fontSize = 18.sp, color = Color.White.copy(alpha = 0.6f)))
                        }
                    }
                    SbMode.Weather -> {
                        wallpaper?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.4f) }
                        val w = weather
                        if (w != null) {
                            Row(
                                Modifier.align(Alignment.Center).graphicsLayer { translationY = drift.dp.toPx() },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(50.dp),
                            ) {
                                Column {
                                    BasicText(w.city, style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f)))
                                    BasicText("${w.temp}°", style = TextStyle(color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Thin))
                                    BasicText("${WeatherRepo.label(w.code)} · máx. ${w.max}° mín. ${w.min}°", style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)))
                                }
                                androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides wallpaper) {
                                    Row(Modifier.liquidGlass(RoundedCornerShape(24.dp), tint = Color.White.copy(alpha = 0.1f)).padding(8.dp)) {
                                        w.days.take(4).forEach { d ->
                                            Column(Modifier.width(74.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                BasicText(d.label, style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                                GlyphIcon(WeatherRepo.icon(d.code), Color.White, 22.dp)
                                                BasicText("${d.max}°", style = LabelStyle.copy(fontSize = 17.sp))
                                                BasicText("${d.min}°", style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    SbMode.Photos -> {
                        var i by remember { mutableIntStateOf(0) }
                        LaunchedEffect(Unit) { while (true) { delay(8_000); i = (i + 1) % photos.size } }
                        val img by produceState<ImageBitmap?>(null, i) { value = Images.loadLarge(ctx, photos[i], 1920) }
                        Crossfade(img, animationSpec = tween(1_500), label = "photo") { b ->
                            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                        BasicText(time, Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 36.dp), style = TextStyle(color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Light))
                    }
                    SbMode.Wallpaper -> {
                        wallpaper?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        Column(Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 36.dp), horizontalAlignment = Alignment.End) {
                            BasicText(time, style = TextStyle(color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light))
                            BasicText(date.replaceFirstChar { it.uppercase() }, style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.75f)))
                        }
                    }
                }
            }
        }
        // Música tocando no topo, como a ilha dinâmica.
        state.nowPlaying?.let { np ->
            androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides wallpaper) {
                Island(np, {}, Modifier.align(Alignment.TopCenter).padding(top = 22.dp))
            }
        }
        if (modes.size > 1) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DotIndicator(modes.size, modeIndex)
            }
        }
    }
}
