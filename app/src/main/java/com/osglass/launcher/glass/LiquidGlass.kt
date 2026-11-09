package com.osglass.launcher.glass

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Painel de "Liquid Glass" barato:
 *  1. desenha o pedaço do papel de parede desfocado que está atrás do elemento,
 *     levemente ampliado a partir do centro (efeito de lente/refração);
 *  2. aplica uma película clara ([tint]) e um brilho especular no topo;
 *  3. contorna com uma borda de luz em gradiente (claro em cima/esquerda, sutil embaixo/direita).
 *
 * A posição vem de onGloballyPositioned e só é lida na fase de desenho, então rolar a tela
 * não causa recomposição — só redesenho.
 */
/** Tema claro/escuro do OSGlass (Central de controle › lua). */
@androidx.compose.runtime.Immutable
data class GlassTheme(val dark: Boolean) {
    val content: Color = if (dark) Color.White else Color(0xFF1C1C1E)
    val focusBg: Color = if (dark) Color.White else Color(0xFF1C1C1E)
    val focusFg: Color = if (dark) Color(0xFF111114) else Color.White
    val surface: Color = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    val scrim: Color = if (dark) Color(0xFF08080E).copy(alpha = 0.6f) else Color(0xFFF2F2F7).copy(alpha = 0.55f)
}

val LocalGlassTheme = androidx.compose.runtime.staticCompositionLocalOf { GlassTheme(true) }

/** Multiplicador da película do vidro (Ajustes › Aparência › Intensidade do vidro). */
val LocalGlassBoost = androidx.compose.runtime.staticCompositionLocalOf { 1f }

fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    lens: Float = 1.06f,
    rim: Float = 1f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val boost = LocalGlassBoost.current
    val light = !LocalGlassTheme.current.dark
    val pos = remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val rimBrush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f * rim),
                0.35f to Color.White.copy(alpha = 0.10f * rim),
                0.7f to Color.White.copy(alpha = 0.02f),
                1f to Color.White.copy(alpha = 0.28f * rim),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            )
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.16f),
                0.5f to Color.Transparent,
            )
            val stroke = Stroke(width = 1.3.dp.toPx())
            onDrawBehind {
                clipPath(path) {
                    if (backdrop != null) {
                        val p = pos.value
                        val sx = backdrop.screenWidth.toFloat() / backdrop.frosted.width
                        val sy = backdrop.screenHeight.toFloat() / backdrop.frosted.height
                        withTransform({
                            scale(lens, lens, pivot = center)
                            translate(-p.x, -p.y)
                            scale(sx, sy, pivot = Offset.Zero)
                        }) {
                            drawImage(backdrop.frosted, filterQuality = FilterQuality.Low)
                        }
                    } else {
                        drawRect(Color(0xFF1C1C22))
                    }
                    // Modo claro: vidro leitoso (branco), como o tvOS claro.
                    if (light) drawRect(Color.White.copy(alpha = (0.5f * boost).coerceIn(0f, 0.85f)))
                    else drawRect(tint.copy(alpha = (tint.alpha * boost).coerceIn(0f, 0.9f)))
                    drawRect(sheen)
                }
                drawOutline(outline, rimBrush, style = stroke)
            }
        }
}
