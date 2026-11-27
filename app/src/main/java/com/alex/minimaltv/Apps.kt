package com.alex.minimaltv

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** Um app que dá pra abrir na TV. */
data class App(val pkg: String, val label: String)

object Apps {

    /** Todos os apps que abrem (TV e celular), sem repetir e sem o nosso. */
    fun launchable(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, App>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val i = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || seen.containsKey(pkg)) continue
                seen[pkg] = App(pkg, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun icon(ctx: Context, pkg: String): Drawable? =
        try { ctx.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    /** Abre o app. Prefere a versão de TV. Retorna false se não deu. */
    fun open(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }
}

/** Lista de favoritos salva na TV (máx. 20). */
object Favorites {
    const val MAX = 20
    private const val KEY = "favorites"

    private val DEFAULTS = listOf(
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.globo.globotv",
        "com.google.android.youtube.tv",
    )

    fun load(ctx: Context): MutableList<String> {
        val prefs = ctx.getSharedPreferences("minimaltv", Context.MODE_PRIVATE)
        val installed = Apps.launchable(ctx).map { it.pkg }.toSet()
        val saved = prefs.getString(KEY, null)
        val list = if (saved == null) {
            // Primeira vez: começa com os principais que estiverem instalados.
            DEFAULTS.filter { it in installed }.ifEmpty { installed.take(4) }
        } else {
            saved.split(",").filter { it.isNotBlank() }
        }
        // Remove apps desinstalados/desativados.
        return list.filter { it in installed }.take(MAX).toMutableList()
    }

    fun save(ctx: Context, list: List<String>) {
        ctx.getSharedPreferences("minimaltv", Context.MODE_PRIVATE)
            .edit().putString(KEY, list.take(MAX).joinToString(",")).apply()
    }
}
