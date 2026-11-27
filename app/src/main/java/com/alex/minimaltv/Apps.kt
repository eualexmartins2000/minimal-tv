package com.alex.minimaltv

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    private val iconCache = HashMap<String, Drawable.ConstantState>()

    /** Ícone do app (guardado em memória: reconstruir a faixa é rápido). */
    fun icon(ctx: Context, pkg: String): Drawable? = try {
        iconCache[pkg]?.newDrawable()
            ?: ctx.packageManager.getApplicationIcon(pkg).also { d -> d.constantState?.let { iconCache[pkg] = it } }
    } catch (e: Exception) { null }

    fun label(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private val colors = HashMap<String, Int>()

    /**
     * Cor que mais aparece no ícone (ignora transparente).
     * Desenha o ícone em 24x24 e conta as cores agrupadas. Fica guardada em memória.
     */
    fun color(pkg: String, icon: Drawable): Int = colors.getOrPut(pkg) {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val old = icon.copyBounds()
        icon.setBounds(0, 0, n, n)
        icon.draw(c)
        icon.bounds = old
        val count = HashMap<Int, Int>()
        val sum = HashMap<Int, IntArray>()
        for (y in 0 until n) for (x in 0 until n) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 200) continue
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            val key = (r shr 5 shl 6) or (g shr 5 shl 3) or (b shr 5)
            count[key] = (count[key] ?: 0) + 1
            val s = sum.getOrPut(key) { IntArray(3) }
            s[0] += r; s[1] += g; s[2] += b
        }
        bmp.recycle()
        val best = count.maxByOrNull { it.value } ?: return@getOrPut Color.parseColor("#C8D0DA")
        val s = sum[best.key]!!
        Color.rgb(s[0] / best.value, s[1] / best.value, s[2] / best.value)
    }

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
