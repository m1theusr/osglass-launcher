package com.osglass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.R
import com.osglass.launcher.data.AppEntry
import com.osglass.launcher.data.HomeOverride
import com.osglass.launcher.data.TvContent
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.Wallpapers
import com.osglass.launcher.glass.liquidGlass
import kotlinx.coroutines.delay

private val Dark = Color(0xFF111114)
private const val LAST = 7

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val Script = FontFamily(
    Font(R.font.dancing_script, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val Greetings = listOf("olá", "hello", "hola", "bonjour", "ciao", "hallo", "こんにちは", "안녕하세요")

/**
 * Boas-vindas no padrão do assistente de configuração da Apple (iOS/macOS/tvOS):
 * "olá" animado, uma tela por passo (ícone, título, descrição), botão largo embaixo
 * e um link discreto logo abaixo.
 */
@Composable
fun OnboardingScreen(state: GlassState, wallpaper: Backdrop?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val settings = state.settings
    var step by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    fun go(n: Int) { forward = n > step; step = n.coerceIn(0, LAST) }

    val favs = remember { mutableStateOf(settings.favorites) }
    val hidden = remember { mutableStateOf(settings.hidden) }
    var name by remember { mutableStateOf(settings.profileName) }
    var homeOn by remember { mutableStateOf(HomeOverride.isEnabled(ctx)) }
    LaunchedEffect(step) { while (step == 6) { homeOn = HomeOverride.isEnabled(ctx); delay(1_000) } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.hasContentPermission = granted
        settings.recsEnabled = granted
        state.contentKey++
        go(6)
    }

    fun finish() {
        settings.profileName = name.trim()
        settings.favorites = favs.value
        settings.hidden = hidden.value
        settings.favoritesInitialized = true
        settings.onboardingDone = true
        onDone()
    }

    BackHandler(enabled = step > 0) { go(step - 1) }

    Box(Modifier.fillMaxSize()) {
        wallpaper?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (forward) 1 else -1
                (fadeIn(tween(350)) + slideInHorizontally(tween(420)) { it / 8 * dir }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { -it / 10 * dir })
            },
            label = "step",
        ) { s ->
            val primary = remember { FocusRequester() }
            val content = remember { FocusRequester() }
            val focusContent = s in 2..4
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { if (focusContent) content.requestFocus() else primary.requestFocus() }
            }
            Column(Modifier.fillMaxSize().padding(top = 20.dp, bottom = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (s) {
                        0 -> Hello()
                        1 -> StepFrame(
                            icon = { AvatarPreview(name, settings.profileColor) },
                            title = "Quem vai usar esta TV?",
                            desc = "Cada perfil terá seus favoritos e papel de parede. Dá para mudar depois em Ajustes.",
                        ) {
                            NameField(name, { name = it.take(20) }, Modifier.focusRequester(content))
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ProfileColors.indices.forEach { i ->
                                    ColorDot(i, settings.profileColor == i, name) { settings.profileColor = i }
                                }
                            }
                        }
                        2 -> StepFrame(
                            icon = { IconBadge(Icons.STANDBY.let { WALL_ICON }, listOf(Color(0xFFFF9F0A), Color(0xFFFF375F))) },
                            title = "Escolha um papel de parede",
                            desc = "O vidro da interface muda de cor junto com ele.",
                        ) { WallpaperPicker(state, Modifier.focusRequester(content)) }
                        3 -> StepFrame(
                            icon = { IconBadge(STAR_ICON, listOf(Color(0xFFFFD60A), Color(0xFFFF9F0A)), filled = true) },
                            title = "Escolha seus favoritos",
                            desc = "Até 6 apps ficam no dock de vidro, na ordem em que você marcar.",
                        ) {
                            AppPicker(state.apps.orEmpty().filter { it.pkg !in hidden.value }, Modifier.focusRequester(content), badge = { app ->
                                favs.value.indexOf(app.pkg).takeIf { it >= 0 }?.let { (it + 1).toString() }
                            }, dimmed = { false }) { app ->
                                favs.value = when {
                                    app.pkg in favs.value -> favs.value - app.pkg
                                    favs.value.size >= DOCK_SIZE -> favs.value
                                    else -> favs.value + app.pkg
                                }
                            }
                        }
                        4 -> StepFrame(
                            icon = { IconBadge(EYE_OFF_ICON, listOf(Color(0xFF8E8E93), Color(0xFF48484A))) },
                            title = "Esconda o que você não usa",
                            desc = "Os atalhos repetidos da TCL já vêm marcados. Nada é desinstalado.",
                        ) {
                            AppPicker(state.apps.orEmpty(), Modifier.focusRequester(content), badge = { app ->
                                if (app.pkg in hidden.value) "Oculto" else null
                            }, dimmed = { it.pkg in hidden.value }) { app ->
                                hidden.value = if (app.pkg in hidden.value) hidden.value - app.pkg else hidden.value + app.pkg
                                if (app.pkg in hidden.value) favs.value = favs.value - app.pkg
                            }
                        }
                        5 -> StepFrame(
                            icon = { IconBadge(TV_ICON, listOf(Color(0xFF5E5CE6), Color(0xFFBF5AF2))) },
                            title = "Destaques dos seus apps",
                            desc = null,
                        ) {
                            GroupedList(
                                listOf(
                                    Triple(Icons.PLAY, Color(0xFFFF375F), "Continue de onde parou" to "Episódios e filmes que você começou, no topo da tela"),
                                    Triple(STAR_ICON, Color(0xFFFF9F0A), "Novidades em tela cheia" to "Os destaques que YouTube, Netflix e Prime publicam"),
                                    Triple(LOCK_ICON, Color(0xFF30D158), "Fica tudo na TV" to "O OSGlass só lê o que os apps já guardam aqui"),
                                ),
                            )
                            Spacer(Modifier.height(10.dp))
                            BasicText(
                                if (state.hasContentPermission) "Permissão concedida." else "O Android vai pedir permissão para ler o conteúdo da TV.",
                                style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.6f)),
                            )
                        }
                        6 -> StepFrame(
                            icon = { IconBadge(REMOTE_ICON, listOf(Color(0xFF636366), Color(0xFF2C2C2E))) },
                            title = "Abrir o OSGlass no botão Home",
                            desc = "Ative “OSGlass — substituir tela inicial” em Acessibilidade. Ele só observa a tela inicial do Google TV, nada mais.",
                        ) {
                            StatusList(homeOn, HomeOverride.projectivyEnabled(ctx))
                        }
                        else -> StepFrame(
                            icon = { IconBadge(CHECK_ICON, listOf(Color(0xFF30D158), Color(0xFF0A8F3A)), round = true) },
                            title = "Tudo pronto, ${name.trim().ifEmpty { "você" }}",
                            desc = null,
                        ) {
                            GroupedList(
                                listOf(
                                    Triple(OK_ICON, Color.White.copy(alpha = 0.18f), "Segure OK em um app" to "Mover, ocultar, informações ou desinstalar"),
                                    Triple(Icons.GEAR, Color.White.copy(alpha = 0.18f), "Toque no relógio" to "Central de controle: StandBy, rede, Bluetooth, timer"),
                                    Triple(HOME_ICON, Color.White.copy(alpha = 0.18f), "Botão Home" to "Volta para o topo da tela inicial de qualquer lugar"),
                                ),
                            )
                        }
                    }
                }
                // ---- Rodapé no padrão Apple: botão largo + link discreto
                val (label, action) = when (s) {
                    0 -> "Começar" to { go(1) }
                    5 -> if (state.hasContentPermission) "Continuar" to { go(6) }
                         else "Permitir" to { permissionLauncher.launch(TvContent.PERMISSION) }
                    6 -> if (homeOn) "Continuar" to { go(7) } else "Abrir Acessibilidade" to { HomeOverride.openAccessibilitySettings(ctx) }
                    LAST -> "Começar a usar" to { finish() }
                    else -> "Continuar" to { go(s + 1) }
                }
                PrimaryButton(label, action, Modifier.focusRequester(primary))
                val secondary: Pair<String, () -> Unit>? = when (s) {
                    3, 4 -> "Pular" to { go(s + 1) }
                    5 -> if (state.hasContentPermission) null else "Agora não" to { settings.recsEnabled = false; go(6) }
                    6 -> if (homeOn) null else "Configurar depois em Ajustes" to { go(7) }
                    else -> null
                }
                Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                    secondary?.let { (t, a) -> TextLink(t, a) }
                }
            }
        }

        if (step > 0) {
            BasicText(
                "‹  Voltar",
                Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 22.dp),
                style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)),
            )
        }
    }
}

/* ------------------------------------------------------------------ Passos */

@Composable
private fun Hello() {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2_400); i = (i + 1) % Greetings.size } }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = i,
            transitionSpec = {
                (fadeIn(tween(700)) + slideInVertically(tween(700)) { it / 6 } + scaleIn(tween(700), initialScale = 0.96f)) togetherWith
                    (fadeOut(tween(500)) + slideOutVertically(tween(500)) { -it / 8 })
            },
            label = "hello",
        ) { idx ->
            val g = Greetings[idx]
            val cjk = g.any { it.code > 0x2E80 }
            BasicText(
                g,
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFD6E4FF), Color(0xFFFFD9F0))),
                    fontSize = if (cjk) 76.sp else 118.sp,
                    fontFamily = if (cjk) FontFamily.Default else Script,
                    fontWeight = if (cjk) FontWeight.Light else FontWeight.SemiBold,
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        BasicText("Bem-vindo ao OSGlass", style = LabelStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.7f)))
    }
}

@Composable
private fun StepFrame(icon: @Composable () -> Unit, title: String, desc: String?, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 60.dp)) {
        icon()
        Spacer(Modifier.height(10.dp))
        BasicText(title, style = TitleStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
        desc?.let {
            Spacer(Modifier.height(6.dp))
            BasicText(it, Modifier.width(560.dp), style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center))
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun IconBadge(d: String, colors: List<Color>, filled: Boolean = false, round: Boolean = false) {
    Box(
        Modifier.size(54.dp).clip(if (round) CircleShape else RoundedCornerShape(14.dp)).background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(d, Color.White, 30.dp, filled) }
}

@Composable
private fun AvatarPreview(name: String, colorIndex: Int) {
    Box(
        Modifier.size(56.dp).background(Brush.linearGradient(ProfileColors[colorIndex.coerceIn(0, ProfileColors.lastIndex)]), CircleShape),
        contentAlignment = Alignment.Center,
    ) { BasicText(name.trim().take(1).uppercase().ifEmpty { "?" }, style = TitleStyle.copy(fontSize = 28.sp, shadow = null)) }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    // Linha comum até apertar OK: só então o teclado da TV abre (senão ele cobre a tela ao receber foco).
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        withFrameNanos { }
        if (editing) { runCatching { fieldFocus.requestFocus() }; keyboard?.show(); wasEditing = true }
        else if (wasEditing) runCatching { rowFocus.requestFocus() }
    }
    val active = focused || editing
    Row(
        modifier
            .width(400.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.12f))
            .focusRequester(rowFocus)
            .onFocusChanged { focused = it.isFocused }
            .then(if (!editing) Modifier.onSelect { editing = true } else Modifier)
            .focusable(enabled = !editing)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (active) Dark else Color.White
        BasicText("Nome", Modifier.width(64.dp), style = LabelStyle.copy(fontSize = 15.sp, color = fg.copy(alpha = 0.55f), shadow = null))
        if (editing) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = LabelStyle.copy(color = Dark, fontSize = 16.sp, shadow = null),
                cursorBrush = SolidColor(Dark),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false }),
                modifier = Modifier.weight(1f).focusRequester(fieldFocus).onFocusChanged { if (!it.isFocused && editing && wasEditing) editing = false },
            )
        } else {
            BasicText(value.ifEmpty { "Seu nome" }, Modifier.weight(1f), style = LabelStyle.copy(color = fg, fontSize = 16.sp, shadow = null))
            BasicText("Editar  ›", style = LabelStyle.copy(color = fg.copy(alpha = 0.5f), fontSize = 13.sp, shadow = null))
        }
    }
}

@Composable
private fun ColorDot(index: Int, selected: Boolean, name: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, spring(0.6f, 450f), label = "dot")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(if (selected || focused) 3.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.7f), CircleShape)
            .padding(4.dp)
            .background(Brush.linearGradient(ProfileColors[index]), CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) { BasicText(name.trim().take(1).uppercase(), style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold, shadow = null)) }
}

@Composable
private fun WallpaperPicker(state: GlassState, modifier: Modifier) {
    val ctx = LocalContext.current
    val list = remember { Wallpapers.list(ctx) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        items(list, key = { it }) { id ->
            val thumb by produceState<ImageBitmap?>(null, id) { value = Wallpapers.thumbnail(ctx, id) }
            var focused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "wp")
            val selected = state.settings.wallpaper == id
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .then(if (id == list.first()) modifier else Modifier)
                        .size(160.dp, 90.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(if (selected || focused) 3.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .onFocusChanged { focused = it.isFocused; if (it.isFocused) state.settings.wallpaper = id }
                        .onSelect { state.settings.wallpaper = id }
                        .focusable(),
                ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                BasicText(Wallpapers.displayName(id), style = LabelStyle.copy(fontSize = 13.sp, color = Color.White.copy(alpha = if (selected) 1f else 0.7f)))
            }
        }
    }
}

@Composable
private fun AppPicker(
    apps: List<AppEntry>,
    firstModifier: Modifier,
    badge: (AppEntry) -> String?,
    dimmed: (AppEntry) -> Boolean,
    onToggle: (AppEntry) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.width(800.dp).height(184.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(10.dp),
    ) {
        items(apps, key = { it.pkg }) { app ->
            PickCard(app, badge(app), dimmed(app), if (app == apps.first()) firstModifier else Modifier) { onToggle(app) }
        }
    }
}

@Composable
private fun PickCard(app: AppEntry, badge: String?, dimmed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, spring(0.62f, 420f), label = "pick")
    val alpha by animateFloatAsState(if (dimmed) 0.35f else 1f, label = "pickAlpha")
    val shape = RoundedCornerShape(12.dp)
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .graphicsLayer { this.alpha = alpha; shadowElevation = if (focused) 16.dp.toPx() else 2.dp.toPx(); this.shape = shape; clip = true }
                .liquidGlass(shape, rim = 0.7f)
                .border(if (badge != null && !dimmed) 2.5.dp else if (focused) 1.5.dp else 0.dp, Color.White, shape)
                .onFocusChanged { focused = it.isFocused }
                .onSelect(onClick)
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                app.banner != null -> Image(app.banner, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    app.icon?.let { Image(it, null, Modifier.size(26.dp)) }
                    BasicText(app.label, style = LabelStyle.copy(fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        badge?.let {
            BasicText(
                it,
                Modifier.align(Alignment.TopEnd).padding(top = 0.dp).graphicsLayer { translationX = 6.dp.toPx(); translationY = (-6).dp.toPx() }
                    .clip(CircleShape).background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp),
                style = LabelStyle.copy(color = Dark, fontSize = 11.sp, fontWeight = FontWeight.Bold, shadow = null),
            )
        }
    }
}

@Composable
private fun GroupedList(rows: List<Triple<String, Color, Pair<String, String>>>) {
    Column(Modifier.width(520.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))) {
        rows.forEachIndexed { i, (icon, color, texts) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
                    GlyphIcon(icon, Color.White, 18.dp, filled = icon == Icons.PLAY || icon == STAR_ICON)
                }
                Column {
                    BasicText(texts.first, style = LabelStyle.copy(fontSize = 15.sp, shadow = null))
                    BasicText(texts.second, style = LabelStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.6f), shadow = null))
                }
            }
        }
    }
}

@Composable
private fun StatusList(on: Boolean, projectivy: Boolean) {
    Column(Modifier.width(520.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Acessibilidade", Modifier.weight(1f), style = LabelStyle.copy(fontSize = 15.sp, shadow = null))
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Color(0xFF30D158) else Color.White.copy(alpha = 0.4f)))
            Spacer(Modifier.width(8.dp))
            BasicText(if (on) "Ativo" else "Desativado", style = LabelStyle.copy(fontSize = 14.sp, color = if (on) Color(0xFF30D158) else Color.White.copy(alpha = 0.6f), shadow = null))
        }
        if (on && projectivy) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            BasicText(
                "O Projectivy também está ativo. Desative o serviço dele na mesma tela.",
                Modifier.padding(16.dp),
                style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color(0xFFFFD38A), shadow = null),
            )
        }
    }
}

/* ------------------------------------------------------------------ Botões */

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier, width: Dp = 320.dp) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, spring(0.6f, 450f), label = "primary")
    val bg by animateColorAsState(if (focused) Color.White else Color.White.copy(alpha = 0.85f), label = "primaryBg")
    Box(
        modifier
            .width(width)
            .height(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = if (focused) 18.dp.toPx() else 0f; shape = RoundedCornerShape(14.dp) }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) { BasicText(label, style = LabelStyle.copy(color = Dark, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, shadow = null)) }
}

@Composable
private fun TextLink(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicText(
        label,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 7.dp),
        style = LabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (focused) 1f else 0.7f)),
    )
}

/* ------------------------------------------------------------------ Ícones dos passos */

private const val WALL_ICON = "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 8a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM21 16l-5-5-8 9"
private const val STAR_ICON = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"
private const val EYE_OFF_ICON = "M3 3l18 18M10.6 6.1A10 10 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1-3 3.6M6.6 6.6C4.3 8.1 3 12 3 12s4 6 9 6a9 9 0 0 0 4.4-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2"
private const val TV_ICON = "M3 5h18v12H3zM8 21h8M10 9.5l4 1.8-4 1.8z"
private const val LOCK_ICON = "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V8a4 4 0 0 1 8 0v3"
private const val REMOTE_ICON = "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM12 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4zM10 14h4M10 17h4"
private const val CHECK_ICON = "M5 12.5l4.5 4.5L19 7.5"
private const val OK_ICON = "M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16zM12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z"
private const val HOME_ICON = "M4 11l8-7 8 7v9H4z"
