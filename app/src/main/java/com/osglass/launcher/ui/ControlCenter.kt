package com.osglass.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.HomeOverrideService
import com.osglass.launcher.data.TvInputs
import com.osglass.launcher.glass.LocalGlassTheme
import com.osglass.launcher.glass.liquidGlass
import com.osglass.launcher.data.GlassSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Dark = Color(0xFF111114)
private val TIMER_STEPS = listOf(0, 30, 60, 90, 120)

/**
 * Central de controle no estilo tvOS: abre pelo relógio no canto superior direito.
 * O que o Android 12 não deixa um app fazer direto (ligar Wi-Fi/Bluetooth) abre a tela certa do sistema.
 */
@Composable
fun ControlCenter(
    visible: Boolean,
    state: GlassState,
    onClose: () -> Unit,
    onStandBy: () -> Unit,
    onSettings: () -> Unit,
    onRecents: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(spring(0.75f, 520f), initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
    ) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var hint by remember { mutableStateOf<String?>(null) }
        var inputsOpen by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }
        val net = remember(tick) { state.networkLabel() }
        val bt = remember(tick) { state.bluetoothOn() }
        val sleepLeft = remember(tick) { HomeOverrideService.sleepMinutesLeft() }
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
        val settings = state.settings
        val time by clockText(if (settings.clock24h) "HH:mm" else "h:mm a")
        val theme = LocalGlassTheme.current

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f))) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = SafeX, top = 24.dp)
                    .width(262.dp)
                    .focusProperties { onExit = { cancelFocusChange() } }
                    .focusGroup(),
                horizontalAlignment = Alignment.End,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(time, style = LabelStyle.copy(color = theme.content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null))
                    Box(Modifier.size(34.dp, 24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                        ToggleGlyph(Dark)
                    }
                    Box(
                        Modifier.size(34.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(ProfileColors[settings.profileColor.coerceIn(0, ProfileColors.lastIndex)]), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(settings.profileName.take(1).uppercase().ifEmpty { "•" }, style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, shadow = null))
                    }
                }
                Spacer(Modifier.height(12.dp))
                ProfilesRow(state)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(Icons.STANDBY, "StandBy", null, Modifier.width(126.dp).height(150.dp).focusRequester(first), big = true) {
                        onClose(); onStandBy()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CcTile(Icons.WIFI, if (net == "Cabo de rede") "Rede" else "Wi-Fi", net, Modifier.width(126.dp).height(70.dp)) {
                            state.start(Intent(Settings.ACTION_WIFI_SETTINGS))
                        }
                        CcTile(
                            Icons.BLUETOOTH, "Bluetooth",
                            when (bt) { true -> "Ligado"; false -> "Desligado"; null -> "—" },
                            Modifier.width(126.dp).height(70.dp),
                        ) { state.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(Icons.INPUTS, "Entradas", null, Modifier.width(126.dp).height(56.dp), active = inputsOpen) {
                        inputsOpen = !inputsOpen
                    }
                    CcTile(Icons.GEAR, "Ajustes", null, Modifier.width(126.dp).height(56.dp)) { onClose(); onSettings() }
                }
                if (inputsOpen) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.inputs.isEmpty()) {
                            GlassPill("TV", { TvInputs.open(ctx, "") })
                        }
                        state.inputs.take(4).forEach { input ->
                            GlassPill(input.label.replace("HDMI ", "HDMI "), { TvInputs.open(ctx, input.id); onClose() })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcRound(Icons.MIC, 46.dp) {
                        val ok = state.start(Intent(Intent.ACTION_ASSIST)) ||
                            state.start(Intent("android.intent.action.VOICE_ASSIST"))
                        if (ok) onClose() else hint = "Busca por voz indisponível"
                    }
                    CcRound(RECENTS_ICON, 46.dp) { onClose(); onRecents() }
                    CcRound(Icons.CHIP, 46.dp) {
                        scope.launch {
                            hint = "Liberando memória…"
                            val mb = state.freeMemory()
                            hint = "Liberou $mb MB · ${state.availableMemoryMb()} MB livres"
                        }
                    }
                    CcRound(Icons.MOON, 46.dp, filled = true, active = !settings.themeDark) {
                        settings.themeDark = !settings.themeDark
                        hint = if (settings.themeDark) "Modo escuro" else "Modo claro"
                    }
                    CcRound(Icons.POWER, 46.dp) {
                        if (!HomeOverrideService.showPowerMenu()) hint = "Ative o OSGlass em Acessibilidade para desligar por aqui"
                    }
                }
                val status = hint ?: sleepLeft?.let { "A TV desliga em $it min" }
                if (status != null) {
                    BasicText(
                        status,
                        Modifier.padding(top = 10.dp).width(262.dp),
                        style = LabelStyle.copy(fontSize = 12.sp, color = theme.content.copy(alpha = 0.8f), textAlign = TextAlign.Center, shadow = null),
                    )
                }
            }
        }
    }
}

@Composable
private fun CcTile(
    icon: String,
    title: String,
    subtitle: String?,
    modifier: Modifier,
    big: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(0.6f, 480f), label = "ccTile")
    val on = focused || active
    val bg by animateColorAsState(if (on) theme.focusBg else Color.Transparent, label = "ccTileBg")
    val fg = if (on) theme.focusFg else theme.content
    val shape = RoundedCornerShape(if (big) 26.dp else 22.dp)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 18.dp else 0.dp, shape)
            .liquidGlass(shape, tint = Color.Black.copy(alpha = 0.34f), rim = 0.8f)
            .background(bg, shape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (big) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                GlyphIcon(icon, fg, 40.dp)
                BasicText(title, style = LabelStyle.copy(color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null))
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GlyphIcon(icon, fg, 20.dp)
                Column {
                    BasicText(title, style = LabelStyle.copy(color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, shadow = null), maxLines = 1)
                    subtitle?.let {
                        BasicText(it, style = LabelStyle.copy(color = fg.copy(alpha = 0.7f), fontSize = 11.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun CcRound(icon: String, size: Dp = 52.dp, filled: Boolean = false, active: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, spring(0.6f, 480f), label = "ccRound")
    val on = focused || active
    val bg by animateColorAsState(if (on) theme.focusBg else Color.Transparent, label = "ccRoundBg")
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 16.dp else 0.dp, CircleShape)
            .liquidGlass(CircleShape, tint = Color.Black.copy(alpha = 0.34f), rim = 0.8f)
            .background(bg, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(icon, if (on) theme.focusFg else theme.content, 20.dp, filled) }
}

private const val RECENTS_ICON = "M4 8h11v11H4zM8 4h12v11"

/** Perfis no topo da Central de controle (como no tvOS): troca com um OK. */
@Composable
private fun ProfilesRow(state: GlassState) {
    val settings = state.settings
    val theme = LocalGlassTheme.current
    val profiles = settings.profiles()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        profiles.forEach { p ->
            ProfileDot(p.name.take(1).uppercase().ifEmpty { "•" }, ProfileColors[p.color.coerceIn(0, ProfileColors.lastIndex)].let { androidx.compose.ui.graphics.Brush.linearGradient(it) }, p.name, p.id == settings.currentProfile, theme.content) {
                settings.switchProfile(p.id)
            }
        }
        ProfileDot("+", androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.18f)), "Adicionar", false, theme.content) {
            val installed = state.byPkg.keys
            settings.addProfile(GlassSettings.DEFAULT_FAVORITES.filter { it in installed })
        }
    }
}

@Composable
private fun ProfileDot(letter: String, brush: androidx.compose.ui.graphics.Brush, name: String, selected: Boolean, labelColor: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, spring(0.6f, 450f), label = "pdot")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier
                .size(42.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(if (focused) 14.dp else 0.dp, CircleShape)
                .background(brush, CircleShape)
                .then(if (selected || focused) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) { BasicText(letter, style = LabelStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold, shadow = null)) }
        BasicText(name, Modifier.padding(top = 4.dp), style = LabelStyle.copy(color = labelColor.copy(alpha = if (selected) 1f else 0.7f), fontSize = 10.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
