package com.alex.minimaltv

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
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
        BG = c(if (dark) "#000000" else "#F5F5F7")
        CARD = c(if (dark) "#2C2C2E" else "#FFFFFF")      // fundo dos cards
        TEXT = c(if (dark) "#F5F5F7" else "#1D1D1F")
        SUPPORT = c(if (dark) "#98989D" else "#86868B")
        ACCENT = c(if (dark) "#0A84FF" else "#007AFF")
    }

    /** Cor do card = cor do ícone. Única exceção: card quase preto no fundo preto some, vira cinza. */
    private fun cardColor(raw: Int): Int {
        val lum = (0.299 * Color.red(raw) + 0.587 * Color.green(raw) + 0.114 * Color.blue(raw)) / 255
        return if (dark && lum < 0.12) CARD else raw
    }

    // Medidas do modelo (design/v2/*.png, px em 1920x1080)
    private val MARGIN = 108
    private val CARD_W = 256
    private val CARD_H = 256
    private val CARD_W_ON = 542      // card focado cresce pro lado: 2 cards + 1 espaço
    private val GAP = 30
    private val CARD_RADIUS = 48
    private val COLS = 5
    private val ICON_SIZE = 124
    private val LABEL = 40f
    private val CIRCLE = 76
    private val CIRCLE_GAP = 28
    private val ROW_H = 364          // fileira = rótulo + card + respiro (cards a 108 do topo)
    private val CARD_TOP = 108       // topo do card dentro da fileira
    private val GRID_TOP = 292       // topo do card da 1ª fileira da grade
    private val LABEL_TOP = 30       // topo do rótulo dentro da fileira

    private var scale = 1f
    private fun px(v: Number) = (v.toFloat() * scale).roundToInt()

    private lateinit var root: FrameLayout
    private lateinit var home: FrameLayout
    private lateinit var grid: FrameLayout

    private lateinit var date: TextView
    private lateinit var weather: TextView

    // Relógio de placas (flip)
    private val flipTiles = mutableListOf<FrameLayout>()
    private val flipDigits = CharArray(4) { ' ' }

    private lateinit var homeScroller: HorizontalScrollView
    private lateinit var homeRow: FrameLayout       // cards + nome
    private lateinit var gridScroller: ScrollView
    private lateinit var gridList: LinearLayout

    // Círculos: ordem salva (ids 0..4), barra de cada tela
    private val circleItems = listOf(
        Triple(0, R.drawable.ic_grid, "Home / Todos os apps"),
        Triple(1, R.drawable.ic_theme, "Tema"),
        Triple(2, R.drawable.ic_boost, "Acelerador"),
        Triple(3, R.drawable.ic_hdmi, "HDMI"),
        Triple(4, R.drawable.ic_settings, "Configurações"),
    )
    private var circleOrder = mutableListOf(0, 1, 2, 3, 4)
    private lateinit var homeBar: LinearLayout
    private lateinit var gridBar: LinearLayout

    // Modo mover
    private val MOVE_NONE = 0
    private val MOVE_FAV = 1
    private val MOVE_CIRCLE = 2
    private var moveKind = MOVE_NONE
    private var movingCircle = -1
    private var movingPkg: String? = null
    private var snapFav = listOf<String>()
    private var snapCircles = listOf<Int>()
    private lateinit var moveHint: TextView

    // Menu de segurar OK
    private var menuLayer: FrameLayout? = null
    private var menuAnchor: View? = null
    private var menuBlocked: ViewGroup? = null
    private var menuAfterClose: (() -> Unit)? = null

    private var favorites = mutableListOf<String>()
    private var homeFocus = 0
    private var gridFocusPkg: String? = null

    private val locale = Locale("pt", "BR")
    private fun cap(t: String) = t.replaceFirstChar { it.uppercase(locale) }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            setFlip(SimpleDateFormat("HHmm", locale).format(now))
            val wd = cap(SimpleDateFormat("EEEE", locale).format(now))
            val mo = cap(SimpleDateFormat("MMMM", locale).format(now))
            date.text = "$wd, ${SimpleDateFormat("d", locale).format(now)} de $mo"
            date.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        scale = max(dm.widthPixels, dm.heightPixels) / 1920f

        dark = getSharedPreferences("minimaltv", MODE_PRIVATE).getBoolean("dark", false)
        applyPalette()
        loadCircleOrder()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(BG))

        root = FrameLayout(this).apply { setBackgroundColor(BG) }
        setContentView(root)
        home = layer()
        grid = layer().apply { visibility = View.GONE }

        buildHome()
        buildGrid()

        moveHint = text(28f, support = true).apply {
            text = "◀ ▶ mover     ·     OK soltar     ·     Voltar cancelar"
            visibility = View.GONE
        }
        place(root, moveHint, top = 1000, gravity = Gravity.CENTER_HORIZONTAL)
    }

    override fun onResume() {
        super.onResume()
        tick.run()
        weather.text = Weather.cached(this)
        Weather.refresh(this) { weather.text = Weather.cached(this) }
        favorites = Favorites.load(this)
        fillHome()
        if (grid.visibility == View.VISIBLE) fillGrid()
    }

    override fun onPause() {
        super.onPause()
        date.removeCallbacks(tick)
    }

    // Apertar Home já estando aqui: volta pro primeiro favorito.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showHome(first = true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Menu aberto: fecha. Na grade: volta pra Home. Na Home: não faz nada.
        if (menuLayer != null) closeMenu()
        else if (grid.visibility == View.VISIBLE) showHome()
    }

    // No modo mover: setas movem, OK solta, Voltar cancela. Tudo mais é ignorado.
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (moveKind != MOVE_NONE) {
            val down = e.action == KeyEvent.ACTION_DOWN
            when (e.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) moveBy(-1); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) moveBy(1); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (down && e.repeatCount == 0) endMove(true); return true
                }
                KeyEvent.KEYCODE_BACK -> { if (!down) endMove(false); return true }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // ================= HOME =================

    private fun buildHome() {
        // Relógio de placas + data + clima, centralizados (design/v2/home.png)
        buildFlipClock().also { place(home, it, top = 213, gravity = Gravity.CENTER_HORIZONTAL) }
        date = text(28f, support = true).also { place(home, it, top = 434, gravity = Gravity.CENTER_HORIZONTAL) }
        weather = text(28f, support = true).also { place(home, it, top = 471, gravity = Gravity.CENTER_HORIZONTAL) }

        homeBar = circles(home, top = 108, gravity = Gravity.CENTER_HORIZONTAL)

        homeScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
        }
        homeRow = FrameLayout(this).apply { clipChildren = false }
        homeScroller.addView(homeRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(ROW_H)))
        home.addView(homeScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_H)).apply {
            topMargin = px(972 - ROW_H); leftMargin = px(MARGIN); rightMargin = px(MARGIN)
        })
    }

    private fun fillHome() {
        homeRow.removeAllViews()
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        homeRow.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H)).apply {
            topMargin = px(CARD_TOP)
        })
        val label = text(LABEL).apply { visibility = View.INVISIBLE }
        homeRow.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(LABEL_TOP)
        })

        favorites.forEachIndexed { i, pkg ->
            cards.addView(card(Apps.label(this, pkg), pkg, label, homeScroller,
                onFocus = { homeFocus = i },
                onClick = { open(pkg) },
                onLong = { v -> favMenu(v, i, pkg) },
                moving = moveKind == MOVE_FAV && i == homeFocus))
        }
        // Os dois últimos cards da faixa: "+" (adicionar) e a grade de apps
        var next = favorites.size
        if (favorites.size < Favorites.MAX) {
            var plus: View? = null
            val at = next++
            plus = card("Adicionar", null, label, homeScroller,
                onFocus = { homeFocus = at },
                onClick = { plus?.let { showAddPanel(it) } })
            cards.addView(plus)
        }
        val gridAt = next
        cards.addView(card("Todos os apps", null, label, homeScroller,
            onFocus = { homeFocus = gridAt },
            onClick = { showGrid() }, glyph = R.drawable.ic_grid))
        if (home.visibility == View.VISIBLE) cards.getChildAt(homeFocus.coerceIn(0, cards.childCount - 1))?.requestFocus()
    }

    // ---------- Relógio de placas ----------

    private fun buildFlipClock(): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until 4) {
            val tile = FrameLayout(this).apply {
                background = round(CARD, px(20).toFloat())
                addView(text(150f).apply { gravity = Gravity.CENTER },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // fresta no meio, como numa placa de verdade
                addView(View(context).apply { setBackgroundColor(BG) },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(3), Gravity.CENTER_VERTICAL))
            }
            flipTiles.add(tile)
            box.addView(tile, LinearLayout.LayoutParams(px(140), px(192)).apply {
                leftMargin = when (i) { 0 -> 0; 2 -> px(28); else -> px(6) }
            })
        }
        return box
    }

    /** Troca os dígitos; a placa que muda "vira" (anima só quando o número muda). */
    private fun setFlip(hhmm: String) {
        for (i in 0 until 4) {
            val d = hhmm.getOrNull(i) ?: '0'
            if (flipDigits[i] == d) continue
            val first = flipDigits[i] == ' '
            flipDigits[i] = d
            val tile = flipTiles[i]
            val tv = tile.getChildAt(0) as TextView
            if (first) { tv.text = d.toString(); continue }
            tile.animate().scaleY(0f).setDuration(110).withEndAction {
                tv.text = d.toString()
                tile.animate().scaleY(1f).setDuration(110).start()
            }.start()
        }
    }

    private fun showHome(first: Boolean = false) {
        grid.visibility = View.GONE
        home.visibility = View.VISIBLE
        if (first) { homeFocus = 0; homeScroller.scrollTo(0, 0) }
        fillHome()
    }

    // ================= GRADE =================

    private fun buildGrid() {
        // Cabeçalho (só os círculos) faz parte da página: rola junto e some ao descer.
        val header = FrameLayout(this).apply { clipChildren = false }
        gridBar = circles(header, top = 108, gravity = Gravity.CENTER_HORIZONTAL)

        gridScroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(GRID_TOP - CARD_TOP)))
        gridList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(gridList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        gridScroller.addView(content)
        grid.addView(gridScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /** 5 por fileira, cada fileira centralizada na tela (design/v2/grade.png). */
    private fun fillGrid() {
        gridList.removeAllViews()
        val apps = Apps.launchable(this)
        var toFocus: View? = null
        apps.chunked(COLS).forEach { chunk ->
            val row = FrameLayout(this).apply { clipChildren = false }
            val cards = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            row.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H)).apply {
                topMargin = px(CARD_TOP)
            })
            val label = text(LABEL).apply { visibility = View.INVISIBLE }
            row.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = px(LABEL_TOP)
            })
            chunk.forEach { app ->
                val c = card(app.label, app.pkg, label, null,
                    onFocus = { gridFocusPkg = app.pkg },
                    onClick = { open(app.pkg) },
                    onLong = { v -> gridMenu(v, app) },
                    showDot = true, centered = true)
                cards.addView(c)
                if (app.pkg == gridFocusPkg) toFocus = c
            }
            gridList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_H)))
        }
        gridList.addView(View(this), LinearLayout.LayoutParams(1, px(100)))   // respiro no fim
        (toFocus ?: firstCard())?.requestFocus()
    }

    private fun firstCard(): View? =
        ((gridList.getChildAt(0) as? FrameLayout)?.getChildAt(0) as? LinearLayout)?.getChildAt(0)

    private fun showGrid() {
        home.visibility = View.GONE
        grid.visibility = View.VISIBLE
        gridFocusPkg = null
        gridScroller.scrollTo(0, 0)
        fillGrid()
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

    private fun loadCircleOrder() {
        val saved = getSharedPreferences("minimaltv", MODE_PRIVATE).getString("circles", null)
        val ids = saved?.split(",")?.mapNotNull { it.toIntOrNull() }
        if (ids != null && ids.sorted() == listOf(0, 1, 2, 3, 4)) circleOrder = ids.toMutableList()
    }

    private fun saveCircleOrder() {
        getSharedPreferences("minimaltv", MODE_PRIVATE).edit()
            .putString("circles", circleOrder.joinToString(",")).apply()
    }

    private fun circles(parent: FrameLayout, top: Int, gravity: Int): LinearLayout {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        place(parent, bar, top = top, gravity = gravity)
        fillCircles(bar)
        return bar
    }

    private fun fillCircles(bar: LinearLayout) {
        bar.removeAllViews()
        circleOrder.forEachIndexed { pos, id ->
            val (_, icon, name) = circleItems.first { it.first == id }
            val moving = moveKind == MOVE_CIRCLE && id == movingCircle
            val c = FrameLayout(this).apply {
                tag = id
                isFocusable = true
                background = round(CARD, px(CIRCLE / 2).toFloat()).apply { if (moving) setStroke(px(3), ACCENT) }
                contentDescription = name
                addView(ImageView(context).apply { setImageResource(icon); setColorFilter(TEXT) },
                    FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER))
                setOnFocusChangeListener { v, has ->
                    (v.background as GradientDrawable).setColor(if (has) ACCENT else CARD)
                    ((v as FrameLayout).getChildAt(0) as ImageView).setColorFilter(if (has) Color.WHITE else TEXT)
                    val sc = if (has) 1.12f else 1f
                    v.animate().scaleX(sc).scaleY(sc).setDuration(120).start()
                }
                setOnClickListener { onCircle(id, this) }
                setOnLongClickListener { circleMenu(this, id); true }
            }
            bar.addView(c, LinearLayout.LayoutParams(px(CIRCLE), px(CIRCLE)).apply { if (pos > 0) leftMargin = px(CIRCLE_GAP) })
        }
    }

    /** Recria as duas barras (Home e Grade) e devolve o foco pro círculo [focusId]. */
    private fun refreshCircles(focusId: Int?) {
        fillCircles(homeBar)
        fillCircles(gridBar)
        if (focusId == null) return
        val bar = if (grid.visibility == View.VISIBLE) gridBar else homeBar
        for (i in 0 until bar.childCount) if (bar.getChildAt(i).tag == focusId) bar.getChildAt(i).requestFocus()
    }

    private fun onCircle(id: Int, view: View) {
        when (id) {
            0 -> if (grid.visibility == View.VISIBLE) showHome() else showGrid()
            1 -> {
                getSharedPreferences("minimaltv", MODE_PRIVATE).edit().putBoolean("dark", !dark).apply()
                recreate()
            }
            2 -> boost()
            3 -> hdmiMenu(view)
            4 -> openSettings(Settings.ACTION_SETTINGS)
        }
    }

    // ================= MENU (segurar OK) =================

    private fun favMenu(anchor: View, i: Int, pkg: String) {
        showMenu(anchor, listOf(
            "Mover" to { startMoveFavorite() },
            "Remover dos favoritos" to { removeFavorite(i) },
            "Info do app" to { appInfo(pkg) },
        ))
    }

    private fun circleMenu(anchor: View, id: Int) {
        val items = mutableListOf<Pair<String, () -> Unit>>("Mover" to { startMoveCircle(id) })
        if (id == 2) items.add("Armazenamento" to { openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) })
        showMenu(anchor, items)
    }

    private fun gridMenu(anchor: View, app: App) {
        val fav = app.pkg in favorites
        showMenu(anchor, listOf(
            (if (fav) "Remover dos favoritos" else "Adicionar aos favoritos") to { toggleFavorite(app) },
            "Info do app" to { appInfo(app.pkg) },
        ))
    }

    // ================= BARRA LATERAL "+" =================

    /** Barra pela direita com os apps que ainda não são favoritos (mais novos primeiro). */
    private fun showAddPanel(anchor: View) {
        closeMenu()
        val r = px(40).toFloat()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)   // só os cantos da esquerda
            }
            elevation = px(16).toFloat()
            setPadding(px(24), px(44), px(24), px(24))
        }
        panel.addView(text(30f, support = true).apply { text = "Adicionar aos favoritos" },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = px(16); bottomMargin = px(24)
            })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        panel.addView(scroller, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val layer = FrameLayout(this).apply { isClickable = true }
        layer.addView(panel, FrameLayout.LayoutParams(px(600), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
        menuLayer = layer; menuAnchor = anchor; menuBlocked = home
        // Ao fechar, o foco volta pro "+" (que andou pra frente conforme entram favoritos)
        menuAfterClose = { homeFocus = favorites.size; fillHome() }
        home.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(layer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        fillAddList(list, 0)
    }

    private fun fillAddList(list: LinearLayout, focusIndex: Int) {
        list.removeAllViews()
        val apps = Apps.newestFirst(this, Apps.launchable(this).filter { it.pkg !in favorites })
        if (apps.isEmpty()) { toast("Todos os apps já são favoritos"); closeMenu(); return }
        apps.forEach { app ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                setPadding(px(16), 0, px(16), 0)
                background = round(Color.TRANSPARENT, px(32).toFloat())
                val ic = ImageView(context).apply { setImageDrawable(Apps.icon(context, app.pkg)) }
                addView(ic, LinearLayout.LayoutParams(px(72), px(72)))
                val tv = TextView(context).apply {
                    text = app.label
                    maxLines = 1
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, 34f * scale)
                    setTextColor(TEXT)
                    typeface = resources.getFont(R.font.inter_regular)
                }
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = px(24) })
                setOnFocusChangeListener { v, has ->
                    (v.background as GradientDrawable).setColor(if (has) ACCENT else Color.TRANSPARENT)
                    tv.setTextColor(if (has) Color.WHITE else TEXT)
                }
                setOnClickListener {
                    if (favorites.size >= Favorites.MAX) { toast("Limite de ${Favorites.MAX} favoritos"); return@setOnClickListener }
                    val idx = list.indexOfChild(this)
                    favorites.add(app.pkg)
                    Favorites.save(this@MainActivity, favorites)
                    toast("${app.label} nos favoritos")
                    if (favorites.size >= Favorites.MAX) closeMenu() else fillAddList(list, idx)
                    fillHome()   // a faixa atrás já mostra o app novo
                }
            }
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(104)))
        }
        list.getChildAt(focusIndex.coerceIn(0, list.childCount - 1))?.requestFocus()
    }

    // ================= HDMI =================

    /** Lista as entradas da TV (HDMI 1, 2...) num menu; escolher troca a entrada. */
    private fun hdmiMenu(anchor: View) {
        val inputs = try {
            (getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)
                ?.tvInputList?.filter { it.isPassthroughInput }
        } catch (e: Exception) { null }
        if (inputs.isNullOrEmpty()) { toast("Nenhuma entrada encontrada"); return }
        showMenu(anchor, inputs.map { inp ->
            val name = (try { inp.loadCustomLabel(this)?.toString() } catch (e: Exception) { null })
                ?.takeIf { it.isNotBlank() } ?: inp.loadLabel(this).toString()
            name to { openInput(inp.id) }
        })
    }

    private fun openInput(id: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { toast("Não deu pra abrir a entrada") }
    }

    private fun removeFavorite(i: Int) {
        if (i !in favorites.indices) return
        val name = Apps.label(this, favorites[i])
        favorites.removeAt(i)
        Favorites.save(this, favorites)
        homeFocus = i.coerceAtMost(favorites.size)
        fillHome()
        toast("$name saiu dos favoritos")
    }

    private fun appInfo(pkg: String) {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg")))
        } catch (e: Exception) { toast("Não deu pra abrir") }
    }

    /** Menu pequeno colado no item (em cima; embaixo se não couber). */
    private fun showMenu(anchor: View, items: List<Pair<String, () -> Unit>>) {
        closeMenu()
        val blocked: ViewGroup = if (grid.visibility == View.VISIBLE) grid else home
        val itemH = px(72); val w = px(400); val pad = px(10)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(CARD, px(36).toFloat())
            setPadding(pad, pad, pad, pad)
            elevation = px(14).toFloat()
        }
        items.forEach { (label, action) ->
            val tv = TextView(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 32f * scale)
                setTextColor(TEXT)
                typeface = resources.getFont(R.font.inter_regular)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px(28), 0, px(28), 0)
                isFocusable = true
                background = round(Color.TRANSPARENT, px(28).toFloat())
                setOnFocusChangeListener { v, has ->
                    (v.background as GradientDrawable).setColor(if (has) ACCENT else Color.TRANSPARENT)
                    (v as TextView).setTextColor(if (has) Color.WHITE else TEXT)
                }
                setOnClickListener { closeMenu(); action() }
            }
            panel.addView(tv, LinearLayout.LayoutParams(w, itemH))
        }
        val panelW = w + 2 * pad
        val panelH = items.size * itemH + 2 * pad
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val x = max(px(MARGIN), min(loc[0], root.width - panelW - px(MARGIN)))
        val above = loc[1] - panelH - px(16)
        val y = if (above >= px(20)) above else loc[1] + anchor.height + px(16)

        val layer = FrameLayout(this).apply { isClickable = true }
        layer.addView(panel, FrameLayout.LayoutParams(panelW, panelH).apply { leftMargin = x; topMargin = y })
        menuLayer = layer; menuAnchor = anchor; menuBlocked = blocked
        blocked.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        root.addView(layer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panel.getChildAt(0).requestFocus()
    }

    private fun closeMenu() {
        val layer = menuLayer ?: return
        root.removeView(layer)
        menuBlocked?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        val after = menuAfterClose
        val anchor = menuAnchor
        menuLayer = null; menuAnchor = null; menuBlocked = null; menuAfterClose = null
        if (after != null) after() else anchor?.requestFocus()
    }

    // ================= MODO MOVER =================

    private fun startMoveFavorite() {
        movingPkg = favorites.getOrNull(homeFocus) ?: return
        moveKind = MOVE_FAV
        snapFav = favorites.toList()
        moveHint.visibility = View.VISIBLE
        fillHome()
    }

    private fun startMoveCircle(id: Int) {
        moveKind = MOVE_CIRCLE
        movingCircle = id
        snapCircles = circleOrder.toList()
        moveHint.visibility = View.VISIBLE
        refreshCircles(id)
    }

    private fun moveBy(d: Int) {
        if (moveKind == MOVE_FAV) {
            val i = homeFocus; val j = i + d
            if (i !in favorites.indices || j !in favorites.indices) return
            Collections.swap(favorites, i, j)
            homeFocus = j
            fillHome()
        } else if (moveKind == MOVE_CIRCLE) {
            val i = circleOrder.indexOf(movingCircle); val j = i + d
            if (j !in circleOrder.indices) return
            Collections.swap(circleOrder, i, j)
            refreshCircles(movingCircle)
        }
    }

    private fun endMove(commit: Boolean) {
        val kind = moveKind
        moveKind = MOVE_NONE
        moveHint.visibility = View.GONE
        if (kind == MOVE_FAV) {
            if (commit) Favorites.save(this, favorites)
            else {
                favorites = snapFav.toMutableList()
                homeFocus = favorites.indexOf(movingPkg).coerceAtLeast(0)
            }
            movingPkg = null
            fillHome()
        } else if (kind == MOVE_CIRCLE) {
            val id = movingCircle
            if (commit) saveCircleOrder() else circleOrder = snapCircles.toMutableList()
            movingCircle = -1
            refreshCircles(id)
        }
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
     * Card de app. Quando focado, cresce pro lado (256 -> 542) e mostra o banner do app
     * (ou o ícone, se o app não tiver banner). O nome aparece em cima, alinhado à esquerda.
     * [glyph]: ícone desenhado (cards "+" e grade). [centered]: margem dos dois lados (grade).
     */
    private fun card(
        name: String, pkg: String?, label: TextView, hScroll: HorizontalScrollView?,
        onFocus: () -> Unit, onClick: () -> Unit, onLong: ((View) -> Unit)? = null,
        showDot: Boolean = false, moving: Boolean = false, centered: Boolean = false,
        glyph: Int = R.drawable.ic_plus,
    ): View {
        val isFav = showDot && pkg != null && pkg in favorites
        val c = FrameLayout(this).apply {
            isFocusable = true
            contentDescription = name
            val icon = pkg?.let { Apps.icon(context, it) }
            // Card pintado com a cor que mais aparece no ícone
            background = round(icon?.let { cardColor(Apps.color(pkg, it)) } ?: CARD, px(CARD_RADIUS).toFloat())
                .apply { if (moving) setStroke(px(5), ACCENT) }
            // Banner (só carregado enquanto o card está focado)
            val ban = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, px(CARD_RADIUS).toFloat())
                    }
                }
            }
            addView(ban, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val img = ImageView(context).apply {
                if (pkg != null) setImageDrawable(icon)
                else { setImageResource(glyph); setColorFilter(SUPPORT) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val sz = if (pkg != null) ICON_SIZE else 104
            addView(img, FrameLayout.LayoutParams(px(sz), px(sz), Gravity.CENTER))
            if (isFav) {
                // Bolinha azul = é favorito
                addView(View(context).apply {
                    background = round(ACCENT, px(8).toFloat()).apply { setStroke(px(2), Color.WHITE) }
                }, FrameLayout.LayoutParams(px(16), px(16), Gravity.TOP or Gravity.END).apply {
                    topMargin = px(22); rightMargin = px(22)
                })
            }
            setOnFocusChangeListener { v, has ->
                setWidth(v, if (has) CARD_W_ON else CARD_W)
                if (pkg != null) {
                    val b = if (has) Apps.banner(context, pkg) else null
                    ban.setImageDrawable(b)
                    ban.visibility = if (b != null) View.VISIBLE else View.GONE
                    img.visibility = if (b != null) View.GONE else View.VISIBLE
                }
                if (has) {
                    onFocus()
                    v.post { showLabel(label, name, v); keepVisible(hScroll, v) }
                } else if (label.tag === v) {
                    label.visibility = View.INVISIBLE
                }
            }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(this); true }
        }
        c.layoutParams = LinearLayout.LayoutParams(px(CARD_W), px(CARD_H)).apply {
            if (centered) { leftMargin = px(GAP / 2); rightMargin = px(GAP / 2) } else rightMargin = px(GAP)
        }
        return c
    }

    private fun setWidth(v: View, w: Int) {
        val lp = v.layoutParams
        lp.width = px(w)
        v.layoutParams = lp
    }

    /** Nome do app: em cima do card focado, alinhado à esquerda dele. */
    private fun showLabel(label: TextView, name: String, card: View) {
        label.text = name
        label.tag = card
        label.translationX = card.left.toFloat()
        label.visibility = View.VISIBLE
    }

    /** Faixa da Home: rola pra manter o card focado (já com a largura nova) inteiro na tela. */
    private fun keepVisible(h: HorizontalScrollView?, v: View) {
        h ?: return
        val s = h.scrollX; val vw = h.width
        when {
            v.left + v.width > s + vw -> h.smoothScrollTo(v.left + v.width - vw + px(GAP), 0)
            v.left < s -> h.smoothScrollTo(max(0, v.left - px(GAP)), 0)
        }
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
