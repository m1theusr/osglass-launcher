package com.osglass.launcher.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Tema claro/escuro do OSGlass (Central de controle › lua). */
@Immutable
data class GlassTheme(val dark: Boolean) {
    val content: Color = if (dark) Color.White else Color(0xFF1C1C1E)
    val focusBg: Color = if (dark) Color.White else Color(0xFF1C1C1E)
    val focusFg: Color = if (dark) Color(0xFF111114) else Color.White
    val surface: Color = if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.55f)
    val scrim: Color = if (dark) Color(0xFF08080E).copy(alpha = 0.6f) else Color(0xFFF2F2F7).copy(alpha = 0.55f)
}

val LocalGlassTheme = staticCompositionLocalOf { GlassTheme(true) }

/** Modo leve/só apps: sem desfoque atrás dos painéis, então o fundo deles escurece mais. */
val LocalLiteMode = staticCompositionLocalOf { false }

fun scrimAlpha(normal: Float, lite: Boolean): Float = if (lite) maxOf(normal, 0.72f) else normal

/**
 * Estilo do vidro (Ajustes › Aparência › Estilo do vidro), aplicado em todos os painéis:
 *  - [clarity] 1 = transparente (o fundo nítido aparece, como o Liquid Glass do tvOS 26/27);
 *    0 = fosco (só o fundo desfocado).
 *  - [boost] multiplica a película (tint) de cada painel.
 */
@Immutable
data class GlassStyle(val clarity: Float = 1f, val boost: Float = 0.7f) {
    companion object {
        fun forLevel(level: Int) = when (level) {
            0 -> GlassStyle(clarity = 1f, boost = 0.6f)    // Transparente
            1 -> GlassStyle(clarity = 0.5f, boost = 1f)    // Equilibrado
            else -> GlassStyle(clarity = 0f, boost = 1.5f) // Fosco
        }
        val LevelNames = listOf("Transparente", "Equilibrado", "Fosco")
    }
}

val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }

/**
 * Painel de vidro barato o suficiente para uma TV com Android 12 (sem RuntimeShader), imitando o Liquid Glass:
 *  1. miolo: o fundo nítido exatamente atrás do painel, suavizado por uma camada desfocada
 *     (mais forte quanto menor a [GlassStyle.clarity]) e levemente escurecido;
 *  2. refração: numa faixa junto à borda o fundo é redesenhado ampliado a partir do centro,
 *     então a imagem "entorta" perto da borda como numa lente grossa;
 *  3. luz: faixa clara grossa na borda de baixo, filete claro no topo e contorno fino de luz.
 *
 * A posição vem de onGloballyPositioned e só é lida na fase de desenho: rolar a tela só redesenha.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    lens: Float = 1.06f,
    rim: Float = 1f,
): Modifier = composed {
    val backdrop = LocalBackdrop.current
    val style = LocalGlassStyle.current
    val light = !LocalGlassTheme.current.dark
    val pos = remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            // Faixa de refração: borda externa menos a mesma forma encolhida.
            val band = (minOf(size.width, size.height) * 0.14f).coerceIn(3.dp.toPx(), 9.dp.toPx())
            val innerSize = androidx.compose.ui.geometry.Size(size.width - 2 * band, size.height - 2 * band)
            val ring = if (innerSize.width > 0 && innerSize.height > 0) {
                val inner = Path().apply { addOutline(shape.createOutline(innerSize, layoutDirection, this@drawWithCache)); translate(Offset(band, band)) }
                Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, path, inner)
            } else null
            val rimBrush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.55f * rim),
                0.4f to Color.White.copy(alpha = 0.18f * rim),
                0.75f to Color.White.copy(alpha = 0.22f * rim),
                1f to Color.White.copy(alpha = 0.6f * rim),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.3f, size.height),
            )
            val bottomLight = Brush.verticalGradient(
                0f to Color.Transparent,
                (1f - band * 1.6f / size.height).coerceIn(0f, 1f) to Color.Transparent,
                1f to Color.White.copy(alpha = 0.32f * rim),
            )
            val topLight = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.20f * rim),
                (band * 0.9f / size.height).coerceIn(0f, 1f) to Color.Transparent,
            )
            val outer = Stroke(width = 1.1.dp.toPx())
            onDrawBehind {
                clipPath(path) {
                    if (backdrop != null) {
                        val p = pos.value
                        val w = backdrop.screenWidth.toFloat()
                        val h = backdrop.screenHeight.toFloat()
                        val sw = backdrop.sharp.width.toFloat()
                        val sh = backdrop.sharp.height.toFloat()
                        val s = maxOf(w / sw, h / sh)
                        val ox = (w - sw * s) / 2f
                        val oy = (h - sh * s) / 2f
                        fun drawSharp(zoom: Float) = withTransform({
                            if (zoom != 1f) scale(zoom, zoom, pivot = center)
                            translate(ox - p.x, oy - p.y)
                            scale(s, s, pivot = Offset.Zero)
                        }) { drawImage(backdrop.sharp, filterQuality = FilterQuality.Low) }
                        fun drawFrosted(alpha: Float) {
                            val sx = w / backdrop.frosted.width
                            val sy = h / backdrop.frosted.height
                            withTransform({
                                scale(lens, lens, pivot = center)
                                translate(-p.x, -p.y)
                                scale(sx, sy, pivot = Offset.Zero)
                            }) { drawImage(backdrop.frosted, alpha = alpha, filterQuality = FilterQuality.Low) }
                        }
                        if (style.clarity > 0f) drawSharp(1f)
                        // Mesmo no modo transparente fica uma leve suavização, como no tvOS.
                        drawFrosted((0.25f + 0.75f * (1f - style.clarity)).coerceIn(0f, 1f))
                        if (ring != null && style.clarity > 0f) {
                            clipPath(ring) { drawSharp(1.1f); drawFrosted(0.25f) }
                        }
                    } else {
                        drawRect(Color(0xFF1C1C22))
                    }
                    if (light) {
                        drawRect(Color.White.copy(alpha = (0.5f * style.boost).coerceIn(0f, 0.85f)))
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.10f * style.clarity))
                        drawRect(tint.copy(alpha = (tint.alpha * style.boost).coerceIn(0f, 0.9f)))
                    }
                    drawRect(topLight)
                    drawRect(bottomLight)
                }
                drawOutline(outline, rimBrush, style = outer)
            }
        }
}
