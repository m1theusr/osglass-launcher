package com.osglass.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.osglass.launcher.data.ContentItem
import com.osglass.launcher.data.Images
import com.osglass.launcher.glass.LocalGlassTheme
import com.osglass.launcher.glass.liquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ Ícones (traço, 24x24) */

object Icons {
    const val STANDBY = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2"
    const val WIFI = "M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.8 16a5 5 0 0 1 6.4 0M12 19.3v0.2"
    const val BLUETOOTH = "M7 7l10 10-5 4V3l5 4L7 17"
    const val INPUTS = "M10 7l5 5-5 5M15 12H3M14 4h5a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-5"
    const val GEAR = "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6zM12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"
    const val MIC = "M9 6a3 3 0 0 1 6 0v5a3 3 0 0 1-6 0zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21"
    const val CHIP = "M6 6h12v12H6zM9 2.5v3.5M15 2.5v3.5M9 18v3.5M15 18v3.5M2.5 9H6M2.5 15H6M18 9h3.5M18 15h3.5"
    const val MOON = "M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"
    const val POWER = "M12 3v8M6.4 6.6a8 8 0 1 0 11.2 0"
    const val PLAY = "M7 4l13 8-13 8z"
}

private val iconCache = HashMap<String, ImageVector>()

fun strokeIcon(d: String, filled: Boolean = false): ImageVector = iconCache.getOrPut(d + filled) {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = if (filled) null else SolidColor(Color.White),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

@Composable
fun GlyphIcon(d: String, tint: Color, size: Dp = 22.dp, filled: Boolean = false) {
    Image(
        rememberVectorPainter(strokeIcon(d, filled)), null,
        Modifier.size(size), colorFilter = ColorFilter.tint(tint),
    )
}

/* ------------------------------------------------------------------ Seleção pelo controle */

/** OK do controle dispara [onClick] no KeyUp, só se este elemento viu o KeyDown. */
fun Modifier.onSelect(onClick: () -> Unit): Modifier = composed {
    val press = remember { PressTracker() }
    onPreviewKeyEvent { e ->
        if (!e.isSelect()) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) press.down = true
        if (e.type == KeyEventType.KeyUp) {
            if (press.down) onClick()
            press.down = false
        }
        true
    }
}

/* ------------------------------------------------------------------ Relógio em pílula + avatar */

val ProfileColors = listOf(
    listOf(Color(0xFFFF9F0A), Color(0xFFFF375F)),
    listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)),
    listOf(Color(0xFF30D158), Color(0xFF0A8F3A)),
    listOf(Color(0xFFBF5AF2), Color(0xFF5E5CE6)),
    listOf(Color(0xFF8E8E93), Color(0xFF48484A)),
)

@Composable
fun ClockPill(onClick: () -> Unit, modifier: Modifier = Modifier, h24: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "clockPill")
    val bg by animateColorAsState(if (focused) theme.focusBg else Color.Transparent, label = "clockBg")
    val fg = if (focused) theme.focusFg else theme.content
    val time by clockText(if (h24) "HH:mm" else "h:mm a")
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 14.dp else 0.dp, shape)
            .liquidGlass(shape, rim = 0.9f)
            .background(bg, shape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(time, style = LabelStyle.copy(color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null))
        Box(
            Modifier.size(34.dp, 24.dp).clip(shape).background(fg.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { ToggleGlyph(fg) }
    }
}

/** Ícone de "dois interruptores" da Central de controle. */
@Composable
fun ToggleGlyph(color: Color) {
    Canvas(Modifier.size(20.dp, 14.dp)) {
        val h = size.height * 0.42f
        val r = CornerRadius(h / 2)
        drawRoundRect(color, Offset(0f, 0f), Size(size.width, h), r)
        drawRoundRect(color.copy(alpha = 0.4f), Offset(0f, size.height - h), Size(size.width, h), r)
        drawCircle(if (color == Color.White) Color(0xFF111114) else Color.White, h * 0.32f, Offset(size.width - h / 2, h / 2))
        drawCircle(color, h * 0.32f, Offset(h / 2, size.height - h / 2))
    }
}

@Composable
fun AvatarButton(letter: String, colorIndex: Int, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(0.6f, 450f), label = "avatar")
    val colors = ProfileColors[colorIndex.coerceIn(0, ProfileColors.lastIndex)]
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 6.dp, CircleShape)
            .background(Brush.linearGradient(colors), CircleShape)
            .border(if (focused) 2.5.dp else 0.dp, Color.White, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(letter, style = TitleStyle.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, shadow = null))
    }
}

@Composable
fun clockText(pattern: String): androidx.compose.runtime.State<String> = produceState(SimpleDateFormat(pattern, Locale.getDefault()).format(Date()), pattern) {
    val fmt = SimpleDateFormat(pattern, Locale.getDefault())
    while (true) {
        value = fmt.format(Date())
        kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
    }
}

/* ------------------------------------------------------------------ Imagens remotas e cards de conteúdo */

@Composable
fun RemoteImage(uri: String?, modifier: Modifier = Modifier, maxWidth: Dp = 320.dp, contentScale: ContentScale = ContentScale.Crop) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { maxWidth.roundToPx() }
    val img by produceState<ImageBitmap?>(null, uri) { value = uri?.let { Images.load(ctx, it, px) } }
    Crossfade(img, animationSpec = tween(250), modifier = modifier, label = "remoteImage") { bmp ->
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = contentScale)
        else Box(Modifier.fillMaxSize())
    }
}

@Composable
fun ContentCard(item: ContentItem, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.62f, 420f), label = "content")
    val width = height * item.aspect.coerceIn(0.66f, 1.8f)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.width(width).zIndex(if (focused) 1f else 0f)) {
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    shadowElevation = if (focused) 20.dp.toPx() else 3.dp.toPx()
                    this.shape = shape; clip = true
                }
                .liquidGlass(shape, rim = 0.6f)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (focused) drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = CornerRadius(14.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                },
        ) {
            RemoteImage(item.image, Modifier.fillMaxSize(), maxWidth = width)
            item.progress?.let { p ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(3.dp).background(Color.White))
            }
        }
        BasicText(
            item.title,
            Modifier.padding(top = 10.dp).graphicsLayer { alpha = if (focused) 1f else 0f },
            style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
