package com.osglass.launcher.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.osglass.launcher.glass.Wallpapers
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Configurações do Glass. Cada propriedade é um State do Compose gravado em SharedPreferences:
 * mudar um valor nos Ajustes atualiza a tela na hora e persiste.
 */
class GlassSettings(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)

    var onboardingDone by bool("onboarding_done", false)
    var favoritesInitialized by bool("favorites_init", sp.contains("favorites"))
    var favorites by stringList("favorites")
    var hidden by stringSet("hidden", DEFAULT_HIDDEN)
    var wallpaper by string("wallpaper", Wallpapers.DEFAULT)

    /** 0 = leve, 1 = médio, 2 = forte. */
    var glassLevel by int("glass_level", 1)
    /** Cards por linha: 5 (grande), 6 (médio), 7 (pequeno). */
    var columns by int("columns", 6)
    var alwaysShowLabels by bool("always_labels", false)
    var clock24h by bool("clock_24h", true)
    var showDate by bool("show_date", true)
    var showInputs by bool("show_inputs", true)
    var liteMode by bool("lite_mode", false)

    /** "Vidro" (padrão, estilo tvOS 27) ou "Original". */
    var iconPack by string("icon_pack", "Vidro")
    var rotateShelf by bool("rotate_shelf", true)
    /** Escurecer o fundo: 0, 1, 2, 3 (0%, 15%, 30%, 45%). */
    var wallDim by int("wall_dim", 0)
    var profileName by string("profile_name", "")
    var profileColor by int("profile_color", 0)

    var recsEnabled by bool("recs_enabled", true)
    var topShelf by bool("top_shelf", true)
    var continueWatching by bool("continue_watching", true)
    var recsDisabledApps by stringSet("recs_disabled", emptySet())

    // ---- Relógio e clima
    var weatherOn by bool("weather_on", true)
    /** Vazio = cidade automática (pela internet). */
    var weatherCity by string("weather_city", "")
    var weatherUnit by string("weather_unit", "C")
    var greeting by bool("greeting", true)

    // ---- StandBy
    var standbyStyle by string("standby_style", "Aleatório")
    /** Minutos parado na tela inicial até o StandBy começar sozinho (0 = nunca). */
    var standbyAfter by int("standby_after", 10)

    // ---- Ilha dinâmica
    var islandOn by bool("island_on", true)
    var themeDark by bool("theme_dark", true)

    // ---- Pastas
    private var foldersJson by string("folders", "[]")
    val folders: List<Folder>
        get() = runCatching {
            val arr = org.json.JSONArray(foldersJson)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val apps = o.getJSONArray("apps")
                Folder(o.getString("name"), (0 until apps.length()).map { apps.getString(it) })
            }
        }.getOrDefault(emptyList())

    fun setFolders(list: List<Folder>) {
        val arr = org.json.JSONArray()
        list.filter { it.apps.isNotEmpty() }.forEach { f ->
            arr.put(org.json.JSONObject().put("name", f.name).put("apps", org.json.JSONArray(f.apps)))
        }
        foldersJson = arr.toString()
    }

    fun putInFolder(pkg: String, name: String) {
        val list = folders.map { it.copy(apps = it.apps - pkg) }.toMutableList()
        val i = list.indexOfFirst { it.name == name }
        if (i >= 0) list[i] = list[i].copy(apps = list[i].apps + pkg) else list += Folder(name, listOf(pkg))
        setFolders(list)
    }

    fun removeFromFolders(pkg: String) = setFolders(folders.map { it.copy(apps = it.apps - pkg) })
    fun deleteFolder(name: String) = setFolders(folders.filter { it.name != name })

    // ---- Perfis: o perfil atual vive nas chaves normais; os outros ficam guardados em JSON.
    private var profilesJson by string("profiles", "")
    var currentProfile by string("current_profile", "p1")

    private fun snapshot() = Profile(currentProfile, profileName, profileColor, favorites, hidden, wallpaper)

    fun profiles(): List<Profile> {
        val stored = runCatching {
            val arr = org.json.JSONArray(profilesJson)
            (0 until arr.length()).map { Profile.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val others = stored.filter { it.id != currentProfile }
        val current = snapshot()
        val ordered = (stored.map { if (it.id == currentProfile) current else it }).ifEmpty { listOf(current) }
        return if (ordered.any { it.id == currentProfile }) ordered else listOf(current) + others
    }

    private fun saveProfiles(list: List<Profile>) {
        profilesJson = org.json.JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
    }

    fun switchProfile(id: String) {
        if (id == currentProfile) return
        val list = profiles()
        val target = list.firstOrNull { it.id == id } ?: return
        saveProfiles(list)
        profileName = target.name
        profileColor = target.color
        favorites = target.favorites
        hidden = target.hidden
        wallpaper = target.wallpaper
        currentProfile = id
    }

    fun addProfile(defaultFavorites: List<String>): String {
        val list = profiles()
        val n = list.size + 1
        val p = Profile("p" + System.currentTimeMillis(), "Perfil $n", n % ProfileColorCount, defaultFavorites, DEFAULT_HIDDEN, Wallpapers.DEFAULT)
        saveProfiles(list + p)
        switchProfile(p.id)
        return p.id
    }

    fun deleteProfile(id: String) {
        val list = profiles()
        if (list.size <= 1) return
        if (id == currentProfile) switchProfile(list.first { it.id != id }.id)
        saveProfiles(profiles().filter { it.id != id })
    }

    private fun bool(key: String, def: Boolean) = pref(sp.getBoolean(key, def)) { putBoolean(key, it) }
    private fun int(key: String, def: Int) = pref(sp.getInt(key, def)) { putInt(key, it) }
    private fun string(key: String, def: String) = pref(sp.getString(key, def) ?: def) { putString(key, it) }
    private fun stringList(key: String) =
        pref(sp.getString(key, "").orEmpty().split(',').filter { it.isNotBlank() }) { putString(key, it.joinToString(",")) }
    private fun stringSet(key: String, def: Set<String>) =
        pref(sp.getStringSet(key, null)?.toSet() ?: def) { putStringSet(key, it) }

    private fun <T> pref(initial: T, write: SharedPreferences.Editor.(T) -> Unit): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(initial)
            override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                state.value = value
                sp.edit { write(value) }
            }
        }

    companion object {
        /** Atalhos duplicados/inúteis que a TCL coloca na lista. Dá pra reexibir em Ajustes. */
        val DEFAULT_HIDDEN = setOf(
            "com.tcl.partnercustomizer",      // atalho "TCL Channel"
            "com.tcl.tv.tclhome_passive",
            "com.tcl.smartalexa",
            "com.google.android.videos",
            "com.spocky.projengmenu",
        )

        /** Ordem sugerida na primeira execução (só entra o que estiver instalado). */
        val DEFAULT_FAVORITES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.spotify.tv.android",
            "com.mercadolibre.android.mplay_tv",
            "com.limelight",
            "com.cloud.gaming",
        )
    }
}

/** Estado do serviço de acessibilidade que faz o botão Home abrir o Glass. */
object HomeOverride {
    private const val PROJECTIVY = "com.spocky.projengmenu/"
    const val ADB_COMMAND =
        "adb shell settings put secure enabled_accessibility_services com.osglass.launcher/.HomeOverrideService"

    private fun enabledServices(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()

    fun isEnabled(ctx: Context): Boolean = enabledServices(ctx).contains("${ctx.packageName}/")
    fun projectivyEnabled(ctx: Context): Boolean = enabledServices(ctx).contains(PROJECTIVY)

    fun openAccessibilitySettings(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

const val ProfileColorCount = 5

data class Folder(val name: String, val apps: List<String>)

data class Profile(
    val id: String,
    val name: String,
    val color: Int,
    val favorites: List<String>,
    val hidden: Set<String>,
    val wallpaper: String,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("id", id).put("name", name).put("color", color)
        .put("favorites", org.json.JSONArray(favorites)).put("hidden", org.json.JSONArray(hidden.toList()))
        .put("wallpaper", wallpaper)

    companion object {
        fun fromJson(o: org.json.JSONObject): Profile {
            fun list(key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            return Profile(
                o.getString("id"), o.optString("name", "Perfil"), o.optInt("color", 0),
                list("favorites"), list("hidden").toSet(), o.optString("wallpaper", Wallpapers.DEFAULT),
            )
        }
    }
}
