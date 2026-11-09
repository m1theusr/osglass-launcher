package com.osglass.launcher.ui

import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.osglass.launcher.data.AppEntry
import com.osglass.launcher.glass.LocalGlassTheme
import com.osglass.launcher.glass.liquidGlass
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val SafeX = 48.dp
val Gap = 14.dp
const val COLUMNS = 6
/** 6 cards + 5 espaços preenchem exatamente a largura útil (960 − 2×48 dp): dock e grade ficam simétricos. */
val CardW = ((960f - 2 * 48f - (COLUMNS - 1) * 14f) / COLUMNS).dp
val CardH = CardW * 9f / 16f
val CardShape = RoundedCornerShape(14.dp)

private val TextShadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 10f)
val TitleStyle = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, shadow = TextShadow)
val LabelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, shadow = TextShadow)

internal fun KeyEvent.isSelect(): Boolean = nativeKeyEvent.keyCode.let {
    it == KEYCODE_DPAD_CENTER || it == KEYCODE_ENTER || it == KEYCODE_NUMPAD_ENTER
}

/**
 * Controla clique x clique longo no botão OK do controle.
 * O clique só dispara se este elemento viu o KeyDown — assim o KeyUp que "vaza" depois
 * de abrir um menu não aciona sem querer o primeiro item do menu.
 */
internal class PressTracker {
    var down = false
    var long = false
}

/* ------------------------------------------------------------------ App card */

@Composable
fun AppCard(
    app: AppEntry,
    focusRequester: FocusRequester,
    moving: Boolean,
    onClick: (Rect) -> Unit,
    onMenu: () -> Unit,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
    glassIcons: Boolean = true,
    showLabel: Boolean = true,
    effects: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val press = remember { PressTracker() }

    val scale by animateFloatAsState(
        if (focused) 1.12f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "scale",
    )
    val lift by animateFloatAsState(if (focused && effects) 1f else 0f, spring(stiffness = 320f), label = "lift")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "label")

    // Brilho que "varre" o card quando ele ganha foco (reflexo de vidro do tvOS).
    val sheen = remember { Animatable(-0.5f) }
    LaunchedEffect(focused) {
        if (focused && effects) {
            sheen.snapTo(-0.5f)
            sheen.animateTo(1.5f, tween(950, easing = FastOutSlowInEasing))
        }
    }

    // Modo "mover": card treme de leve, como os ícones do iOS.
    val wiggle = if (moving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            -1.4f, 1.4f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "wiggleAngle",
        ).value
    } else 0f

    Column(
        modifier.width(CardW).zIndex(if (focused) 1f else 0f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(CardW, CardH)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = wiggle
                    shadowElevation = (2f + 22f * lift).dp.toPx()
                    shape = CardShape
                    clip = true
                }
                .then(if (app.banner == null) Modifier.liquidGlass(CardShape, rim = 0.8f) else Modifier)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (focused != it.isFocused) onFocus(it.isFocused)
                    focused = it.isFocused
                }
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    when {
                        e.isSelect() -> {
                            if (e.type == KeyEventType.KeyDown) {
                                if (e.nativeKeyEvent.repeatCount == 0) {
                                    press.down = true; press.long = false
                                } else if (press.down && !press.long && !moving) {
                                    press.long = true
                                    onMenu()
                                }
                            } else if (e.type == KeyEventType.KeyUp) {
                                if (press.down && !press.long) {
                                    if (moving) onMoveDone() else onClick(bounds)
                                }
                                press.down = false; press.long = false
                            }
                            true
                        }
                        code == KEYCODE_MENU -> {
                            if (e.type == KeyEventType.KeyUp && !moving) onMenu()
                            true
                        }
                        moving && (code == KEYCODE_DPAD_LEFT || code == KEYCODE_DPAD_RIGHT) -> {
                            if (e.type == KeyEventType.KeyDown) onMove(if (code == KEYCODE_DPAD_LEFT) -1 else 1)
                            true
                        }
                        moving && (code == KEYCODE_DPAD_UP || code == KEYCODE_DPAD_DOWN) -> true
                        else -> false
                    }
                }
                .focusable()
                .drawWithContent {
                    drawContent()
                    if (glassIcons) {
                        // Ícone de vidro (tvOS 27): brilho no topo + borda de luz sempre visível.
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.16f),
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.10f),
                            ),
                        )
                        drawRoundRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.25f)),
                                start = Offset.Zero, end = Offset(size.width, size.height),
                            ),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(1.dp.toPx()),
                        )
                    }
                    if (lift > 0.01f) {
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.22f * lift),
                                0.45f to Color.Transparent,
                            ),
                        )
                        val band = size.width * 0.4f
                        val x = -band + (size.width + 2 * band) * sheen.value
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.30f * lift), Color.Transparent),
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height),
                            ),
                        )
                        drawRoundRect(
                            Color.White.copy(alpha = 0.45f * lift),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                    if (moving) {
                        drawRoundRect(
                            Color.White,
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(3.dp.toPx()),
                        )
                    }
                },
        ) {
            if (app.banner != null) {
                Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    app.icon?.let { Image(it, null, Modifier.size(38.dp)) }
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        app.label,
                        Modifier.padding(horizontal = 8.dp),
                        style = LabelStyle.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showLabel) {
            BasicText(
                app.label,
                Modifier
                    .padding(top = 12.dp)
                    .graphicsLayer { alpha = labelAlpha },
                style = LabelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ Pill (botão de vidro) */

@Composable
fun GlassPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val press = remember { PressTracker() }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "pill")
    val bg by animateColorAsState(
        when {
            focused -> theme.focusBg
            primary -> Color.White.copy(alpha = 0.85f)
            else -> Color.Transparent
        },
        label = "pillBg",
    )
    val fg by animateColorAsState(if (focused) theme.focusFg else if (primary) Color(0xFF111114) else theme.content, label = "pillFg")
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 14.dp else 0.dp, shape)
            .liquidGlass(shape, rim = 0.9f)
            .background(bg, shape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (!e.isSelect()) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) press.down = true
                if (e.type == KeyEventType.KeyUp) {
                    if (press.down) onClick()
                    press.down = false
                }
                true
            }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        BasicText(label, style = LabelStyle.copy(color = fg, fontSize = 14.sp, shadow = null))
    }
}

/* ------------------------------------------------------------------ Folha de vidro (menus) */

class SheetItem(
    val label: String,
    val detail: String? = null,
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * Painel modal de vidro. A navegação ↑/↓ é feita manualmente e todas as setas são consumidas
 * aqui, então o foco nunca "escapa" pros cards que estão atrás.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    icon: ImageBitmap?,
    items: List<SheetItem>,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + scaleIn(spring(0.72f, 520f), initialScale = 0.9f),
        exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.96f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f)),
            contentAlignment = Alignment.Center,
        ) {
            val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
            var index by remember(items.size) { mutableIntStateOf(0) }
            LaunchedEffect(requesters) {
                withFrameNanos { }
                runCatching { requesters.firstOrNull()?.requestFocus() }
            }
            val shape = RoundedCornerShape(30.dp)
            Column(
                Modifier
                    .width(400.dp)
                    .shadow(40.dp, shape)
                    .liquidGlass(shape, tint = Color.White.copy(alpha = 0.13f), lens = 1.1f)
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent e.nativeKeyEvent.keyCode in ARROWS
                        }
                        when (e.nativeKeyEvent.keyCode) {
                            KEYCODE_DPAD_UP -> {
                                index = (index - 1).coerceAtLeast(0)
                                runCatching { requesters[index].requestFocus() }; true
                            }
                            KEYCODE_DPAD_DOWN -> {
                                index = (index + 1).coerceAtMost(requesters.lastIndex)
                                runCatching { requesters[index].requestFocus() }; true
                            }
                            KEYCODE_DPAD_LEFT -> { items.getOrNull(index)?.onLeft?.invoke(); true }
                            KEYCODE_DPAD_RIGHT -> { items.getOrNull(index)?.onRight?.invoke(); true }
                            else -> false
                        }
                    }
                    .padding(22.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Image(it, null, Modifier.size(64.dp, 36.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        val theme = LocalGlassTheme.current
                        BasicText(title, style = TitleStyle.copy(color = theme.content, fontSize = 20.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            BasicText(it, style = LabelStyle.copy(color = theme.content.copy(alpha = 0.65f), shadow = null))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                items.forEachIndexed { i, item ->
                    SheetButton(item, requesters[i]) { index = i }
                    if (i != items.lastIndex) Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private val ARROWS = setOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT)

@Composable
private fun SheetButton(item: SheetItem, requester: FocusRequester, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val press = remember { PressTracker() }
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(0.6f, 500f), label = "btn")
    val bg by animateColorAsState(if (focused) theme.focusBg else theme.surface, label = "btnBg")
    val fg by animateColorAsState(if (focused) theme.focusFg else theme.content, label = "btnFg")
    val style = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { e ->
                if (!e.isSelect()) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) press.down = true
                if (e.type == KeyEventType.KeyUp) {
                    if (press.down) item.onClick()
                    press.down = false
                }
                true
            }
            .focusable()
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.onLeft != null) BasicText("‹  ", style = style)
        BasicText(item.label, Modifier.weight(1f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.detail?.let { BasicText(it, style = style.copy(color = fg.copy(alpha = 0.6f))) }
        if (item.onRight != null) BasicText("  ›", style = style)
    }
}

/* ------------------------------------------------------------------ Relógio */

@Composable
fun Clock(modifier: Modifier = Modifier) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val locale = Locale.getDefault()
    val time = remember(now) { SimpleDateFormat("HH:mm", locale).format(now) }
    val date = remember(now) {
        SimpleDateFormat("EEEE, d 'de' MMMM", locale).format(now).replaceFirstChar { it.uppercase() }
    }
    Column(modifier, horizontalAlignment = Alignment.End) {
        BasicText(time, style = TitleStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold))
        BasicText(date, style = LabelStyle.copy(color = Color.White.copy(alpha = 0.75f)))
    }
}
