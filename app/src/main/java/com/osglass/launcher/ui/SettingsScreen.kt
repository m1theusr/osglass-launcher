package com.osglass.launcher.ui

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.osglass.launcher.data.AppLoader
import com.osglass.launcher.data.Backups
import com.osglass.launcher.data.HomeOverride
import com.osglass.launcher.data.TvContent
import com.osglass.launcher.data.TvSystem
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.LocalBackdrop
import com.osglass.launcher.glass.LocalGlassTheme
import com.osglass.launcher.data.Media
import com.osglass.launcher.data.Recents
import com.osglass.launcher.data.WeatherRepo
import com.osglass.launcher.data.GlassSettings
import com.osglass.launcher.HomeOverrideService
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import com.osglass.launcher.glass.Wallpapers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val Dark = Color(0xFF111114)

/* ------------------------------------------------------------------ Modelo das linhas */

private sealed interface SRow
private data class SHeader(val text: String) : SRow
private data class SCaption(val text: String, val warn: Boolean = false, val mono: Boolean = false) : SRow
private class SToggle(val label: String, val value: Boolean, val sub: String? = null, val icon: String? = null, val color: Color? = null, val onChange: (Boolean) -> Unit) : SRow
private class SChoice(val label: String, val value: String, val sub: String? = null, val icon: String? = null, val color: Color? = null, val onNext: () -> Unit) : SRow
private class SLink(val label: String, val value: String = "", val sub: String? = null, val icon: String? = null, val color: Color? = null, val onClick: () -> Unit) : SRow
private class SInfo(val label: String, val value: String, val icon: String? = null, val color: Color? = null) : SRow
private class SSlider(val label: String, val percent: Int, val muted: Boolean, val onDelta: (Boolean) -> Unit) : SRow
private class SMeter(val label: String, val fraction: Float, val value: String, val action: String? = null, val onAction: (() -> Unit)? = null) : SRow
private object SWallpapers : SRow
private class SEdit(val label: String, val value: String, val placeholder: String, val onDone: (String) -> Unit) : SRow
private class STiles(val items: List<Pair<String, () -> Unit>>) : SRow

private class Cat(val name: String, val icon: String, val color: Color)

private object SIcons {
    const val HOME = "M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z"
    const val LOOK = "M12 3a9 9 0 1 0 0 18c1.5 0 2-1 2-2s-1-1.5-1-2.5S14 15 15 15h2a4 4 0 0 0 4-4c0-4.4-4-8-9-8zM7.5 11.5h.01M10 7.5h.01M15 7.5h.01"
    const val STATUS = "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 11a3.5 3.5 0 0 0 1 7z"
    const val REMOTE = "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2zM12 6.5h.01M10 11h4M10 14h4"
    const val APPS = "M6 4h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM9 9h6v6H9z"
    const val STAR = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"
    const val BOLT = "M13 2L4 14h7l-1 8 9-12h-7z"
    const val TV = "M3 5h18v12H3zM8 21h8M12 17v4"
    const val INFO = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 11v6M12 7.5h.01"
    const val SOUND = "M4 9.5h3.5L12 6v12l-4.5-3.5H4zM15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"
    const val SEARCH = "M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13zM15.5 15.5l5 5"
    const val CLOUD = "M7 18h10a4 4 0 0 0 .5-8A6 6 0 0 0 6 11a3.5 3.5 0 0 0 1 7zM12 15.5v-5M9.6 12.6L12 10.2l2.4 2.4"
}

private val Groups = listOf(
    listOf(
        Cat("Wi-Fi e rede", Icons.WIFI, Color(0xFF0A84FF)),
        Cat("Bluetooth", Icons.BLUETOOTH, Color(0xFF0A84FF)),
        Cat("Som", SIcons.SOUND, Color(0xFFFF375F)),
    ),
    listOf(
        Cat("Tela inicial", SIcons.HOME, Color(0xFF5E5CE6)),
        Cat("Aparência", SIcons.LOOK, Color(0xFFFF9F0A)),
        Cat("Relógio e clima", SIcons.STATUS, Color(0xFF32ADE6)),
        Cat("Modo StandBy", Icons.STANDBY, Color(0xFF48484A)),
        Cat("Controle e acessibilidade", SIcons.REMOTE, Color(0xFF636366)),
    ),
    listOf(
        Cat("Apps", SIcons.APPS, Color(0xFFBF5AF2)),
        Cat("Recomendações", SIcons.STAR, Color(0xFFFF9F0A)),
        Cat("Desempenho", SIcons.BOLT, Color(0xFF30D158)),
        Cat("Ajustes da TV", SIcons.TV, Color(0xFF8E8E93)),
        Cat("Sobre", SIcons.INFO, Color(0xFF8E8E93)),
    ),
)

private const val SEARCH = "Buscar"
private const val PROFILE = "Perfil e backup"
private val ColorNames = listOf("Laranja", "Azul", "Verde", "Roxo", "Grafite")

/* ------------------------------------------------------------------ Tela */

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(state: GlassState, wallpaper: Backdrop?, onExit: () -> Unit, onStandBy: () -> Unit, onRedoOnboarding: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val settings = state.settings
    var cat by remember { mutableStateOf("Tela inicial") }
    var tick by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<File?>(null) }
    var contentFocused by remember { mutableStateOf(false) }
    val catFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun catReq(name: String) = catFocus.getOrPut(name) { FocusRequester() }

    LaunchedEffect(Unit) { while (true) { delay(2_000); tick++ } }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { catReq(cat).requestFocus() } }
    LaunchedEffect(notice) { if (notice != null) { delay(4_000); notice = null } }
    LaunchedEffect(cat) { pendingRestore = null }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.hasContentPermission = granted
        settings.recsEnabled = granted
        state.contentKey++
    }

    BackHandler {
        if (contentFocused) runCatching { catReq(cat).requestFocus() } else onExit()
    }

    fun openAndroid(action: String) {
        if (!state.start(Intent(action))) state.start(Intent(Settings.ACTION_SETTINGS))
    }
    fun openTclPanel() {
        AppLoader.launchIntent(ctx, "com.tcl.dashboard")?.let { state.start(it) } ?: openAndroid(Settings.ACTION_SOUND_SETTINGS)
    }

    @Suppress("UNUSED_EXPRESSION") tick // relê valores ao vivo (volume, memória, rede) a cada 2 s

    fun rowsFor(name: String): List<SRow> = when (name) {
        PROFILE -> buildList {
            add(SHeader("Perfis"))
            val profiles = settings.profiles()
            profiles.forEach { pr ->
                val current = pr.id == settings.currentProfile
                add(SLink(pr.name, if (current) "Em uso" else "Trocar", "${pr.favorites.size} favoritos", SIcons.LOOK, ProfileColors[pr.color.coerceIn(0, ProfileColors.lastIndex)][0]) {
                    settings.switchProfile(pr.id); tick++
                })
            }
            add(SLink("Adicionar perfil") {
                val installed = state.byPkg.keys
                settings.addProfile(GlassSettings.DEFAULT_FAVORITES.filter { it in installed }); notice = "Perfil criado"; tick++
            })
            add(SCaption("Cada perfil tem seus favoritos, apps ocultos e papel de parede. Troque também pela Central de controle."))
            add(SHeader("Este perfil"))
            add(SEdit("Nome", settings.profileName, "Seu nome") { settings.profileName = it.trim().ifEmpty { settings.profileName } })
            add(SChoice("Cor do perfil", ColorNames[settings.profileColor.coerceIn(0, ColorNames.lastIndex)]) {
                settings.profileColor = (settings.profileColor + 1) % ColorNames.size
            })
            if (profiles.size > 1) {
                val confirming = pendingRestore?.name == "#delete"
                add(SLink("Apagar este perfil", if (confirming) "OK de novo para apagar" else "") {
                    if (confirming) { settings.deleteProfile(settings.currentProfile); pendingRestore = null; notice = "Perfil apagado" }
                    else pendingRestore = File("#delete")
                })
            }
            add(SHeader("Backup"))
            add(SLink("Fazer backup agora", icon = SIcons.CLOUD, color = Color(0xFF0A84FF)) {
                Backups.create(ctx); notice = "Backup salvo"; tick++
            })
            val backups = Backups.list(ctx)
            if (backups.isNotEmpty()) {
                add(SHeader("Restaurar"))
                backups.forEach { f ->
                    val confirming = pendingRestore == f
                    add(SLink(Backups.label(f), if (confirming) "OK de novo para restaurar" else "${(f.length() + 1023) / 1024} KB", icon = SIcons.CLOUD, color = Color.White.copy(alpha = 0.18f)) {
                        if (confirming) Backups.restoreAndRestart(ctx, f) else pendingRestore = f
                    })
                }
            }
            add(SCaption("Cada backup guarda uma cópia nova. Os 20 mais recentes ficam em Android/data/com.osglass.launcher/files/backups."))
            add(SHeader("Boas-vindas"))
            add(SLink("Refazer boas-vindas") { onRedoOnboarding() })
        }
        "Wi-Fi e rede" -> listOf(
            SInfo("Conexão", state.networkLabel(), Icons.WIFI, Color(0xFF0A84FF)),
            SInfo("Endereço IP", TvSystem.ipAddress(ctx) ?: "—"),
            SHeader("Redes"),
            SLink("Escolher rede Wi-Fi", "abre no Android") { openAndroid(Settings.ACTION_WIFI_SETTINGS) },
            SLink("Rede e internet", "proxy, DNS e cabo") { openAndroid(Settings.ACTION_WIRELESS_SETTINGS) },
            SCaption("No Android 12 um app não consegue ligar ou desligar o Wi-Fi sozinho; a troca de rede abre direto a tela do Android."),
        )
        "Bluetooth" -> listOf(
            SInfo("Bluetooth", when (state.bluetoothOn()) { true -> "Ligado"; false -> "Desligado"; null -> "—" }, Icons.BLUETOOTH, Color(0xFF0A84FF)),
            SLink("Dispositivos e controles", "abre no Android") { openAndroid(Settings.ACTION_BLUETOOTH_SETTINGS) },
        )
        "Som" -> listOf(
            SSlider("Volume", TvSystem.volumePercent(ctx), TvSystem.isMuted(ctx)) { up -> TvSystem.adjustVolume(ctx, up); tick++ },
            SToggle("Sem som", TvSystem.isMuted(ctx), icon = SIcons.SOUND, color = Color(0xFFFF375F)) { TvSystem.setMuted(ctx, it); tick++ },
            SHeader("Saída"),
            SLink("Som da TV", "abre no Android") { openAndroid(Settings.ACTION_SOUND_SETTINGS) },
            SLink("Modo de som", "painel da TCL") { openTclPanel() },
        )
        "Tela inicial" -> listOf(
            SChoice("Pacote de ícones", settings.iconPack, "Vidro: card com brilho e borda de luz, como no tvOS 27", SIcons.HOME, Color(0xFF5E5CE6)) {
                settings.iconPack = if (settings.iconPack == "Vidro") "Original" else "Vidro"
            },
            SToggle("Top Shelf", settings.topShelf, "destaque do app em foco em tela cheia") { settings.topShelf = it },
            SToggle("Girar destaques sozinho", settings.rotateShelf, "troca a cada 8 segundos") { settings.rotateShelf = it },
            SToggle("Continuar assistindo", settings.continueWatching, "fileira abaixo do dock") { settings.continueWatching = it },
            SCaption("Para reordenar os favoritos: segure OK no app › Mover."),
            SHeader("Ilha dinâmica"),
            SToggle("Música tocando no topo", settings.islandOn, "OK na pílula pausa ou continua") { settings.islandOn = it },
            if (Media.hasAccess(ctx)) SInfo("Acesso à música tocando", "Concedido")
            else SLink("Permitir acesso à música tocando", "abre no Android") { openAndroid(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
            SCaption(Media.ADB_COMMAND, mono = true),
            SHeader("Apps recentes"),
            if (Recents.hasAccess(ctx)) SInfo("Acesso ao uso", "Concedido")
            else SLink("Permitir acesso ao uso", "abre no Android") { openAndroid(Settings.ACTION_USAGE_ACCESS_SETTINGS) },
            SCaption(Recents.ADB_COMMAND, mono = true),
            SCaption("Os apps recentes abrem pela Central de controle."),
        )
        "Aparência" -> listOf(
            SChoice("Modo", if (settings.themeDark) "Escuro" else "Claro", "também pela lua na Central de controle", Icons.MOON, Color(0xFF5E5CE6)) { settings.themeDark = !settings.themeDark },
            SHeader("Papel de parede"),
            SWallpapers,
            SChoice("Escurecer o fundo", listOf("0%", "15%", "30%", "45%")[settings.wallDim.coerceIn(0, 3)]) {
                settings.wallDim = (settings.wallDim + 1) % 4
            },
            SHeader("Vidro"),
            SChoice("Intensidade do vidro", listOf("Leve", "Médio", "Forte")[settings.glassLevel.coerceIn(0, 2)], icon = SIcons.LOOK, color = Color(0xFFFF9F0A)) {
                settings.glassLevel = (settings.glassLevel + 1) % 3
            },
            SCaption("Suas próprias imagens: coloque em Android/data/com.osglass.launcher/files/wallpapers."),
        )
        "Relógio e clima" -> buildList {
            add(SToggle("Formato 24 horas", settings.clock24h, icon = Icons.STANDBY, color = Color(0xFF48484A)) { settings.clock24h = it })
            add(SToggle("Saudação", settings.greeting, "“Bom dia”, “Boa tarde”, “Boa noite” ao lado do clima") { settings.greeting = it })
            add(SHeader("Clima"))
            add(SToggle("Mostrar clima", settings.weatherOn, "canto superior esquerdo e StandBy", SIcons.STATUS, Color(0xFF32ADE6)) { settings.weatherOn = it })
            add(SEdit("Cidade", settings.weatherCity, "Automática (pela internet)") { settings.weatherCity = it.trim() })
            add(SChoice("Unidade", if (settings.weatherUnit == "F") "°F" else "°C") { settings.weatherUnit = if (settings.weatherUnit == "F") "C" else "F" })
            state.weather?.let { w -> add(SInfo("Agora em ${w.city}", "${w.temp}° · ${WeatherRepo.label(w.code)}")) }
            add(SCaption("Previsão da Open-Meteo. Na cidade automática, a localização aproximada vem do seu IP (ipwho.is)."))
        }
        "Modo StandBy" -> buildList {
            val styles = listOf("Aleatório", "Relógio", "Clima", "Fotos", "Papel de parede")
            add(SChoice("Estilo", settings.standbyStyle, "Aleatório alterna relógio, clima, fotos e papel de parede", Icons.STANDBY, Color(0xFF48484A)) {
                settings.standbyStyle = styles[(styles.indexOf(settings.standbyStyle) + 1) % styles.size]
            })
            val afters = listOf(0, 5, 10, 15, 30)
            add(SChoice("Começar sozinho", if (settings.standbyAfter == 0) "Nunca" else "após ${settings.standbyAfter} min parado") {
                settings.standbyAfter = afters[(afters.indexOf(settings.standbyAfter) + 1) % afters.size]
            })
            add(SLink("Abrir StandBy agora") { onStandBy() })
            add(SHeader("Timer para desligar"))
            val left = HomeOverrideService.sleepMinutesLeft()
            add(SChoice("Desligar a TV", left?.let { "em $it min" } ?: "Desligado", icon = Icons.MOON, color = Color(0xFF5E5CE6)) {
                val steps = listOf(0, 30, 60, 90, 120)
                val next = steps.firstOrNull { it > (left ?: 0) } ?: 0
                notice = if (!HomeOverrideService.setSleepTimer(next)) "Ative o OSGlass em Acessibilidade" else if (next == 0) "Timer desligado" else "Desliga em $next min"
                tick++
            })
        }
        "Controle e acessibilidade" -> buildList {
            add(SHeader("Botão Home"))
            add(SInfo("OSGlass no botão Home", if (HomeOverride.isEnabled(ctx)) "Ativo" else "Desativado", SIcons.REMOTE, Color(0xFF636366)))
            add(SLink("Abrir ajustes de acessibilidade") { HomeOverride.openAccessibilitySettings(ctx) })
            if (HomeOverride.projectivyEnabled(ctx)) {
                add(SCaption("O Projectivy também está ativo. Desative o serviço dele para os dois não disputarem o botão Home.", warn = true))
            }
            add(SHeader("Pelo computador"))
            add(SCaption(HomeOverride.ADB_COMMAND, mono = true))
            add(SCaption("O mesmo serviço faz funcionar Desligar e Timer na Central de controle."))
        }
        "Apps" -> buildList {
            val favs = state.favorites()
            add(SHeader("Favoritos (${favs.size})"))
            favs.forEach { app -> add(SLink(app.label, "Remover") { state.toggleFavorite(app.pkg) }) }
            val others = state.others()
            if (others.isNotEmpty()) {
                add(SHeader("Adicionar aos favoritos"))
                others.forEach { app -> add(SLink(app.label, "Adicionar") { state.toggleFavorite(app.pkg) }) }
            }
            val folders = settings.folders
            if (folders.isNotEmpty()) {
                add(SHeader("Pastas"))
                folders.forEach { f -> add(SLink(f.name, "${f.apps.size} apps · Desfazer pasta") { settings.deleteFolder(f.name) }) }
            }
            add(SCaption("Para criar uma pasta: segure OK num app › Colocar numa pasta."))
            val hidden = state.hiddenApps()
            if (hidden.isNotEmpty()) {
                add(SHeader("Apps ocultos (${hidden.size})"))
                hidden.forEach { app -> add(SLink(app.label, "Mostrar") { state.unhide(app.pkg) }) }
            }
        }
        "Recomendações" -> buildList {
            if (state.hasContentPermission) add(SInfo("Permissão de conteúdo", "Concedida", SIcons.STAR, Color(0xFFFF9F0A)))
            else add(SLink("Permitir acesso ao conteúdo", "", "necessário para os destaques", SIcons.STAR, Color(0xFFFF9F0A)) { permissionLauncher.launch(TvContent.PERMISSION) })
            add(SToggle("Mostrar recomendações", settings.recsEnabled) { settings.recsEnabled = it })
            add(SToggle("Continuar assistindo", settings.continueWatching) { settings.continueWatching = it })
            val pkgs = (state.channels.keys + state.watchNext.map { it.pkg }).distinct()
            add(SHeader("Apps que publicam conteúdo"))
            if (pkgs.isEmpty()) add(SCaption("Nenhum app publicou conteúdo ainda."))
            pkgs.forEach { pkg ->
                val label = state.byPkg[pkg]?.label ?: pkg
                add(SToggle(label, pkg !in settings.recsDisabledApps) { on ->
                    settings.recsDisabledApps = if (on) settings.recsDisabledApps - pkg else settings.recsDisabledApps + pkg
                })
            }
            add(SCaption("As sugestões “Para você” do Google TV vêm do servidor do Google e não ficam disponíveis para outros launchers."))
        }
        "Desempenho" -> buildList {
            add(SToggle("Modo leve", settings.liteMode, "desliga desfoque, brilho e sombras", SIcons.BOLT, Color(0xFF30D158)) { settings.liteMode = it })
            val total = TvSystem.totalMemoryMb(ctx)
            val free = state.availableMemoryMb()
            add(SMeter("Memória", 1f - free.toFloat() / total, "$free MB livres de ${"%.1f".format(total / 1024f)} GB", "Liberar") {
                scope.launch { notice = "Liberando…"; val mb = state.freeMemory(); notice = "Liberou $mb MB"; tick++ }
            })
            val (used, totalDisk) = TvSystem.storage()
            add(SMeter("Armazenamento", used.toFloat() / totalDisk, "%.1f GB usados de %.1f GB".format(used / 1e9, totalDisk / 1e9)))
            add(SInfo("Memória usada pelo OSGlass", "${TvSystem.ownMemoryMb(ctx)} MB"))
        }
        "Ajustes da TV" -> listOf(
            SCaption("Atalhos que abrem direto a tela certa do Android e da TCL."),
            STiles(
                listOf(
                    "Imagem" to { openAndroid(Settings.ACTION_DISPLAY_SETTINGS) },
                    "Som da TV" to { openAndroid(Settings.ACTION_SOUND_SETTINGS) },
                    "Rede e internet" to { openAndroid(Settings.ACTION_WIRELESS_SETTINGS) },
                    "Data e hora" to { openAndroid(Settings.ACTION_DATE_SETTINGS) },
                    "Idioma" to { openAndroid(Settings.ACTION_LOCALE_SETTINGS) },
                    "Teclado" to { openAndroid(Settings.ACTION_INPUT_METHOD_SETTINGS) },
                    "Protetor de tela" to { openAndroid(Settings.ACTION_DREAM_SETTINGS) },
                    "Apps" to { openAndroid(Settings.ACTION_APPLICATION_SETTINGS) },
                    "Armazenamento" to { openAndroid(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) },
                    "Acessibilidade" to { openAndroid(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    "Sobre a TV" to { openAndroid(Settings.ACTION_DEVICE_INFO_SETTINGS) },
                    "Painel rápido TCL" to { openTclPanel() },
                    "Todos os ajustes" to { openAndroid(Settings.ACTION_SETTINGS) },
                ),
            ),
        )
        "Sobre" -> listOf(
            SInfo("Versão", runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty() }.getOrDefault(""), SIcons.INFO, Color(0xFF8E8E93)),
            SInfo("Modelo", TvSystem.deviceName),
            SInfo("Sistema", TvSystem.androidVersion),
            SInfo("Licença", "MIT · código aberto"),
            SInfo("Código-fonte", "github.com/m1theusr/osglass-launcher"),
            SInfo("Desenvolvido por", "m1theusr"),
        )
        else -> emptyList()
    }

    val rows: List<SRow> = if (cat == SEARCH) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) listOf(SCaption("Digite o que procura, por exemplo “volume”, “papel de parede” ou “memória”."))
        else (listOf(PROFILE) + Groups.flatten().map { it.name }).flatMap { name ->
            rowsFor(name).filter { r ->
                val label = when (r) { is SToggle -> r.label; is SChoice -> r.label; is SLink -> r.label; is SInfo -> r.label; is SSlider -> r.label; is SMeter -> r.label; is SEdit -> r.label; else -> "" }
                label.lowercase().contains(q)
            }
        }.ifEmpty { listOf(SCaption("Nada encontrado para “$query”.")) }
    } else rowsFor(cat)

    Box(Modifier.fillMaxSize()) {
        wallpaper?.let { Image(it.sharp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        val theme = LocalGlassTheme.current
        Box(Modifier.fillMaxSize().background(theme.scrim))
        val m = with(LocalDensity.current) { 28.dp.toPx() }
        CompositionLocalProvider(LocalBackdrop provides wallpaper, LocalBringIntoViewSpec provides EdgeSpec(m, m)) {
            Row(Modifier.fillMaxSize()) {
                // ---- Barra lateral
                Box(
                    Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = if (theme.dark) 0.06f else 0.3f)),
                ) {
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 30.dp)) {
                        item {
                            SidebarItem(
                                "Buscar", SIcons.SEARCH, null, cat == SEARCH, catReq(SEARCH),
                                onFocus = { cat = SEARCH }, onClick = { focusManager.moveFocus(FocusDirection.Right) }, search = true,
                            )
                        }
                        item {
                            ProfileCard(settings.profileName, settings.profileColor, cat == PROFILE, catReq(PROFILE), onFocus = { cat = PROFILE }) {
                                focusManager.moveFocus(FocusDirection.Right)
                            }
                        }
                        Groups.forEach { group ->
                            item { Spacer(Modifier.height(12.dp)) }
                            items(group, key = { it.name }) { c ->
                                SidebarItem(c.name, c.icon, c.color, cat == c.name, catReq(c.name), onFocus = { cat = c.name }) {
                                    focusManager.moveFocus(FocusDirection.Right)
                                }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.CenterEnd).width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))
                }
                // ---- Conteúdo
                key(cat) {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight().onFocusChanged { contentFocused = it.hasFocus },
                        contentPadding = PaddingValues(start = 30.dp, end = 40.dp, top = 30.dp, bottom = 50.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(cat, Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp), style = TitleStyle.copy(color = theme.content, fontSize = 30.sp, fontWeight = FontWeight.Bold, shadow = null))
                                notice?.let { BasicText(it, style = LabelStyle.copy(fontSize = 13.sp, color = Color(0xFF8FF0A8))) }
                            }
                        }
                        if (cat == SEARCH) item { SearchField(query) { query = it } }
                        items(rows.size) { i -> RowView(rows[i], state) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ Barra lateral */

@Composable
private fun SidebarItem(
    name: String,
    icon: String,
    color: Color?,
    selected: Boolean,
    requester: FocusRequester,
    onFocus: () -> Unit,
    search: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(0.6f, 500f), label = "side")
    val bg by animateColorAsState(
        when {
            focused -> theme.focusBg
            selected -> theme.content.copy(alpha = 0.14f)
            search -> theme.content.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "sideBg",
    )
    val fg = if (focused) theme.focusFg else theme.content
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .height(if (search) 40.dp else 38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .onSelect(onClick)
            .focusable()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (color != null) IconTile(icon, color) else GlyphIcon(icon, fg.copy(alpha = 0.75f), 17.dp)
        BasicText(name, style = LabelStyle.copy(color = if (search && !focused) fg.copy(alpha = 0.7f) else fg, fontSize = 14.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProfileCard(name: String, colorIndex: Int, selected: Boolean, requester: FocusRequester, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, spring(0.6f, 500f), label = "prof")
    val bg by animateColorAsState(if (focused) theme.focusBg else theme.content.copy(alpha = if (selected) 0.16f else 0.09f), label = "profBg")
    val fg = if (focused) theme.focusFg else theme.content
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .onSelect(onClick)
            .focusable()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).background(Brush.linearGradient(ProfileColors[colorIndex.coerceIn(0, ProfileColors.lastIndex)]), CircleShape),
            contentAlignment = Alignment.Center,
        ) { BasicText(name.take(1).uppercase().ifEmpty { "•" }, style = TitleStyle.copy(fontSize = 19.sp, shadow = null)) }
        Column {
            BasicText(name, style = LabelStyle.copy(color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, shadow = null))
            BasicText("Perfil e backup", style = LabelStyle.copy(color = fg.copy(alpha = 0.65f), fontSize = 12.sp, fontWeight = FontWeight.Normal, shadow = null))
        }
    }
}

@Composable
private fun IconTile(icon: String, color: Color) {
    Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        GlyphIcon(icon, Color.White, 16.dp)
    }
}

/* ------------------------------------------------------------------ Linhas */

@Composable
private fun RowShell(
    onClick: (() -> Unit)?,
    onHorizontal: ((Boolean) -> Unit)? = null,
    content: @Composable RowScope.(fg: Color, sub: Color) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.015f else 1f, spring(0.6f, 500f), label = "row")
    val bg by animateColorAsState(if (focused) theme.focusBg else theme.surface, label = "rowBg")
    val fg = if (focused) theme.focusFg else theme.content
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.onSelect(onClick) else Modifier)
            .then(
                if (onHorizontal != null) Modifier.onPreviewKeyEvent { e ->
                    val code = e.nativeKeyEvent.keyCode
                    if (code != KEYCODE_DPAD_LEFT && code != KEYCODE_DPAD_RIGHT) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown) onHorizontal(code == KEYCODE_DPAD_RIGHT)
                    true
                } else Modifier,
            )
            .focusable()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content(fg, fg.copy(alpha = 0.55f)) }
}

@Composable
private fun RowLabel(label: String, sub: String?, fg: Color, subColor: Color, modifier: Modifier) {
    Column(modifier) {
        BasicText(label, style = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
        sub?.let { BasicText(it, style = LabelStyle.copy(color = subColor, fontSize = 12.sp, fontWeight = FontWeight.Normal, shadow = null), maxLines = 2) }
    }
}

@Composable
private fun RowView(row: SRow, state: GlassState) {
    val theme = LocalGlassTheme.current
    when (row) {
        is SEdit -> EditRow(row)
        is SHeader -> BasicText(row.text, Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp), style = LabelStyle.copy(fontSize = 13.sp, color = theme.content.copy(alpha = 0.6f), shadow = null))
        is SCaption -> when {
            row.mono -> BasicText(
                row.text,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)).padding(12.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontFamily = FontFamily.Monospace),
            )
            row.warn -> BasicText(
                row.text,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFAA28)).border(1.dp, Color(0x4DFFBE5A), RoundedCornerShape(12.dp)).padding(12.dp),
                style = LabelStyle.copy(color = Color(0xFFFFD38A), fontSize = 13.sp, fontWeight = FontWeight.Normal, shadow = null),
            )
            else -> BasicText(row.text, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = LabelStyle.copy(color = theme.content.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Normal, shadow = null))
        }
        is SToggle -> RowShell({ row.onChange(!row.value) }) { fg, sub ->
            row.icon?.let { IconTile(it, row.color ?: Color.Gray) }
            RowLabel(row.label, row.sub, fg, sub, Modifier.weight(1f))
            if (row.value) Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF30D158)))
            BasicText(if (row.value) "Ligado" else "Desligado", style = LabelStyle.copy(color = sub, fontSize = 14.sp, shadow = null))
        }
        is SChoice -> RowShell(row.onNext) { fg, sub ->
            row.icon?.let { IconTile(it, row.color ?: Color.Gray) }
            RowLabel(row.label, row.sub, fg, sub, Modifier.weight(1f))
            BasicText("${row.value}  ›", style = LabelStyle.copy(color = sub, fontSize = 14.sp, shadow = null))
        }
        is SLink -> RowShell(row.onClick) { fg, sub ->
            row.icon?.let { IconTile(it, row.color ?: Color.Gray) }
            RowLabel(row.label, row.sub, fg, sub, Modifier.weight(1f))
            BasicText(if (row.value.isEmpty()) "›" else "${row.value}  ›", style = LabelStyle.copy(color = sub, fontSize = 14.sp, shadow = null))
        }
        is SInfo -> RowShell(null) { fg, sub ->
            row.icon?.let { IconTile(it, row.color ?: Color.Gray) }
            RowLabel(row.label, null, fg, sub, Modifier.weight(1f))
            BasicText(row.value, style = LabelStyle.copy(color = sub, fontSize = 14.sp, shadow = null))
        }
        is SSlider -> RowShell(null, onHorizontal = row.onDelta) { fg, sub ->
            IconTile(SIcons.SOUND, Color(0xFFFF375F))
            BasicText(row.label, Modifier.width(80.dp), style = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null))
            BasicText("‹", style = LabelStyle.copy(color = sub, fontSize = 18.sp, shadow = null))
            Bar(if (row.muted) 0f else row.percent / 100f, fg, Modifier.weight(1f))
            BasicText("›", style = LabelStyle.copy(color = sub, fontSize = 18.sp, shadow = null))
            BasicText(if (row.muted) "Mudo" else "${row.percent}%", Modifier.width(48.dp), style = LabelStyle.copy(color = sub, fontSize = 14.sp, shadow = null))
        }
        is SMeter -> RowShell(row.onAction) { fg, sub ->
            BasicText(row.label, Modifier.width(120.dp), style = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null))
            Bar(row.fraction, fg, Modifier.weight(1f))
            BasicText(row.value, style = LabelStyle.copy(color = sub, fontSize = 13.sp, shadow = null))
            row.action?.let {
                BasicText(
                    it,
                    Modifier.clip(CircleShape).background(fg.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 5.dp),
                    style = LabelStyle.copy(color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, shadow = null),
                )
            }
        }
        SWallpapers -> WallpaperRow(state)
        is STiles -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            row.items.chunked(3).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    line.forEach { (label, action) -> Tile(label, action, Modifier.weight(1f)) }
                    repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier) {
    Box(modifier.height(6.dp).clip(CircleShape).background(color.copy(alpha = 0.2f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

@Composable
private fun Tile(label: String, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, spring(0.6f, 500f), label = "tile")
    val fg = if (focused) theme.focusFg else theme.content
    Column(
        modifier
            .height(66.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) theme.focusBg else theme.surface)
            .onFocusChanged { focused = it.isFocused }
            .onSelect(onClick)
            .focusable()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(label, style = LabelStyle.copy(color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, shadow = null))
        BasicText("abre no Android", style = LabelStyle.copy(color = fg.copy(alpha = 0.55f), fontSize = 11.sp, fontWeight = FontWeight.Normal, shadow = null))
    }
}

@Composable
private fun WallpaperRow(state: GlassState) {
    val ctx = LocalContext.current
    val list = remember { Wallpapers.list(ctx) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 2.dp)) {
        items(list, key = { it }) { id ->
            val thumb by produceState<ImageBitmap?>(null, id) { value = Wallpapers.thumbnail(ctx, id) }
            var focused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(0.6f, 450f), label = "thumb")
            val selected = state.settings.wallpaper == id
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(128.dp, 72.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(if (selected || focused) 2.dp else 0.dp, if (focused) Color.White else Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .onSelect { state.settings.wallpaper = id }
                        .focusable(),
                ) { thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                BasicText(Wallpapers.displayName(id), style = LabelStyle.copy(fontSize = 12.sp, color = Color.White.copy(alpha = if (selected) 1f else 0.7f), shadow = null))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(SIcons.SEARCH, if (focused) Dark else Color.White.copy(alpha = 0.7f), 18.dp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = LabelStyle.copy(color = if (focused) Dark else Color.White, fontSize = 16.sp, shadow = null),
            cursorBrush = SolidColor(if (focused) Dark else Color.White),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) BasicText("Buscar nos ajustes", style = LabelStyle.copy(color = (if (focused) Dark else Color.White).copy(alpha = 0.45f), fontSize = 16.sp, shadow = null))
                    inner()
                }
            },
        )
    }
}

/** Linha com texto editável: OK abre o teclado; "Concluído" salva. */
@Composable
private fun EditRow(row: SEdit) {
    var editing by remember { mutableStateOf(false) }
    var wasEditing by remember { mutableStateOf(false) }
    var text by remember(row.value) { mutableStateOf(row.value) }
    var focused by remember { mutableStateOf(false) }
    val theme = LocalGlassTheme.current
    val rowFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        withFrameNanos { }
        if (editing) { runCatching { fieldFocus.requestFocus() }; keyboard?.show(); wasEditing = true }
        else if (wasEditing) runCatching { rowFocus.requestFocus() }
    }
    val active = focused || editing
    val fg = if (active) theme.focusFg else theme.content
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) theme.focusBg else theme.surface)
            .focusRequester(rowFocus)
            .onFocusChanged { focused = it.isFocused }
            .then(if (!editing) Modifier.onSelect { editing = true } else Modifier)
            .focusable(enabled = !editing)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(row.label, Modifier.width(120.dp), style = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null))
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                textStyle = LabelStyle.copy(color = fg, fontSize = 15.sp, shadow = null),
                cursorBrush = SolidColor(fg),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); row.onDone(text); editing = false }),
                modifier = Modifier.weight(1f).focusRequester(fieldFocus).onFocusChanged {
                    if (!it.isFocused && editing && wasEditing) { row.onDone(text); editing = false }
                },
            )
        } else {
            BasicText(row.value.ifEmpty { row.placeholder }, Modifier.weight(1f), style = LabelStyle.copy(color = fg.copy(alpha = if (row.value.isEmpty()) 0.5f else 0.8f), fontSize = 14.sp, shadow = null), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("Editar  ›", style = LabelStyle.copy(color = fg.copy(alpha = 0.5f), fontSize = 13.sp, shadow = null))
        }
    }
}
