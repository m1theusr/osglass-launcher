package com.osglass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.GlassSettings
import com.osglass.launcher.data.Profile
import com.osglass.launcher.data.ProfileColorCount
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.GlassStyle
import com.osglass.launcher.glass.GlassTheme
import com.osglass.launcher.glass.LocalBackdrop
import com.osglass.launcher.glass.LocalGlassStyle
import com.osglass.launcher.glass.LocalGlassTheme
import com.osglass.launcher.glass.Wallpapers
import com.osglass.launcher.glass.liquidGlass

private enum class WStep { Name, Start, Wallpaper, Favorites, Hidden, Look, Highlights, Protection, Done }

/**
 * Assistente de perfil (criar ou editar), no padrão da boas-vindas: um passo por tela,
 * prévia ao vivo do papel de parede, do visual e do avatar. Tudo fica num rascunho até o fim.
 */
@Composable
fun ProfileWizard(state: GlassState, screenW: Int, screenH: Int, editId: String?, onDone: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    val pinGate = LocalPinGate.current
    val editing = editId != null
    val original = remember { editId?.let { settings.profileById(it) } }
    val installed = state.byPkg.keys
    var draft by remember {
        mutableStateOf(
            original ?: run {
                val n = settings.profiles().size + 1
                Profile(
                    "p" + System.currentTimeMillis(), "", n % ProfileColorCount,
                    GlassSettings.DEFAULT_FAVORITES.filter { it in installed }.take(DOCK_SIZE),
                    GlassSettings.DEFAULT_HIDDEN, Wallpapers.DEFAULT,
                )
            },
        )
    }
    val steps = remember { if (editing) WStep.entries.filter { it != WStep.Start } else WStep.entries.toList() }
    var index by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val step = steps[index]
    fun go(i: Int) { forward = i > index; index = i.coerceIn(0, steps.lastIndex) }
    BackHandler { if (index > 0) go(index - 1) else onCancel() }

    // Prévia ao vivo do papel de parede escolhido no rascunho.
    val backdrop by produceState<Backdrop?>(null, draft.wallpaper) { value = Wallpapers.load(ctx, draft.wallpaper, screenW, screenH) }

    fun finish() {
        val final = draft.copy(name = draft.name.trim().ifEmpty { "Perfil ${settings.profiles().size + if (editing) 0 else 1}" })
        if (editing) settings.updateProfile(final) else settings.createProfile(final)
        onDone()
    }

    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalGlassTheme provides GlassTheme(draft.themeDark),
        LocalGlassStyle provides GlassStyle.forLevel(draft.glassLevel),
    ) {
        Box(Modifier.fillMaxSize()) {
            backdrop?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 9 * dir }) togetherWith
                        (fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { -it / 11 * dir })
                },
                label = "wizard",
            ) { s ->
                val primary = remember { FocusRequester() }
                val content = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    runCatching { if (s in listOf(WStep.Done, WStep.Highlights, WStep.Protection, WStep.Look)) primary.requestFocus() else content.requestFocus() }
                }
                Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (s) {
                            WStep.Name -> WizardFrame(
                                { ProfileAvatar(draft.name, draft.icon, draft.color, 76.dp, Modifier.shadow(18.dp, CircleShape)) },
                                if (editing) "Editar perfil" else "Novo perfil",
                                "Escolha o nome e o avatar.",
                            ) {
                                NameField(draft.name, { draft = draft.copy(name = it.take(20)) }, Modifier.focusRequester(content))
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ProfileColors.indices.forEach { i -> ColorDot(i, draft.color == i, draft.name.ifEmpty { "+" }) { draft = draft.copy(color = i) } }
                                }
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IconChoice(null, draft.icon.isEmpty(), draft.name) { draft = draft.copy(icon = "") }
                                    AvatarIcons.forEach { (key, _) -> IconChoice(key, draft.icon == key, draft.name) { draft = draft.copy(icon = key) } }
                                }
                            }
                            WStep.Start -> WizardFrame(
                                { WizardIcon(Icons.STANDBY.let { COPY_ICON }) },
                                "Começar de onde?",
                                "Dá para mudar tudo nos próximos passos.",
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    val cur = settings.profileName.ifEmpty { "perfil atual" }
                                    ChoiceCard("Copiar de $cur", "favoritos, apps ocultos, pastas, papel de parede e visual", Modifier.focusRequester(content)) {
                                        draft = draft.copy(
                                            favorites = settings.favorites.take(DOCK_SIZE), hidden = settings.hidden, wallpaper = settings.wallpaper,
                                            themeDark = settings.themeDark, glassLevel = settings.glassLevel, recsEnabled = settings.recsEnabled,
                                            recsDisabled = settings.recsDisabledApps,
                                            folders = org.json.JSONArray().apply {
                                                settings.folders.forEach { f -> put(org.json.JSONObject().put("name", f.name).put("apps", org.json.JSONArray(f.apps))) }
                                            }.toString(),
                                        )
                                        go(index + 1)
                                    }
                                    ChoiceCard("Começar do zero", "favoritos sugeridos, papel de parede padrão e modo escuro", Modifier) { go(index + 1) }
                                }
                            }
                            WStep.Wallpaper -> WizardFrame({ WizardIcon(WALL_ICON2) }, "Papel de parede", "A tela muda ao vivo enquanto você escolhe.") {
                                DraftWallpaperPicker(draft.wallpaper, Modifier.focusRequester(content)) { draft = draft.copy(wallpaper = it) }
                            }
                            WStep.Favorites -> WizardFrame({ WizardIcon(STAR_ICON2, filled = true) }, "Favoritos do dock", "Até 6 apps, na ordem em que você marcar.") {
                                AppPicker(state.apps.orEmpty().filter { it.pkg !in draft.hidden }, Modifier.focusRequester(content), badge = { app ->
                                    draft.favorites.indexOf(app.pkg).takeIf { it >= 0 }?.let { (it + 1).toString() }
                                }, dimmed = { false }) { app ->
                                    draft = draft.copy(
                                        favorites = when {
                                            app.pkg in draft.favorites -> draft.favorites - app.pkg
                                            draft.favorites.size >= DOCK_SIZE -> draft.favorites
                                            else -> draft.favorites + app.pkg
                                        },
                                    )
                                }
                            }
                            WStep.Hidden -> WizardFrame(
                                { WizardIcon(EYE_OFF_ICON2) },
                                "Apps ocultos",
                                if (draft.kids) "No perfil infantil, só os apps visíveis podem ser abertos." else "Esconda o que este perfil não usa.",
                            ) {
                                AppPicker(state.apps.orEmpty(), Modifier.focusRequester(content), badge = { app ->
                                    if (app.pkg in draft.hidden) "Oculto" else null
                                }, dimmed = { it.pkg in draft.hidden }) { app ->
                                    val hide = app.pkg !in draft.hidden
                                    draft = draft.copy(
                                        hidden = if (hide) draft.hidden + app.pkg else draft.hidden - app.pkg,
                                        favorites = if (hide) draft.favorites - app.pkg else draft.favorites,
                                    )
                                }
                            }
                            WStep.Look -> WizardFrame({ WizardIcon(Icons.MOON, filled = true) }, "Visual", "Modo e estilo do vidro deste perfil — a prévia é a própria tela.") {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OptionPill("Escuro", draft.themeDark, Modifier.focusRequester(primary)) { draft = draft.copy(themeDark = true) }
                                    OptionPill("Claro", !draft.themeDark, Modifier) { draft = draft.copy(themeDark = false) }
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    GlassStyle.LevelNames.forEachIndexed { i, n -> OptionPill(n, draft.glassLevel == i, Modifier) { draft = draft.copy(glassLevel = i) } }
                                }
                                Spacer(Modifier.height(20.dp))
                                // Amostra do vidro com as escolhas atuais.
                                Row(
                                    Modifier.liquidGlass(RoundedCornerShape(22.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    repeat(4) { Box(Modifier.size(70.dp, 40.dp).clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.85f))) }
                                }
                            }
                            WStep.Highlights -> WizardFrame({ WizardIcon(TV_ICON2) }, "Destaques", "O que os apps publicam: o destaque em tela cheia e o Continuar assistindo.") {
                                if (draft.kids) {
                                    BasicText("No perfil infantil os destaques ficam desligados.", style = LabelStyle.copy(fontSize = 15.sp))
                                    Spacer(Modifier.height(8.dp))
                                }
                                ToggleLine("Mostrar destaques e recomendações", draft.recsEnabled && !draft.kids, Modifier.focusRequester(primary), enabled = !draft.kids) {
                                    draft = draft.copy(recsEnabled = !draft.recsEnabled)
                                }
                                val pkgs = (state.channels.keys + state.watchNext.map { it.pkg }).distinct()
                                if (draft.recsEnabled && !draft.kids && pkgs.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pkgs.take(6).forEach { pkg ->
                                            val on = pkg !in draft.recsDisabled
                                            OptionPill(state.byPkg[pkg]?.label ?: pkg, on, Modifier) {
                                                draft = draft.copy(recsDisabled = if (on) draft.recsDisabled + pkg else draft.recsDisabled - pkg)
                                            }
                                        }
                                    }
                                }
                            }
                            WStep.Protection -> WizardFrame({ WizardIcon(LOCK_ICON2) }, "Proteção", null) {
                                ToggleLine("Perfil infantil", draft.kids, Modifier.focusRequester(primary), sub = "só apps visíveis, sem recomendações, sem Ajustes e sem desinstalar") {
                                    draft = draft.copy(kids = !draft.kids)
                                }
                                Spacer(Modifier.height(10.dp))
                                ToggleLine("Pedir PIN para entrar neste perfil", draft.pin.isNotEmpty(), Modifier) {
                                    if (draft.pin.isNotEmpty()) draft = draft.copy(pin = "")
                                    else pinGate(PinRequest("Crie um PIN de 4 dígitos", "Ele vai ser pedido ao entrar em ${draft.name.ifEmpty { "este perfil" }}", create = true) { h -> draft = draft.copy(pin = h) })
                                }
                                if (draft.kids && settings.parentPin.isEmpty()) {
                                    Spacer(Modifier.height(12.dp))
                                    BasicText(
                                        "Ao continuar, você vai criar o PIN dos pais. Ele protege os Ajustes e a saída do perfil infantil.",
                                        Modifier.width(520.dp),
                                        style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color(0xFFFFD38A), textAlign = TextAlign.Center),
                                    )
                                }
                            }
                            WStep.Done -> WizardFrame(
                                { ProfileAvatar(draft.name, draft.icon, draft.color, 86.dp, Modifier.shadow(20.dp, CircleShape)) },
                                if (editing) "Pronto para salvar" else "Tudo pronto, ${draft.name.trim().ifEmpty { "novo perfil" }}",
                                null,
                            ) {
                                Summary(
                                    listOf(
                                        "Dock" to "${draft.favorites.size} apps",
                                        "Papel de parede" to Wallpapers.displayName(draft.wallpaper),
                                        "Visual" to "${if (draft.themeDark) "Escuro" else "Claro"} · ${GlassStyle.LevelNames[draft.glassLevel.coerceIn(0, 2)]}",
                                        "Destaques" to if (draft.kids || !draft.recsEnabled) "Desligados" else "Ligados",
                                        "Proteção" to listOfNotNull("Infantil".takeIf { draft.kids }, "PIN".takeIf { draft.pin.isNotEmpty() }).joinToString(" · ").ifEmpty { "Nenhuma" },
                                    ),
                                )
                            }
                        }
                    }
                    // ---- Rodapé
                    val label = when (s) {
                        WStep.Done -> if (editing) "Salvar" else "Começar a usar"
                        WStep.Start -> null
                        else -> "Continuar"
                    }
                    if (label != null) {
                        PrimaryButton(label, {
                            when {
                                s == WStep.Done -> finish()
                                s == WStep.Protection && draft.kids && settings.parentPin.isEmpty() ->
                                    pinGate(PinRequest("Crie o PIN dos pais", "Protege os Ajustes e a saída do perfil infantil", create = true) { h ->
                                        settings.parentPin = h; go(index + 1)
                                    })
                                else -> go(index + 1)
                            }
                        }, if (s in listOf(WStep.Done)) Modifier.focusRequester(primary) else Modifier)
                    } else Spacer(Modifier.height(48.dp))
                    Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                        when {
                            s == WStep.Done && editing && settings.profiles().size > 1 ->
                                TextLink(if (confirmDelete) "Confirmar: apagar ${draft.name}" else "Apagar perfil") {
                                    if (confirmDelete) { settings.deleteProfile(draft.id); onDone() } else confirmDelete = true
                                }
                            s in listOf(WStep.Wallpaper, WStep.Favorites, WStep.Hidden, WStep.Look, WStep.Highlights) -> TextLink("Pular") { go(index + 1) }
                            index == 0 -> TextLink("Cancelar", onCancel)
                        }
                    }
                }
            }
            BasicText(
                "Passo ${index + 1} de ${steps.size}",
                Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 22.dp),
                style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f)),
            )
            if (index > 0) {
                BasicText("‹  Voltar", Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 22.dp), style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)))
            }
        }
    }
}

/* ------------------------------------------------------------------ Peças do assistente */

@Composable
private fun WizardFrame(icon: @Composable () -> Unit, title: String, desc: String?, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 60.dp)) {
        icon()
        Spacer(Modifier.height(10.dp))
        BasicText(title, style = TitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
        desc?.let {
            Spacer(Modifier.height(6.dp))
            BasicText(it, Modifier.width(560.dp), style = LabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.72f), textAlign = TextAlign.Center))
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun WizardIcon(d: String, filled: Boolean = false) {
    Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).liquidGlass(RoundedCornerShape(14.dp), tint = Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        GlyphIcon(d, Color.White, 28.dp, filled)
    }
}

@Composable
private fun IconChoice(key: String?, selected: Boolean, name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.18f else 1f, spring(0.6f, 450f), label = "iconChoice")
    val glyph = key?.let { k -> AvatarIcons.first { it.first == k }.second }
    Box(
        Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.12f), CircleShape)
            .border(if (selected) 2.5.dp else 0.dp, Color.White, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        val fg = if (focused) Color(0xFF111114) else Color.White
        if (glyph != null) GlyphIcon(glyph.first, fg, 19.dp, glyph.second)
        else BasicText(name.trim().take(1).uppercase().ifEmpty { "A" }, style = LabelStyle.copy(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, shadow = null))
    }
}

@Composable
private fun ChoiceCard(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, spring(0.6f, 420f), label = "choice")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .width(280.dp)
            .height(130.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 22.dp else 0.dp, shape)
            .liquidGlass(shape, tint = Color.White.copy(alpha = 0.12f))
            .background(if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        val fg = if (focused) Color(0xFF111114) else Color.White
        BasicText(title, style = LabelStyle.copy(color = fg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        BasicText(sub, style = LabelStyle.copy(color = fg.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.Normal, shadow = null))
    }
}

@Composable
private fun OptionPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "opt")
    val bg by animateColorAsState(
        when { focused -> Color.White; selected -> Color.White.copy(alpha = 0.32f); else -> Color.White.copy(alpha = 0.1f) }, label = "optBg",
    )
    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(if (selected && !focused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val fg = if (focused) Color(0xFF111114) else Color.White
        if (selected) GlyphIcon(CHECK_ICON2, fg, 14.dp)
        BasicText(label, style = LabelStyle.copy(color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null), maxLines = 1)
    }
}

@Composable
private fun ToggleLine(label: String, value: Boolean, modifier: Modifier, sub: String? = null, enabled: Boolean = true, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.1f), label = "toggleLine")
    val fg = if (focused) Color(0xFF111114) else Color.White
    Row(
        modifier
            .width(520.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .onSelect { if (enabled) onToggle() }
            .focusable()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = LabelStyle.copy(color = fg.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp, shadow = null))
            sub?.let { BasicText(it, style = LabelStyle.copy(color = fg.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Normal, shadow = null)) }
        }
        if (value) Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF30D158)))
        Spacer(Modifier.width(8.dp))
        BasicText(if (value) "Ligado" else "Desligado", style = LabelStyle.copy(color = fg.copy(alpha = 0.6f), fontSize = 14.sp, shadow = null))
    }
}

@Composable
private fun Summary(rows: List<Pair<String, String>>) {
    Column(Modifier.width(480.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))) {
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp)) {
                BasicText(k, Modifier.weight(1f), style = LabelStyle.copy(fontSize = 14.sp, shadow = null))
                BasicText(v, style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.65f), shadow = null))
            }
        }
    }
}

@Composable
private fun DraftWallpaperPicker(selected: String, modifier: Modifier, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val list = remember { Wallpapers.list(ctx) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        items(list, key = { it }) { id ->
            val thumb by produceState<ImageBitmap?>(null, id) { value = Wallpapers.thumbnail(ctx, id) }
            var focused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "dwp")
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .then(if (id == list.first()) modifier else Modifier)
                        .size(160.dp, 90.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(if (selected == id || focused) 3.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) onPick(id) }
                        .onSelect { onPick(id) }
                        .focusable(),
                ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                BasicText(Wallpapers.displayName(id), style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = if (selected == id) 1f else 0.7f)))
            }
        }
    }
}

private const val COPY_ICON = "M8 8h11v12H8zM5 16V4h11"
private const val WALL_ICON2 = "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 8a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM21 16l-5-5-8 9"
private const val STAR_ICON2 = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"
private const val EYE_OFF_ICON2 = "M3 3l18 18M10.6 6.1A10 10 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1-3 3.6M6.6 6.6C4.3 8.1 3 12 3 12s4 6 9 6a9 9 0 0 0 4.4-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2"
private const val TV_ICON2 = "M3 5h18v12H3zM8 21h8M10 9.5l4 1.8-4 1.8z"
private const val LOCK_ICON2 = "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V8a4 4 0 0 1 8 0v3"
private const val CHECK_ICON2 = "M5 12.5l4.5 4.5L19 7.5"
