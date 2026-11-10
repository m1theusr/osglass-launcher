package com.osglass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.GlassSettings
import com.osglass.launcher.data.Profile
import com.osglass.launcher.data.ProfileColorCount
import com.osglass.launcher.glass.Backdrop

/** Fundo das telas de perfil: papel de parede pré-desfocado e escurecido (leve e com cara de vidro). */
@Composable
private fun ProfileBackdrop(wallpaper: Backdrop?) {
    wallpaper?.let { Image(it.frosted, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}

/**
 * "Quem está usando?" — aparece depois que a TV reinicia (com mais de um perfil), no estilo Netflix/Apple TV.
 */
@Composable
fun ProfilePickerScreen(state: GlassState, wallpaper: Backdrop?, onPicked: () -> Unit, onAdd: () -> Unit) {
    val settings = state.settings
    val profiles = remember { settings.profiles() }
    val requesters = remember { profiles.associate { it.id to FocusRequester() } }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters[settings.currentProfile]?.requestFocus() ?: requesters.values.firstOrNull()?.requestFocus() }
    }
    BackHandler { onPicked() }

    Box(Modifier.fillMaxSize()) {
        ProfileBackdrop(wallpaper)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("Quem está usando?", style = TitleStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.Top) {
                profiles.forEach { p ->
                    ProfileTile(
                        letter = p.name.take(1).uppercase().ifEmpty { "•" },
                        brush = Brush.linearGradient(ProfileColors[p.color.coerceIn(0, ProfileColors.lastIndex)]),
                        name = p.name.ifEmpty { "Sem nome" },
                        requester = requesters.getValue(p.id),
                    ) {
                        settings.switchProfile(p.id)
                        onPicked()
                    }
                }
                ProfileTile("+", SolidColor(Color.White.copy(alpha = 0.16f)), "Adicionar perfil", remember { FocusRequester() }, plus = true, onClick = onAdd)
            }
        }
        BasicText(
            "Dá para trocar de perfil depois na Central de controle (relógio no canto da tela).",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp),
            style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun ProfileTile(letter: String, brush: Brush, name: String, requester: FocusRequester, plus: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.16f else 1f, spring(0.6f, 380f), label = "profileTile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(130.dp)) {
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                // Sem sombra no "+": com fundo translúcido a sombra aparece por dentro, em forma de polígono.
                .then(if (plus) Modifier else Modifier.shadow(if (focused) 30.dp else 8.dp, CircleShape))
                .background(brush, CircleShape)
                .border(if (focused) 4.dp else if (plus) 1.5.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.4f), CircleShape)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(letter, style = TitleStyle.copy(fontSize = if (plus) 44.sp else 46.sp, fontWeight = if (plus) FontWeight.Light else FontWeight.Bold, shadow = null))
        }
        Spacer(Modifier.height(18.dp))
        BasicText(
            name,
            style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = if (focused) 1f else 0.7f), textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Página "Novo perfil": nome (OK abre o teclado), cor e criar. */
@Composable
fun NewProfileScreen(state: GlassState, wallpaper: Backdrop?, onDone: () -> Unit, onCancel: () -> Unit) {
    val settings = state.settings
    val n = remember { settings.profiles().size + 1 }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(n % ProfileColorCount) }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { nameFocus.requestFocus() } }
    BackHandler { onCancel() }

    fun create() {
        val installed = state.byPkg.keys
        settings.addProfile(GlassSettings.DEFAULT_FAVORITES.filter { it in installed })
        settings.profileName = name.trim().ifEmpty { "Perfil $n" }
        settings.profileColor = color
        onDone()
    }

    Box(Modifier.fillMaxSize()) {
        ProfileBackdrop(wallpaper)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(104.dp).shadow(20.dp, CircleShape).background(Brush.linearGradient(ProfileColors[color]), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(name.trim().take(1).uppercase().ifEmpty { "+" }, style = TitleStyle.copy(fontSize = 44.sp, fontWeight = FontWeight.Bold, shadow = null))
            }
            Spacer(Modifier.height(18.dp))
            BasicText("Novo perfil", style = TitleStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
            BasicText(
                "Cada perfil tem seus favoritos, apps ocultos e papel de parede.",
                style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.7f)),
            )
            Spacer(Modifier.height(24.dp))
            NameField(name, { name = it.take(20) }, Modifier.focusRequester(nameFocus))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileColors.indices.forEach { i -> ColorDot(i, color == i, name.ifEmpty { "+" }) { color = i } }
            }
            Spacer(Modifier.height(30.dp))
            PrimaryButton("Criar perfil", { create() }, Modifier)
            Spacer(Modifier.height(4.dp))
            TextLink("Cancelar", onCancel)
        }
    }
}

/** Contador de inicializações do Android: muda a cada vez que a TV liga. */
fun bootCount(ctx: android.content.Context): Int =
    runCatching { android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(-1)

@Suppress("unused")
private fun Profile.initial() = name.take(1).uppercase()
