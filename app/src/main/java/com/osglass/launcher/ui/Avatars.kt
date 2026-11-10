package com.osglass.launcher.ui

import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_9
import android.view.KeyEvent.KEYCODE_DEL
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.hashPin
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ Avatares */

/** Ícones de avatar (traço/preenchidos, 24×24). A chave é salva no perfil. */
val AvatarIcons: List<Pair<String, Pair<String, Boolean>>> = listOf(
    "estrela" to ("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z" to true),
    "coracao" to ("M12 20.5s-7.5-4.6-9.4-9.4A5.3 5.3 0 0 1 12 6.4a5.3 5.3 0 0 1 9.4 4.7c-1.9 4.8-9.4 9.4-9.4 9.4z" to true),
    "foguete" to ("M12 2.5c3 2 5 5.8 5 9.8l-2 3.7H9l-2-3.7c0-4 2-7.8 5-9.8zM12 8a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM9 16l-3 4.5h4M15 16l3 4.5h-4" to false),
    "controle" to ("M6.5 8.5h11a4.5 4.5 0 0 1 4.5 4.5v.5a3 3 0 0 1-5.4 1.8L15 13.5H9l-1.6 1.8A3 3 0 0 1 2 13.5V13a4.5 4.5 0 0 1 4.5-4.5zM7.5 10.5v3M6 12h3M15.5 11.5h.01M17.5 13h.01" to false),
    "musica" to ("M9 18V5.5l11-2.5v13M9 18a3 3 0 1 1-3-3a3 3 0 0 1 3 3zM20 16a3 3 0 1 1-3-3a3 3 0 0 1 3 3z" to false),
    "bola" to ("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7.5l3.8 2.8-1.5 4.4H9.7l-1.5-4.4zM12 3v4.5M15.8 10.3l4.4-1.5M14.3 14.7l2.6 4M9.7 14.7l-2.6 4M8.2 10.3L3.8 8.8" to false),
    "raio" to ("M13 2L4 14h7l-1 8 9-12h-7z" to true),
    "coroa" to ("M3 8l4.5 4 4.5-7 4.5 7L21 8l-2 11H5z" to true),
    "gato" to ("M5 4l3.5 4h7L19 4v9a7 7 0 0 1-14 0zM9.5 13h.01M14.5 13h.01M10.5 16.5h3" to false),
    "flor" to ("M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5zM12 3.5a3 3 0 0 1 0 6a3 3 0 0 1 0-6zM12 14.5a3 3 0 0 1 0 6a3 3 0 0 1 0-6zM3.5 12a3 3 0 0 1 6 0a3 3 0 0 1-6 0zM14.5 12a3 3 0 0 1 6 0a3 3 0 0 1-6 0z" to false),
)

/** Avatar do perfil: círculo com o gradiente da cor e o ícone escolhido (ou a inicial do nome). */
@Composable
fun ProfileAvatar(name: String, icon: String, colorIndex: Int, size: Dp, modifier: Modifier = Modifier) {
    val glyph = AvatarIcons.firstOrNull { it.first == icon }?.second
    Box(
        modifier.size(size).background(Brush.linearGradient(ProfileColors[colorIndex.coerceIn(0, ProfileColors.lastIndex)]), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (glyph != null) GlyphIcon(glyph.first, Color.White, size * 0.5f, filled = glyph.second)
        else BasicText(
            name.trim().take(1).uppercase().ifEmpty { "•" },
            style = TitleStyle.copy(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold, shadow = null),
        )
    }
}

/* ------------------------------------------------------------------ PIN */

/**
 * Pedido de PIN. [create] = definir um novo (digita duas vezes); senão confere com [verifyHash].
 * [onSuccess] recebe o hash do PIN digitado.
 */
class PinRequest(
    val title: String,
    val subtitle: String? = null,
    val create: Boolean = false,
    val verifyHash: String? = null,
    val onCancel: () -> Unit = {},
    val onSuccess: (String) -> Unit,
)

/** Abre o teclado de PIN por cima de qualquer tela. */
val LocalPinGate = staticCompositionLocalOf<(PinRequest) -> Unit> { { it.onSuccess("") } }

@Composable
fun PinOverlay(req: PinRequest?, onDismiss: () -> Unit) {
    AnimatedVisibility(req != null, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
        val r = req ?: return@AnimatedVisibility
        var digits by remember(r) { mutableStateOf("") }
        var first by remember(r) { mutableStateOf<String?>(null) }
        var error by remember(r) { mutableStateOf<String?>(null) }
        val shake = remember(r) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val firstKey = remember(r) { FocusRequester() }
        LaunchedEffect(r) { withFrameNanos { }; runCatching { firstKey.requestFocus() } }
        BackHandler { r.onCancel(); onDismiss() }

        fun fail(msg: String) {
            error = msg; digits = ""
            scope.launch { for (x in listOf(14f, -12f, 8f, -5f, 0f)) shake.animateTo(x, tween(55)) }
        }
        fun type(d: Char) {
            if (digits.length >= 4) return
            digits += d
            if (digits.length < 4) return
            val h = hashPin(digits)
            when {
                r.create && first == null -> { first = h; digits = ""; error = null }
                r.create -> if (h == first) { r.onSuccess(h); onDismiss() } else { first = null; fail("Os PINs não conferem. Digite de novo.") }
                h == r.verifyHash -> { r.onSuccess(h); onDismiss() }
                else -> fail("PIN incorreto")
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent code in KEYCODE_0..KEYCODE_9 || code == KEYCODE_DEL
                    when (code) {
                        in KEYCODE_0..KEYCODE_9 -> { type('0' + (code - KEYCODE_0)); true }
                        KEYCODE_DEL -> { digits = digits.dropLast(1); true }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.focusProperties { onExit = { cancelFocusChange() } }.focusGroup()) {
                GlyphIcon("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V8a4 4 0 0 1 8 0v3", Color.White, 34.dp)
                Spacer(Modifier.height(12.dp))
                BasicText(
                    if (r.create && first != null) "Confirme o PIN" else r.title,
                    style = TitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    error ?: r.subtitle ?: "Use os números do controle ou o teclado abaixo",
                    style = LabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = if (error != null) Color(0xFFFF8A8A) else Color.White.copy(alpha = 0.7f)),
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(4) { i ->
                        Box(Modifier.size(16.dp).background(if (i < digits.length) Color.White else Color.Transparent, CircleShape).border(2.dp, Color.White, CircleShape))
                    }
                }
                Spacer(Modifier.height(24.dp))
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    keys.chunked(3).forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            line.forEach { k ->
                                if (k.isEmpty()) Spacer(Modifier.size(54.dp))
                                else PinKey(k, if (k == "1") Modifier.focusRequester(firstKey) else Modifier) {
                                    if (k == "⌫") digits = digits.dropLast(1) else type(k[0])
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                TextLink("Cancelar") { r.onCancel(); onDismiss() }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(0.6f, 500f), label = "pinKey")
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.12f), label = "pinKeyBg")
    Box(
        modifier
            .size(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 14.dp else 0.dp, CircleShape)
            .background(bg, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = LabelStyle.copy(color = if (focused) Color(0xFF111114) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, shadow = null))
    }
}
