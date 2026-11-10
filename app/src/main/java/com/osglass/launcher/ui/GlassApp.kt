package com.osglass.launcher.ui

import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.osglass.launcher.data.AppEntry
import com.osglass.launcher.data.AppLoader
import com.osglass.launcher.data.ContentItem
import com.osglass.launcher.data.GlassSettings
import com.osglass.launcher.data.TvContent
import com.osglass.launcher.data.TvInputEntry
import com.osglass.launcher.data.TvInputs
import com.osglass.launcher.glass.Backdrop
import com.osglass.launcher.glass.Wallpapers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

enum class Screen { Onboarding, Home, Settings, StandBy, Profiles, NewProfile }

const val DOCK_SIZE = 6

/** Estado compartilhado entre as telas: apps, conteúdo dos apps, entradas e ações. */
class GlassState(val ctx: Context, val settings: GlassSettings) {
    var apps by mutableStateOf<List<AppEntry>?>(null)
    var channels by mutableStateOf<Map<String, List<ContentItem>>>(emptyMap())
    var watchNext by mutableStateOf<List<ContentItem>>(emptyList())
    var inputs by mutableStateOf<List<TvInputEntry>>(emptyList())
    var hasContentPermission by mutableStateOf(TvContent.hasPermission(ctx))
    var weather by mutableStateOf<com.osglass.launcher.data.Weather?>(null)
    var nowPlaying by mutableStateOf<com.osglass.launcher.data.NowPlaying?>(null)
    var appsKey by mutableIntStateOf(0)
    var contentKey by mutableIntStateOf(0)

    val byPkg: Map<String, AppEntry> get() = apps.orEmpty().associateBy { it.pkg }

    /** O dock tem no máximo [DOCK_SIZE] apps, como a fileira de cima do tvOS. */
    fun favorites(): List<AppEntry> {
        val map = byPkg
        return settings.favorites.mapNotNull { map[it] }.filter { it.pkg !in settings.hidden }.take(DOCK_SIZE)
    }

    /** Tudo que não está no dock — inclusive favoritos além do 6º, que aparecem primeiro na grade. */
    fun others(): List<AppEntry> {
        val dock = favorites().map { it.pkg }.toSet()
        val map = byPkg
        val extraFavs = settings.favorites.filter { it !in dock && it !in settings.hidden }.mapNotNull { map[it] }
        val rest = apps.orEmpty().filter { it.pkg !in dock && it.pkg !in settings.favorites && it.pkg !in settings.hidden }
        return extraFavs + rest
    }

    fun hiddenApps(): List<AppEntry> = apps.orEmpty().filter { it.pkg in settings.hidden }

    fun contentFor(pkg: String): List<ContentItem> {
        if (!settings.recsEnabled || settings.kidsProfile || pkg in settings.recsDisabledApps) return emptyList()
        return channels[pkg].orEmpty().ifEmpty { watchNext.filter { it.pkg == pkg } }
    }

    fun continueWatching(): List<ContentItem> =
        if (settings.recsEnabled && settings.continueWatching && !settings.kidsProfile) watchNext.filter { it.pkg !in settings.recsDisabledApps }
        else emptyList()

    fun toggleFavorite(pkg: String) {
        val list = settings.favorites.filter { it !in settings.hidden }.toMutableList()
        if (pkg in settings.favorites) {
            settings.favorites = settings.favorites - pkg
        } else {
            // Dock cheio: o novo entra na última vaga e o 6º desce para a grade.
            list.add(minOf(list.size, DOCK_SIZE - 1), pkg)
            settings.favorites = list
        }
    }

    fun hide(pkg: String) {
        settings.hidden = settings.hidden + pkg
        settings.favorites = settings.favorites - pkg
    }

    fun unhide(pkg: String) { settings.hidden = settings.hidden - pkg }

    fun moveFavorite(pkg: String, delta: Int) {
        val list = settings.favorites.toMutableList()
        val i = list.indexOf(pkg)
        val j = (i + delta).coerceIn(0, minOf(list.lastIndex, DOCK_SIZE - 1))
        if (i < 0 || i == j) return
        list.add(j, list.removeAt(i))
        settings.favorites = list
    }

    fun cycleWallpaper(delta: Int) {
        val list = Wallpapers.list(ctx)
        if (list.isEmpty()) return
        val i = list.indexOf(settings.wallpaper).coerceAtLeast(0)
        settings.wallpaper = list[(i + delta).mod(list.size)]
    }

    fun start(intent: Intent, options: Bundle? = null): Boolean =
        runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options) }.isSuccess

    fun launchApp(pkg: String, view: View? = null, bounds: Rect? = null) {
        val intent = AppLoader.launchIntent(ctx, pkg) ?: return
        val opts = if (view != null && bounds != null && bounds.width > 0) {
            android.app.ActivityOptions.makeScaleUpAnimation(
                view, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt(),
            ).toBundle()
        } else null
        start(intent, opts)
    }

    /* ---- status da TV para a Central de controle ---- */

    fun networkLabel(): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "—"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sem conexão"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Conectado"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cabo de rede"
            else -> "Conectado"
        }
    }

    fun bluetoothOn(): Boolean? = runCatching {
        ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
    }.getOrNull()

    fun availableMemoryMb(): Long {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.availMem / (1024 * 1024)
    }

    /** Fecha processos parados em segundo plano. Devolve quantos MB ficaram livres a mais. */
    suspend fun freeMemory(): Long = withContext(Dispatchers.IO) {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val before = availableMemoryMb()
        ctx.packageManager.getInstalledApplications(0)
            .map { it.packageName }
            .filter { it != ctx.packageName }
            .forEach { runCatching { am.killBackgroundProcesses(it) } }
        delay(800)
        (availableMemoryMb() - before).coerceAtLeast(0)
    }
}

@Composable
fun GlassApp(homeEvents: SharedFlow<Unit>, resumeEvents: SharedFlow<Unit>) {
    val ctx = LocalContext.current
    val settings = remember { GlassSettings(ctx) }
    val state = remember { GlassState(ctx, settings) }
    val (screenW, screenH) = remember {
        val dm = ctx.resources.displayMetrics
        maxOf(dm.widthPixels, dm.heightPixels) to minOf(dm.widthPixels, dm.heightPixels)
    }
    // Depois que a TV reinicia (contador de boot mudou), pergunta quem está usando — se houver mais de um perfil.
    val boot = remember { bootCount(ctx) }
    var screen by remember {
        mutableStateOf(
            when {
                !settings.onboardingDone -> Screen.Onboarding
                settings.askProfileOnBoot && boot != settings.lastBootCount && settings.profiles().size > 1 -> Screen.Profiles
                else -> Screen.Home
            },
        )
    }
    var newProfileReturn by remember { mutableStateOf(Screen.Home) }
    var editProfileId by remember { mutableStateOf<String?>(null) }
    var pinReq by remember { mutableStateOf<PinRequest?>(null) }
    val pinGate: (PinRequest) -> Unit = { pinReq = it }

    /** Troca de perfil com as proteções: sair do infantil pede o PIN dos pais; entrar num perfil com PIN pede o PIN dele. */
    fun switchGuarded(id: String) {
        if (id == settings.currentProfile) return
        val target = settings.profileById(id) ?: return
        fun enter() {
            if (target.pin.isNotEmpty()) pinGate(PinRequest("PIN de ${target.name}", null, verifyHash = target.pin) { settings.switchProfile(id) })
            else settings.switchProfile(id)
        }
        if (settings.kidsProfile && settings.parentPin.isNotEmpty()) {
            pinGate(PinRequest("PIN dos pais", "Para sair do perfil infantil", verifyHash = settings.parentPin) { enter() })
        } else enter()
    }

    /** No perfil infantil, ações de adulto (Ajustes, novo perfil) pedem o PIN dos pais. */
    fun adultOnly(action: () -> Unit) {
        if (settings.kidsProfile && settings.parentPin.isNotEmpty()) {
            pinGate(PinRequest("PIN dos pais", "Esta opção é protegida no perfil infantil", verifyHash = settings.parentPin) { action() })
        } else action()
    }
    LaunchedEffect(Unit) { settings.lastBootCount = boot }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { state.appsKey++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    LaunchedEffect(state.appsKey) {
        val loaded = AppLoader.load(ctx)
        if (!settings.favoritesInitialized) {
            val installed = loaded.map { it.pkg }.toSet()
            settings.favorites = GlassSettings.DEFAULT_FAVORITES.filter { it in installed }
            settings.favoritesInitialized = true
        }
        state.apps = loaded
    }
    LaunchedEffect(state.contentKey, settings.recsEnabled, state.hasContentPermission, settings.appsOnly) {
        if (settings.recsEnabled && state.hasContentPermission && !settings.appsOnly) {
            state.channels = TvContent.loadChannels(ctx)
            state.watchNext = TvContent.loadWatchNext(ctx)
        } else {
            state.channels = emptyMap()
            state.watchNext = emptyList()
        }
    }
    LaunchedEffect(Unit) { state.inputs = withContext(Dispatchers.IO) { TvInputs.list(ctx) } }
    LaunchedEffect(Unit) {
        resumeEvents.collect {
            state.hasContentPermission = TvContent.hasPermission(ctx)
            state.contentKey++
        }
    }
    LaunchedEffect(Unit) { homeEvents.collect { if (screen != Screen.Onboarding && screen != Screen.Profiles) screen = Screen.Home } }
    LaunchedEffect(settings.weatherOn, settings.weatherCity, settings.weatherUnit, settings.appsOnly) {
        if (!settings.weatherOn || settings.appsOnly) { state.weather = null; return@LaunchedEffect }
        while (true) {
            com.osglass.launcher.data.WeatherRepo.load(settings.weatherCity, settings.weatherUnit == "F")?.let { state.weather = it }
            delay(30 * 60_000L)
        }
    }
    LaunchedEffect(settings.islandOn, settings.appsOnly) {
        while (settings.islandOn && !settings.appsOnly) {
            state.nowPlaying = if (com.osglass.launcher.data.Media.hasAccess(ctx)) withContext(Dispatchers.IO) { com.osglass.launcher.data.Media.current(ctx) } else null
            delay(3_000)
        }
        state.nowPlaying = null
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            val after = settings.standbyAfter
            val idle = android.os.SystemClock.elapsedRealtime() - com.osglass.launcher.MainActivity.lastInteraction
            if (after > 0 && screen == Screen.Home && com.osglass.launcher.MainActivity.isResumed && idle > after * 60_000L) screen = Screen.StandBy
        }
    }

    // Só apps: papel de parede em meia resolução (~2 MB em vez de ~8 MB) e cache de imagens limpo.
    val wallpaper by produceState<Backdrop?>(null, settings.wallpaper, settings.appsOnly) {
        value = Wallpapers.load(ctx, settings.wallpaper, screenW, screenH, div = if (settings.appsOnly) 2 else 1)
    }
    LaunchedEffect(settings.appsOnly) { if (settings.appsOnly) com.osglass.launcher.data.Images.clear() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
      androidx.compose.runtime.CompositionLocalProvider(
          // Modo leve / só apps: vidro só fosco (sem a imagem nítida nem a refração, que pesam na GPU).
          com.osglass.launcher.glass.LocalGlassStyle provides com.osglass.launcher.glass.GlassStyle.forLevel(settings.glassLevel)
              .let { if (settings.liteMode || settings.appsOnly) it.copy(clarity = 0f) else it },
          com.osglass.launcher.glass.LocalGlassTheme provides com.osglass.launcher.glass.GlassTheme(settings.themeDark),
          LocalPinGate provides pinGate,
      ) {
        AnimatedContent(
            screen,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
            label = "screen",
        ) { s ->
            when (s) {
                Screen.Home -> HomeScreen(
                    state, wallpaper, screenW, screenH, homeEvents,
                    onStandBy = { screen = Screen.StandBy },
                    onOpenSettings = { adultOnly { screen = Screen.Settings } },
                    onNewProfile = { adultOnly { newProfileReturn = Screen.Home; editProfileId = null; screen = Screen.NewProfile } },
                    onSwitchProfile = { switchGuarded(it) },
                )
                Screen.Onboarding -> OnboardingScreen(state, wallpaper) { screen = Screen.Home }
                Screen.Profiles -> ProfilePickerScreen(
                    state, wallpaper,
                    onPicked = { screen = Screen.Home },
                    onAdd = { newProfileReturn = Screen.Profiles; editProfileId = null; screen = Screen.NewProfile },
                    onEdit = { id -> newProfileReturn = Screen.Profiles; editProfileId = id; screen = Screen.NewProfile },
                )
                Screen.NewProfile -> ProfileWizard(
                    state, screenW, screenH, editProfileId,
                    onDone = { screen = if (editProfileId != null) newProfileReturn else Screen.Home },
                    onCancel = { screen = newProfileReturn },
                )
                Screen.Settings -> SettingsScreen(
                    state, wallpaper,
                    onExit = { screen = Screen.Home },
                    onStandBy = { screen = Screen.StandBy },
                    onRedoOnboarding = { settings.onboardingDone = false; screen = Screen.Onboarding },
                    onNewProfile = { newProfileReturn = Screen.Settings; editProfileId = null; screen = Screen.NewProfile },
                    onPickProfile = { screen = Screen.Profiles },
                    onEditProfile = { id -> newProfileReturn = Screen.Settings; editProfileId = id; screen = Screen.NewProfile },
                    onSwitchProfile = { switchGuarded(it) },
                )
                Screen.StandBy -> StandByScreen(state, wallpaper) { screen = Screen.Home }
            }
        }
        PinOverlay(pinReq) { pinReq = null }
      }
    }
}
