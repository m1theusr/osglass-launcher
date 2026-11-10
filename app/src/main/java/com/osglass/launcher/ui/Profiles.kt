package com.osglass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.Profile
import com.osglass.launcher.glass.Backdrop

/** Fundo das telas de perfil: papel de parede pré-desfocado e escurecido (leve e com cara de vidro). */
@Composable
private fun ProfileBackdrop(wallpaper: Backdrop?) {
    wallpaper?.let { Image(it.frosted, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
}

/**
 * "Quem está usando?" — no estilo Netflix/Apple TV. Aparece depois que a TV reinicia (com mais de um
 * perfil) e pelos Ajustes. "Gerenciar perfis" põe um lápis em cada avatar para editar.
 */
@Composable
fun ProfilePickerScreen(
    state: GlassState,
    wallpaper: Backdrop?,
    onPicked: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val settings = state.settings
    val pinGate = LocalPinGate.current
    val profiles = remember { settings.profiles() }
    val requesters = remember { profiles.associate { it.id to FocusRequester() } }
    var manage by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf<String?>(null) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requesters[settings.currentProfile]?.requestFocus() ?: requesters.values.firstOrNull()?.requestFocus() }
    }
    // Entrar no perfil: a tela cresce e some, e a tela inicial aparece por trás.
    LaunchedEffect(entering) {
        val id = entering ?: return@LaunchedEffect
        exit.animateTo(1f, tween(380))
        settings.switchProfile(id)
        onPicked()
    }
    BackHandler { if (manage) manage = false else onPicked() }

    fun pick(p: Profile) {
        when {
            manage -> if (p.pin.isNotEmpty()) pinGate(PinRequest("PIN de ${p.name}", "Para editar este perfil", verifyHash = p.pin) { onEdit(p.id) }) else onEdit(p.id)
            p.pin.isNotEmpty() && p.id != settings.currentProfile ->
                pinGate(PinRequest("PIN de ${p.name}", null, verifyHash = p.pin) { entering = p.id })
            else -> entering = p.id
        }
    }

    Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - exit.value; scaleX = 1f + exit.value * 0.12f; scaleY = 1f + exit.value * 0.12f }) {
        ProfileBackdrop(wallpaper)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(
                if (manage) "Gerenciar perfis" else "Quem está usando?",
                style = TitleStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(38.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.Top) {
                profiles.forEach { p ->
                    ProfileTile(p, requesters.getValue(p.id), manage) { pick(p) }
                }
                if (!manage) AddTile(onAdd)
            }
            Spacer(Modifier.height(30.dp))
            GlassPill(if (manage) "Concluído" else "Gerenciar perfis", { manage = !manage })
        }
        BasicText(
            if (manage) "Escolha um perfil para editar nome, avatar, favoritos, visual e proteção."
            else "Dá para trocar de perfil depois na Central de controle (relógio no canto da tela).",
            Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
            style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun ProfileTile(p: Profile, requester: FocusRequester, manage: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.16f else 1f, spring(0.6f, 380f), label = "profileTile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(128.dp)) {
        Box(
            Modifier
                .size(108.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (focused) 30.dp else 8.dp, CircleShape)
                .border(if (focused) 4.dp else 0.dp, Color.White, CircleShape)
                .focusRequester(requester)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable(),
        ) {
            ProfileAvatar(p.name, p.icon, p.color, 108.dp)
            if (manage) {
                Box(Modifier.size(108.dp).background(Color.Black.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon("M4 20h4L19 9l-4-4L4 16zM14 6l4 4", Color.White, 34.dp)
                }
            } else if (p.pin.isNotEmpty() || p.kids) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).size(30.dp).background(Color(0xFF1C1C1E), CircleShape).border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (p.pin.isNotEmpty()) GlyphIcon("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V8a4 4 0 0 1 8 0v3", Color.White, 15.dp)
                    else BasicText("K", style = LabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, shadow = null))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        BasicText(
            p.name.ifEmpty { "Sem nome" },
            style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = if (focused) 1f else 0.7f), textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (p.kids) BasicText("Infantil", style = LabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.55f)))
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.16f else 1f, spring(0.6f, 380f), label = "addTile")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(128.dp)) {
        Box(
            Modifier
                .size(108.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Color.White.copy(alpha = if (focused) 0.95f else 0.14f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon("M12 5v14M5 12h14", if (focused) Color(0xFF111114) else Color.White, 40.dp) }
        Spacer(Modifier.height(18.dp))
        BasicText("Adicionar perfil", style = LabelStyle.copy(fontSize = 16.sp, color = Color.White.copy(alpha = if (focused) 1f else 0.7f)))
    }
}

/** Contador de inicializações do Android: muda a cada vez que a TV liga. */
fun bootCount(ctx: android.content.Context): Int =
    runCatching { android.provider.Settings.Global.getInt(ctx.contentResolver, android.provider.Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
