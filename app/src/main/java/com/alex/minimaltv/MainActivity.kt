package com.alex.minimaltv

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Home + Grade. Medidas copiadas dos modelos do Canva
 * (design/home.png e design/grade.png, 1920x1080) e convertidas com px().
 */
class MainActivity : Activity() {

    // Paleta (claro / escuro). Definida em onCreate conforme o tema salvo.
    private var dark = false
    private var BG = 0        // fundo do site
    private var CARD = 0      // fundo de campos e cards sem cor própria
    private var TEXT = 0      // título
    private var SUPPORT = 0   // texto de apoio
    private var ACCENT = 0    // destaque azul

    private fun applyPalette() {
        fun c(h: String) = Color.parseColor(h)
        BG = c(if (dark) "#1C1C1E" else "#F5F5F7")
        CARD = c(if (dark) "#3A3A3C" else "#E5E5EA")
        TEXT = c(if (dark) "#F5F5F7" else "#1D1D1F")
        SUPPORT = c(if (dark) "#98989D" else "#86868B")
        ACCENT = c(if (dark) "#0A84FF" else "#007AFF")
    }

    /** Cor do card = cor do ícone, mas nunca igual ao fundo (ex.: card branco no fundo claro). */
    private fun cardColor(raw: Int): Int {
        val lum = (0.299 * Color.red(raw) + 0.587 * Color.green(raw) + 0.114 * Color.blue(raw)) / 255
        return if (!dark && lum > 0.90) CARD else if (dark && lum < 0.16) CARD else raw
    }

    // Medidas do modelo (px em 1920x1080)
    private val MARGIN = 108
    private val CARD_W = 256
    private val CARD_H = 256
    private val CARD_H_ON = 342      // card focado cresce pra cima
    private val GAP = 31
    private val CARD_RADIUS = 48
    private val COLS = 6
    private val ICON_SIZE = 124
    private val LABEL = 44f

    private var scale = 1f
    private fun px(v: Number) = (v.toFloat() * scale).roundToInt()

    private lateinit var root: FrameLayout
    private lateinit var home: FrameLayout
    private lateinit var grid: FrameLayout

    private lateinit var date: TextView
    private lateinit var clock: TextView
    private lateinit var weather: TextView
    private lateinit var gridDate: TextView
    private lateinit var gridClock: TextView
    private lateinit var gridWeather: TextView

    private lateinit var homeScroller: HorizontalScrollView
    private lateinit var homeRow: FrameLayout       // cards + nome
    private lateinit var gridScroller: ScrollView
    private lateinit var gridList: LinearLayout

    private var favorites = mutableListOf<String>()
    private var homeFocus = 0
    private var gridFocusPkg: String? = null

    private val locale = Locale("pt", "BR")
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            val hm = SimpleDateFormat("HH:mm", locale).format(now)
            clock.text = hm
            gridClock.text = hm
            val day = SimpleDateFormat("EEE", locale).format(now).replace(".", "").uppercase(locale)
            val month = SimpleDateFormat("MMM", locale).format(now).replace(".", "").uppercase(locale)
            date.text = "$day, ${SimpleDateFormat("d", locale).format(now)} de $month"
            gridDate.text = date.text
            clock.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        scale = max(dm.widthPixels, dm.heightPixels) / 1920f

        dark = getSharedPreferences("minimaltv", MODE_PRIVATE).getBoolean("dark", false)
        applyPalette()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(BG))

        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        home = layer()
        grid = layer().apply { visibility = View.GONE }

        buildHome()
        buildGrid()
    }

    override fun onResume() {
        super.onResume()
        tick.run()
        favorites = Favorites.load(this)
        fillHome()
        if (grid.visibility == View.VISIBLE) fillGrid()
    }

    override fun onPause() {
        super.onPause()
        clock.removeCallbacks(tick)
    }

    // Apertar Home já estando aqui: volta pro primeiro favorito.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showHome(first = true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Na grade, Voltar retorna pra Home. Na Home, não faz nada.
        if (grid.visibility == View.VISIBLE) showHome()
    }

    // ================= HOME =================

    private fun buildHome() {
        date = text(30f, support = true).also { place(home, it, left = MARGIN, top = 106) }
        weather = text(30f, support = true).also { place(home, it, top = 106, right = MARGIN, gravity = Gravity.END) }
        clock = text(145f, light = true).also { place(home, it, top = 69, gravity = Gravity.CENTER_HORIZONTAL) }

        circles(home, top = 246, gravity = Gravity.CENTER_HORIZONTAL)

        homeScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
        }
        homeRow = FrameLayout(this).apply { clipChildren = false }
        homeScroller.addView(homeRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H_ON)))
        home.addView(homeScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H_ON)).apply {
            topMargin = px(630); leftMargin = px(MARGIN); rightMargin = px(MARGIN)
        })
    }

    private fun fillHome() {
        homeRow.removeAllViews()
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        homeRow.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H_ON)))
        val label = text(LABEL).apply { visibility = View.INVISIBLE }
        homeRow.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(2)
        })

        favorites.forEachIndexed { i, pkg ->
            cards.addView(card(Apps.label(this, pkg), pkg, label, homeScroller,
                onFocus = { homeFocus = i },
                onClick = { open(pkg) }))
        }
        if (favorites.size < Favorites.MAX) {
            cards.addView(card("Adicionar", null, label, homeScroller,
                onFocus = { homeFocus = favorites.size },
                onClick = { showGrid(hint = true) }))
        }
        if (home.visibility == View.VISIBLE) cards.getChildAt(homeFocus.coerceIn(0, cards.childCount - 1))?.requestFocus()
    }

    private fun showHome(first: Boolean = false) {
        grid.visibility = View.GONE
        home.visibility = View.VISIBLE
        if (first) { homeFocus = 0; homeScroller.scrollTo(0, 0) }
        fillHome()
    }

    // ================= GRADE =================

    private fun buildGrid() {
        gridDate = text(30f, support = true).also { place(grid, it, left = MARGIN, top = 106) }
        gridClock = text(30f).also { place(grid, it, top = 106, gravity = Gravity.CENTER_HORIZONTAL) }
        gridWeather = text(30f, support = true).also { place(grid, it, top = 106, right = MARGIN, gravity = Gravity.END) }
        circles(grid, top = 165, gravity = Gravity.CENTER_HORIZONTAL)

        gridScroller = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false   // corta no topo (não vaza sobre os círculos), deixa respiro embaixo
            setPadding(0, 0, 0, px(60))
        }
        gridList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        gridScroller.addView(gridList)
        grid.addView(gridScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = px(254); leftMargin = px(MARGIN); rightMargin = px(MARGIN)
        })
    }

    private fun fillGrid() {
        gridList.removeAllViews()
        val apps = Apps.launchable(this)
        var toFocus: View? = null
        apps.chunked(COLS).forEachIndexed { r, chunk ->
            val row = FrameLayout(this).apply { clipChildren = false }
            val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
            row.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H_ON)))
            val label = text(LABEL).apply { visibility = View.INVISIBLE }
            row.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(2)
            })
            chunk.forEach { app ->
                val c = card(app.label, app.pkg, label, null,
                    onFocus = { gridFocusPkg = app.pkg },
                    onClick = { open(app.pkg) },
                    onLong = { toggleFavorite(app) })
                cards.addView(c)
                if (app.pkg == gridFocusPkg) toFocus = c
            }
            gridList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H_ON)).apply {
                if (r > 0) topMargin = px(GAP)
            })
        }
        (toFocus ?: firstCard())?.requestFocus()
    }

    private fun firstCard(): View? =
        ((gridList.getChildAt(0) as? FrameLayout)?.getChildAt(0) as? LinearLayout)?.getChildAt(0)

    private fun showGrid(hint: Boolean = false) {
        home.visibility = View.GONE
        grid.visibility = View.VISIBLE
        gridFocusPkg = null
        gridScroller.scrollTo(0, 0)
        fillGrid()
        if (hint) toast("Segure OK num app para favoritar")
    }

    private fun toggleFavorite(app: App) {
        if (app.pkg in favorites) {
            favorites.remove(app.pkg); toast("${app.label} saiu dos favoritos")
        } else if (favorites.size >= Favorites.MAX) {
            toast("Limite de ${Favorites.MAX} favoritos"); return
        } else {
            favorites.add(app.pkg); toast("${app.label} nos favoritos")
        }
        Favorites.save(this, favorites)
        gridFocusPkg = app.pkg
        val y = gridScroller.scrollY
        fillGrid()
        gridScroller.post { gridScroller.scrollTo(0, y) }
    }

    // ================= CÍRCULOS =================

    private fun circles(parent: FrameLayout, top: Int, right: Int = 0, gravity: Int) {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        place(parent, bar, top = top, right = right, gravity = gravity)
        val items = listOf(
            R.drawable.ic_grid to "Home / Todos os apps",
            R.drawable.ic_theme to "Tema",
            R.drawable.ic_boost to "Acelerador",
            R.drawable.ic_hdmi to "HDMI",
            R.drawable.ic_settings to "Configurações",
        )
        items.forEachIndexed { i, (icon, name) ->
            val c = FrameLayout(this).apply {
                isFocusable = true
                background = round(CARD, px(40).toFloat())
                contentDescription = name
                addView(ImageView(context).apply { setImageResource(icon); setColorFilter(TEXT) },
                    FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER))
                setOnFocusChangeListener { v, has ->
                    (v.background as GradientDrawable).setColor(if (has) ACCENT else CARD)
                    ((v as FrameLayout).getChildAt(0) as ImageView).setColorFilter(if (has) Color.WHITE else TEXT)
                    val s = if (has) 1.12f else 1f
                    v.animate().scaleX(s).scaleY(s).setDuration(120).start()
                }
                setOnClickListener { onCircle(i) }
                setOnLongClickListener { onCircleLong(i) }
            }
            bar.addView(c, LinearLayout.LayoutParams(px(80), px(80)).apply { if (i > 0) leftMargin = px(30) })
        }
    }

    private fun onCircle(i: Int) {
        when (i) {
            0 -> if (grid.visibility == View.VISIBLE) showHome() else showGrid()
            1 -> {
                getSharedPreferences("minimaltv", MODE_PRIVATE).edit().putBoolean("dark", !dark).apply()
                recreate()
            }
            2 -> boost()
            3 -> toast("HDMI: em breve")
            4 -> openSettings(Settings.ACTION_SETTINGS)
        }
    }

    private fun onCircleLong(i: Int): Boolean {
        if (i == 2) { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS); return true }
        return false
    }

    /** Fecha apps parados em segundo plano pra liberar RAM. */
    private fun boost() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        Apps.launchable(this).forEach { am.killBackgroundProcesses(it.pkg) }
        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val freed = max(0L, after - before) / (1024 * 1024)
        toast(if (freed > 0) "Memória liberada: $freed MB" else "Memória já estava livre")
    }

    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { toast("Não deu pra abrir") }
        }
    }

    private fun open(pkg: String) {
        if (!Apps.open(this, pkg)) toast("Não deu pra abrir")
    }

    // ================= CARD =================

    /**
     * Card de app. Focado: cresce de 308 pra 410 e mostra o nome ao lado
     * (à direita; à esquerda se não couber na tela).
     */
    private fun card(
        name: String, pkg: String?, label: TextView, hScroll: HorizontalScrollView?,
        onFocus: () -> Unit, onClick: () -> Unit, onLong: (() -> Unit)? = null,
    ): View {
        val isFav = pkg != null && onLong != null && pkg in favorites
        val c = FrameLayout(this).apply {
            isFocusable = true
            contentDescription = name
            val icon = pkg?.let { Apps.icon(context, it) }
            // Card pintado com a cor que mais aparece no ícone
            background = round(icon?.let { cardColor(Apps.color(pkg, it)) } ?: CARD, px(CARD_RADIUS).toFloat())
            val img = ImageView(context).apply {
                if (pkg != null) setImageDrawable(icon)
                else { setImageResource(R.drawable.ic_plus); setColorFilter(SUPPORT) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val s = if (pkg != null) ICON_SIZE else 104
            addView(img, FrameLayout.LayoutParams(px(s), px(s), Gravity.CENTER))
            if (isFav) {
                // Bolinha = é favorito
                addView(View(context).apply {
                    background = round(ACCENT, px(8).toFloat()).apply { setStroke(px(2), Color.WHITE) }
                }, FrameLayout.LayoutParams(px(16), px(16), Gravity.TOP or Gravity.END).apply {
                        topMargin = px(22); rightMargin = px(22)
                    })
            }
            setOnFocusChangeListener { v, has ->
                setHeight(v, if (has) CARD_H_ON else CARD_H)
                if (has) {
                    onFocus()
                    v.post { showLabel(label, name, v, hScroll) }
                } else if (label.tag === v) {
                    label.visibility = View.INVISIBLE
                }
            }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        c.layoutParams = LinearLayout.LayoutParams(px(CARD_W), px(CARD_H)).apply { rightMargin = px(GAP) }
        return c
    }

    private fun setHeight(v: View, h: Int) {
        val lp = v.layoutParams
        lp.height = px(h)
        v.layoutParams = lp
    }

    private fun showLabel(label: TextView, name: String, card: View, hScroll: HorizontalScrollView?) {
        label.text = name
        label.tag = card
        val w = label.paint.measureText(name)
        val visibleRight = (hScroll?.scrollX ?: 0) + px(1920 - 2 * MARGIN)
        var x = card.left + px(CARD_W) + px(GAP).toFloat()
        if (x + w > visibleRight) x = card.left - px(GAP) - w
        label.translationX = x
        label.visibility = View.VISIBLE
    }

    // ================= AJUDANTES =================

    private fun layer() = FrameLayout(this).apply { clipChildren = false }.also {
        root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun text(size1080: Float, light: Boolean = false, support: Boolean = false) = TextView(this).apply {
        setTextColor(if (support) SUPPORT else TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size1080 * scale)
        includeFontPadding = false
        maxLines = 1
        typeface = resources.getFont(if (light) R.font.inter_light else R.font.inter_regular)
    }

    private fun place(parent: FrameLayout, v: View, left: Int = 0, top: Int = 0, right: Int = 0, gravity: Int = Gravity.START) {
        parent.addView(v, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity or Gravity.TOP
        ).apply { leftMargin = px(left); topMargin = px(top); rightMargin = px(right) })
    }

    private fun round(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
