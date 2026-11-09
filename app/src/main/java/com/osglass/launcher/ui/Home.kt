package com.osglass.launcher.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.AppEntry
import com.osglass.launcher.data.ContentItem
import com.osglass.launcher.data.TvContent
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.LocalBackdrop
import com.osglass.launcher.glass.Wallpapers
import com.osglass.launcher.glass.liquidGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

private val HeroHeight = 412.dp
private val DockHeight = CardH + 28.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: GlassState,
    wallpaper: Backdrop?,
    screenW: Int,
    screenH: Int,
    homeEvents: SharedFlow<Unit>,
    onStandBy: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val settings = state.settings
    val glassIcons = settings.iconPack == "Vidro"

    val favs = state.favorites()
    val folderItems = settings.folders.mapNotNull { f ->
        val apps = f.apps.mapNotNull { state.byPkg[it] }.filter { it.pkg !in settings.hidden }
        if (apps.isEmpty()) null else FolderItem(f, apps)
    }
    val inFolders = folderItems.flatMap { it.apps.map { a -> a.pkg } }.toSet()
    val others = state.others().filter { it.pkg !in inFolders }
    val gridItems: List<Any> = folderItems + others
    var openFolder by remember { mutableStateOf<String?>(null) }
    var lastFolder by remember { mutableStateOf<String?>(null) }
    var folderPickFor by remember { mutableStateOf<AppEntry?>(null) }
    var recentsOpen by remember { mutableStateOf(false) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun req(pkg: String) = requesters.getOrPut(pkg) { FocusRequester() }
    fun tryFocus(pkg: String?): Boolean =
        pkg != null && requesters[pkg] != null && runCatching { requesters[pkg]!!.requestFocus() }.isSuccess
    val clockFocus = remember { FocusRequester() }

    // ---- Top Shelf: conteúdo do app em foco
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    val shelfPkg = focusedPkg ?: favs.firstOrNull()?.pkg
    val shelfApp = shelfPkg?.let { state.byPkg[it] }
    val shelfItems = if (settings.topShelf && shelfPkg != null) state.contentFor(shelfPkg) else emptyList()
    var heroIndex by remember(shelfPkg) { mutableIntStateOf(0) }
    LaunchedEffect(shelfPkg, shelfItems.size, settings.rotateShelf) {
        if (shelfItems.size > 1 && settings.rotateShelf && !settings.liteMode) {
            while (true) { delay(8_000); heroIndex = (heroIndex + 1) % shelfItems.size }
        }
    }
    val heroItem = shelfItems.getOrNull(heroIndex.coerceAtMost(shelfItems.lastIndex))
    val heroBackdrop by produceState<Backdrop?>(null, heroItem?.image) {
        value = heroItem?.image?.let { Wallpapers.hero(ctx, it, screenW, screenH) }
    }
    val backdrop = if (heroItem != null) heroBackdrop ?: wallpaper else wallpaper

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.hasContentPermission = granted
        settings.recsEnabled = granted
        state.contentKey++
    }

    // ---- overlays e modos
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var lastMenu by remember { mutableStateOf<AppEntry?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var restoreFocus by remember { mutableStateOf<String?>(null) }
    var ccOpen by remember { mutableStateOf(false) }
    val overlayOpen = ccOpen || menuFor != null || openFolder != null || folderPickFor != null || recentsOpen
    val blur by animateDpAsState(if (overlayOpen && !settings.liteMode) 14.dp else 0.dp, tween(250), label = "blur")

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 } }
    val dim by animateFloatAsState(if (scrolled) 0.5f else 0f, tween(400), label = "dim")

    fun focusFirst() { tryFocus((favs.firstOrNull() ?: others.firstOrNull())?.pkg) }
    fun closeMenu(focusTo: String? = menuFor?.pkg) { menuFor = null; restoreFocus = focusTo ?: "" }

    LaunchedEffect(state.apps != null) { if (state.apps != null) { withFrameNanos { }; focusFirst() } }
    LaunchedEffect(menuFor) { menuFor?.let { lastMenu = it } }
    LaunchedEffect(openFolder) { openFolder?.let { lastFolder = it } }
    LaunchedEffect(restoreFocus) {
        val target = restoreFocus ?: return@LaunchedEffect
        withFrameNanos { }
        when {
            target == "#clock" -> runCatching { clockFocus.requestFocus() }
            !tryFocus(target) -> focusFirst()
        }
        restoreFocus = null
    }
    LaunchedEffect(settings.favorites) { moving?.let { withFrameNanos { }; tryFocus(it) } }
    LaunchedEffect(Unit) {
        homeEvents.collect {
            menuFor = null; ccOpen = false; moving = null; openFolder = null; folderPickFor = null; recentsOpen = false
            listState.animateScrollToItem(0)
            focusFirst()
        }
    }

    BackHandler {
        when {
            moving != null -> moving = null
            ccOpen -> { ccOpen = false; restoreFocus = "#clock" }
            folderPickFor != null -> { restoreFocus = folderPickFor?.pkg; folderPickFor = null }
            menuFor != null -> closeMenu()
            recentsOpen -> { recentsOpen = false; restoreFocus = "#clock" }
            openFolder != null -> { restoreFocus = "folder:" + openFolder; openFolder = null }
            else -> scope.launch { listState.animateScrollToItem(0); focusFirst() }
        }
    }

    val edgeTop = with(LocalDensity.current) { 34.dp.toPx() }
    val edgeBottom = with(LocalDensity.current) { 78.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().blur(blur)) {
            Crossfade(backdrop, animationSpec = tween(700), label = "backdrop") { b ->
                if (b != null) Image(b.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (heroItem != null) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.72f), 0.45f to Color.Black.copy(alpha = 0.3f), 0.75f to Color.Transparent)))
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (dim + settings.wallDim * 0.15f).coerceAtMost(0.8f))))

            CompositionLocalProvider(LocalBackdrop provides backdrop, LocalBringIntoViewSpec provides EdgeSpec(edgeTop, edgeBottom)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 110.dp)) {
                    item(key = "hero") {
                        HeroArea(
                            state = state,
                            app = shelfApp,
                            item = heroItem,
                            count = shelfItems.size,
                            index = heroIndex,
                            needsPermission = !state.hasContentPermission,
                            clockFocus = clockFocus,
                            onClock = { ccOpen = true },
                            onAvatar = onOpenSettings,
                            onOpenApp = { shelfApp?.let { state.launchApp(it.pkg) } },
                            onPlay = { heroItem?.let { TvContent.open(ctx, it) } },
                            onRequestPermission = { permissionLauncher.launch(TvContent.PERMISSION) },
                        )
                    }
                    item(key = "dock") {
                        Dock {
                            if (favs.isEmpty() && state.apps != null) {
                                item {
                                    BasicText(
                                        "Segure OK em um app para adicionar aos favoritos",
                                        Modifier.padding(vertical = 28.dp, horizontal = 8.dp),
                                        style = LabelStyle.copy(color = Color.White.copy(alpha = 0.7f)),
                                    )
                                }
                            }
                            items(favs, key = { it.pkg }) { app ->
                                AppCard(
                                    app = app,
                                    focusRequester = req(app.pkg),
                                    moving = moving == app.pkg,
                                    onClick = { state.launchApp(app.pkg, view, it) },
                                    onMenu = { menuFor = app },
                                    onMove = { d -> state.moveFavorite(app.pkg, d) },
                                    onMoveDone = { moving = null },
                                    onFocus = { if (it) focusedPkg = app.pkg },
                                    glassIcons = glassIcons,
                                    showLabel = false,
                                    effects = !settings.liteMode,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                    val cw = state.continueWatching()
                    if (cw.isNotEmpty()) {
                        item(key = "cw-title") { SectionTitle("Continuar assistindo") }
                        item(key = "cw") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = SafeX, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(Gap),
                            ) {
                                items(cw, key = { it.key }) { c -> ContentCard(c, 96.dp, { TvContent.open(ctx, c) }) }
                            }
                        }
                    }
                    if (gridItems.isNotEmpty()) {
                        item(key = "apps-title") { SectionTitle("Apps") }
                        items(gridItems.chunked(COLUMNS), key = { row -> "row-" + gridKey(row.first()) }) { row ->
                            Row(
                                Modifier.padding(start = SafeX, end = SafeX, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(Gap),
                            ) {
                                row.forEach { item ->
                                    when (item) {
                                        is FolderItem -> FolderCard(item.folder, item.apps, req("folder:" + item.folder.name), { openFolder = item.folder.name }, glassIcons)
                                        is AppEntry -> AppCard(
                                            app = item,
                                            focusRequester = req(item.pkg),
                                            moving = false,
                                            onClick = { state.launchApp(item.pkg, view, it) },
                                            onMenu = { menuFor = item },
                                            onMove = {},
                                            onMoveDone = {},
                                            onFocus = { if (it) focusedPkg = item.pkg },
                                            glassIcons = glassIcons,
                                            effects = !settings.liteMode,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            ControlCenter(
                visible = ccOpen,
                state = state,
                onClose = { ccOpen = false; restoreFocus = "#clock" },
                onStandBy = onStandBy,
                onSettings = onOpenSettings,
                onRecents = { recentsOpen = true },
            )

            // ---- Menu do app (segurar OK / botão Menu)
            val m = menuFor ?: lastMenu
            val menuItems = if (m == null) emptyList() else buildList {
                val isFav = m.pkg in settings.favorites
                if (isFav) add(SheetItem("Mover") { moving = m.pkg; closeMenu() })
                add(SheetItem(if (isFav) "Remover dos favoritos" else "Adicionar aos favoritos") { state.toggleFavorite(m.pkg); closeMenu() })
                val inFolder = settings.folders.any { m.pkg in it.apps }
                if (inFolder) add(SheetItem("Tirar da pasta") { settings.removeFromFolders(m.pkg); closeMenu(focusTo = "") })
                else add(SheetItem("Colocar numa pasta") { folderPickFor = m; menuFor = null })
                add(SheetItem("Ocultar") { state.hide(m.pkg); closeMenu(focusTo = "") })
                add(SheetItem("Informações do app") {
                    state.start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${m.pkg}")))
                    closeMenu()
                })
                if (!isSystemApp(ctx, m.pkg)) {
                    add(SheetItem("Desinstalar") { state.start(Intent(Intent.ACTION_DELETE, Uri.parse("package:${m.pkg}"))); closeMenu() })
                }
                add(SheetItem("Cancelar") { closeMenu() })
            }
            val folderName = openFolder ?: lastFolder
            val shownFolder = folderItems.firstOrNull { it.folder.name == folderName }
            FolderOverlay(
                folder = shownFolder?.folder,
                visible = openFolder != null && shownFolder != null,
                apps = shownFolder?.apps.orEmpty(),
                glassIcons = glassIcons,
                onLaunch = { app, b -> state.launchApp(app.pkg, view, b) },
                onMenu = { app -> menuFor = app },
            )
            GlassSheet(
                visible = menuFor != null,
                title = m?.label.orEmpty(),
                subtitle = if (m != null && m.pkg in settings.favorites) "Favorito" else null,
                icon = m?.banner ?: m?.icon,
                items = menuItems,
            )
            val pick = folderPickFor
            val existing = settings.folders.map { it.name }
            val pickItems = (existing + listOf("Streaming", "Jogos", "Música", "Ferramentas").filter { it !in existing }).map { name ->
                SheetItem(name, detail = if (name in existing) "Pasta" else "Nova pasta") {
                    pick?.let { settings.putInFolder(it.pkg, name) }
                    folderPickFor = null
                    restoreFocus = "folder:$name"
                }
            } + SheetItem("Cancelar") { restoreFocus = pick?.pkg; folderPickFor = null }
            GlassSheet(
                visible = pick != null,
                title = "Colocar numa pasta",
                subtitle = pick?.label,
                icon = pick?.banner ?: pick?.icon,
                items = if (pick != null) pickItems else emptyList(),
            )
            RecentsOverlay(visible = recentsOpen, state = state, onClose = { recentsOpen = false })

        }
    }
}

@Composable
private fun HeroArea(
    state: GlassState,
    app: AppEntry?,
    item: ContentItem?,
    count: Int,
    index: Int,
    needsPermission: Boolean,
    clockFocus: FocusRequester,
    onClock: () -> Unit,
    onAvatar: () -> Unit,
    onOpenApp: () -> Unit,
    onPlay: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    val settings = state.settings
    Box(Modifier.fillMaxWidth().height(HeroHeight)) {
        Row(
            Modifier.fillMaxWidth().padding(start = SafeX, end = SafeX, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (settings.weatherOn) state.weather?.let { WeatherChip(it); Spacer(Modifier.width(12.dp)) }
            if (settings.greeting) {
                BasicText(greetingFor(settings.profileName), style = LabelStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.weight(1f))
            if (settings.islandOn) state.nowPlaying?.let { np ->
                Island(np, { com.osglass.launcher.data.Media.togglePlay(np); state.nowPlaying = np.copy(playing = !np.playing) })
                Spacer(Modifier.width(14.dp))
            }
            ClockPill(onClock, Modifier.focusRequester(clockFocus), h24 = settings.clock24h)
            Spacer(Modifier.width(10.dp))
            AvatarButton(settings.profileName.take(1).uppercase().ifEmpty { "•" }, settings.profileColor, onAvatar)
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = SafeX, bottom = 26.dp).width(480.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (app != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (app.banner ?: app.icon)?.let {
                        Image(it, null, Modifier.size(32.dp, 20.dp).clip(RoundedCornerShape(5.dp)), contentScale = ContentScale.Crop)
                    }
                    BasicText(app.label, style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                }
            }
            when {
                item != null -> {
                    BasicText(item.title, style = TitleStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    item.subtitle?.let { BasicText(it, style = LabelStyle.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)), maxLines = 1) }
                    item.progress?.let { p ->
                        Box(Modifier.width(150.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                            Box(Modifier.fillMaxWidth(p).height(3.dp).background(Color.White))
                        }
                    }
                    item.description?.let {
                        BasicText(it, style = LabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.78f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPill(if (item.progress != null) "▶  Retomar" else "▶  Assistir", onPlay, primary = true)
                        if (app != null) GlassPill("Abrir ${app.label}", onOpenApp)
                    }
                    if (count > 1) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(count.coerceAtMost(10)) { i ->
                                Box(Modifier.size(if (i == index) 16.dp else 6.dp, 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == index) 1f else 0.4f)))
                            }
                        }
                    }
                }
                app != null -> {
                    BasicText(app.label, style = TitleStyle.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold))
                    BasicText(
                        when {
                            needsPermission -> "Ative os destaques para ver aqui o que há de novo nos seus apps."
                            !settings.recsEnabled -> "Destaques desligados em Ajustes."
                            else -> "Este app não publica destaques."
                        },
                        style = LabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.75f)),
                    )
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassPill("Abrir", onOpenApp, primary = true)
                        if (needsPermission) GlassPill("Ativar destaques", onRequestPermission)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    BasicText(text, Modifier.padding(start = SafeX, top = 22.dp, bottom = 12.dp), style = TitleStyle)
}

/** Faixa de vidro com os favoritos — o "dock" da tela inicial. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Dock(content: LazyListScope.() -> Unit) {
    val pad = 14.dp
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().padding(horizontal = SafeX - pad)) {
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .liquidGlass(shape, tint = Color.White.copy(alpha = 0.10f), lens = 1.04f),
        ) {
            // Rola de card em card, alinhado às bordas internas: nunca sobra um card cortado.
            val padPx = with(LocalDensity.current) { pad.toPx() }
            CompositionLocalProvider(LocalBringIntoViewSpec provides EdgeSpec(padPx, padPx, adaptiveEnd = false)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Gap),
                    contentPadding = PaddingValues(start = pad, end = pad, top = pad, bottom = pad),
                    modifier = Modifier.height(DockHeight),
                    content = content,
                )
            }
        }
    }
}

/** Rola só o necessário para o item focado ficar inteiro na tela, com uma margem. */
@OptIn(ExperimentalFoundationApi::class)
internal class EdgeSpec(private val startMargin: Float, private val endMargin: Float, private val adaptiveEnd: Boolean = true) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        // Margem maior embaixo: o nome do app fica abaixo do card e a TV corta as bordas (overscan).
        val start = startMargin
        // Só itens na parte de baixo (grade de apps) ganham a margem extra; o dock fica onde está.
        val end = containerSize - if (!adaptiveEnd) endMargin else if (offset > containerSize * 0.8f) endMargin else startMargin / 2
        return when {
            size > end - start -> offset - start
            offset < start -> offset - start
            offset + size > end -> offset + size - end
            else -> 0f
        }
    }
}

private fun isSystemApp(ctx: android.content.Context, pkg: String): Boolean = runCatching {
    ctx.packageManager.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
}.getOrDefault(true)

private fun appVersion(ctx: android.content.Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
}.getOrDefault("")

private class FolderItem(val folder: com.osglass.launcher.data.Folder, val apps: List<AppEntry>)

private fun gridKey(item: Any): String = when (item) {
    is FolderItem -> "folder:" + item.folder.name
    is AppEntry -> item.pkg
    else -> item.toString()
}
