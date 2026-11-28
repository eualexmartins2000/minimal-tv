package com.alex.minimaltv

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PorterDuff
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
 * Home + Grade, visual "Vidro" (design/v3, prototype/launcher.html).
 * Medidas na escala 1920x1080, convertidas com px(). Sem sombras, sem desfoque ao vivo (TV de 0,9 GB).
 */
class MainActivity : Activity() {

    // Paleta (claro / escuro). Definida em onCreate conforme o tema salvo.
    private var dark = false
    private var BASE = 0       // fundo liso por baixo das manchas
    private var CARD = 0       // menus e painéis (opaco, pra ler bem)
    private var TEXT = 0
    private var SUPPORT = 0
    private var ACCENT = 0
    private var GL1 = 0; private var GL2 = 0; private var GL3 = 0   // degradê do vidro
    private var EDGE = 0       // borda fina do vidro
    private var SLIT = 0       // fresta das placas do relógio
    private var auraStrength = 0.5f

    private fun applyPalette() {
        fun c(h: String) = Color.parseColor(h)
        fun w(a: Float) = Color.argb((a * 255).roundToInt(), 255, 255, 255)
        BASE = c(if (dark) "#06070B" else "#E9ECF4")
        CARD = c(if (dark) "#2C2C2E" else "#FFFFFF")
        TEXT = c(if (dark) "#F5F5F7" else "#10213D")
        SUPPORT = c(if (dark) "#C4C9E6" else "#34476B")
        ACCENT = c(if (dark) "#0A84FF" else "#007AFF")
        if (dark) { GL1 = w(.20f); GL2 = w(.04f); GL3 = w(.12f); EDGE = w(.26f); SLIT = w(.30f); auraStrength = .5f }
        else { GL1 = w(.46f); GL2 = w(.12f); GL3 = w(.26f); EDGE = w(.55f); SLIT = w(.55f); auraStrength = .55f }
    }

    // Medidas do modelo (design/v3, px em 1920x1080)
    private val MARGIN = 108
    private val CARD_W = 256
    private val CARD_H = 256
    private val CARD_W_ON = 542      // card focado cresce pro lado: 2 cards + 1 espaço
    private val GAP = 30
    private val CARD_RADIUS = 48
    private val COLS = 4             // grade: 4 colunas, fileiras alinhadas à direita (x = 1812)
    private val ICON_SIZE = 124
    private val LABEL = 40f
    private val BTN = 76             // botões do sistema
    private val BTN_RADIUS = 24
    private val HOME_ROW_TOP = 641   // rótulo da faixa; o card fica 75 abaixo (y = 716)
    private val HOME_CARD_TOP = 75
    private val HOME_ROW_H = 439
    private val GRID_FIRST = 110     // rótulo da 1ª fileira da grade (card em y = 186)
    private val GRID_CARD_TOP = 76
    private val ROW_PITCH = 366
    private val LIFT = 10            // card focado sobe 10

    private var scale = 1f
    private fun px(v: Number) = (v.toFloat() * scale).roundToInt()

    private lateinit var root: FrameLayout
    private lateinit var aura: AuraView
    private lateinit var home: FrameLayout
    private lateinit var grid: FrameLayout

    private lateinit var date: TextView

    // Relógio de placas (flip)
    private val flipTiles = mutableListOf<FrameLayout>()
    private val flipDigits = CharArray(4) { ' ' }

    private lateinit var homeScroller: HorizontalScrollView
    private lateinit var homeRow: FrameLayout       // cards + nome
    private lateinit var gridScroller: ScrollView
    private lateinit var gridList: LinearLayout

    // Botões do sistema: ordem salva (ids), bloco 2x3 na Home, coluna na Grade
    private val circleItems = listOf(
        Triple(0, R.drawable.ic_grid, "Home / Todos os apps"),
        Triple(1, R.drawable.ic_theme, "Tema"),
        Triple(2, R.drawable.ic_boost, "Acelerador"),
        Triple(3, R.drawable.ic_hdmi, "HDMI"),
        Triple(4, R.drawable.ic_settings, "Configurações"),
        Triple(5, R.drawable.ic_bluetooth, "Bluetooth"),
    )
    private var circleOrder = mutableListOf(0, 1, 2, 3, 5, 4)
    private lateinit var homeBar: FrameLayout
    private lateinit var gridBar: FrameLayout

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
    private var swapId = 0

    private val locale = Locale("pt", "BR")
    private fun cap(t: String) = t.replaceFirstChar { it.uppercase(locale) }
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            setFlip(SimpleDateFormat("HHmm", locale).format(now))
            val wd = cap(SimpleDateFormat("EEEE", locale).format(now))
            val mo = cap(SimpleDateFormat("MMMM", locale).format(now))
            date.text = "$wd, ${SimpleDateFormat("d", locale).format(now)} de $mo de ${SimpleDateFormat("yyyy", locale).format(now)} em ${Weather.cached(this@MainActivity)}"
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
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(BASE))

        root = FrameLayout(this).apply { setBackgroundColor(BASE) }
        setContentView(root)
        aura = AuraView(this, auraStrength)
        root.addView(aura, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setAura(Color.parseColor("#6C4CF1"), Color.parseColor("#2C1A8C"), false)
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

    /** Manchas do fundo: duas cores do app focado + o azul de destaque. */
    private fun setAura(a: Int, b: Int, animate: Boolean) = aura.setColors(a, b, ACCENT, animate)

    override fun onResume() {
        super.onResume()
        tick.run()
        Weather.refresh(this) { tick.run() }
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
                KeyEvent.KEYCODE_DPAD_UP -> { if (down && moveKind == MOVE_CIRCLE) moveBy(-2); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { if (down && moveKind == MOVE_CIRCLE) moveBy(2); return true }
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
        // Data + clima (uma linha), relógio de placas e bloco 2x3 de botões (design/v3)
        date = text(28f, support = true).also { place(home, it, top = 106, gravity = Gravity.CENTER_HORIZONTAL) }
        buildFlipClock().also { place(home, it, left = 414, top = 170) }
        homeBar = FrameLayout(this).apply { clipChildren = false }
        home.addView(homeBar, FrameLayout.LayoutParams(px(2 * BTN + 28 + 16), px(3 * BTN + 2 * 29 + 16)).apply {
            leftMargin = px(1328 - 8); topMargin = px(170 - 8)
        })
        fillCircles(homeBar, side = false)

        homeScroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
        }
        homeRow = FrameLayout(this).apply { clipChildren = false }
        homeScroller.addView(homeRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(HOME_ROW_H)))
        home.addView(homeScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(HOME_ROW_H)).apply {
            topMargin = px(HOME_ROW_TOP); leftMargin = px(MARGIN); rightMargin = px(MARGIN)
        })
    }

    private fun fillHome() {
        homeRow.removeAllViews()
        val cards = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false }
        homeRow.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H)).apply {
            topMargin = px(HOME_CARD_TOP)
        })
        val label = text(LABEL).apply { visibility = View.INVISIBLE }
        homeRow.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

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
                background = glass(px(30).toFloat())
                cameraDistance = px(900).toFloat() * 3
                addView(text(250f).apply { gravity = Gravity.CENTER; letterSpacing = -0.045f },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // fresta no meio, como numa placa de verdade
                addView(View(context).apply { setBackgroundColor(SLIT) },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(4), Gravity.CENTER_VERTICAL))
            }
            flipTiles.add(tile)
            box.addView(tile, LinearLayout.LayoutParams(px(206), px(286)).apply {
                leftMargin = when (i) { 0 -> 0; 2 -> px(30); else -> px(14) }
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
            tile.animate().rotationX(-90f).setDuration(150).setInterpolator(null).withEndAction {
                tv.text = d.toString()
                tile.rotationX = 90f
                tile.animate().rotationX(0f).setDuration(Motion.DUR).setInterpolator(Motion.spring).start()
            }.start()
        }
    }

    private fun showHome(first: Boolean = false) {
        if (first) { homeFocus = 0; homeScroller.scrollTo(0, 0) }
        swap(show = home, hide = grid, grow = false)
        fillHome()
    }

    // ================= GRADE =================

    private fun buildGrid() {
        // Coluna de botões fixa na esquerda (x = 108); a grade rola ao lado.
        gridBar = FrameLayout(this).apply { clipChildren = false }

        gridScroller = ScrollView(this).apply { isVerticalScrollBarEnabled = false }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        gridList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; clipChildren = false }
        content.addView(gridList, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        gridScroller.addView(content)
        grid.addView(gridScroller, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        grid.addView(gridBar, FrameLayout.LayoutParams(px(BTN + 16), px(240 + 6 * 105.2f)).apply {
            leftMargin = px(MARGIN - 8)
        })
        fillCircles(gridBar, side = true)
    }

    /** 4 por fileira, cada fileira alinhada à direita (design/v3). */
    private fun fillGrid() {
        gridList.removeAllViews()
        gridList.setPadding(0, px(GRID_FIRST), 0, 0)
        val apps = Apps.launchable(this)
        var toFocus: View? = null
        apps.chunked(COLS).forEach { chunk ->
            val row = FrameLayout(this).apply { clipChildren = false }
            val cards = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                clipChildren = false
            }
            row.addView(cards, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H)).apply {
                topMargin = px(GRID_CARD_TOP); rightMargin = px(MARGIN)
            })
            val label = text(LABEL).apply { visibility = View.INVISIBLE }
            row.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            chunk.forEachIndexed { j, app ->
                val c = card(app.label, app.pkg, label, null,
                    onFocus = { gridFocusPkg = app.pkg },
                    onClick = { open(app.pkg) },
                    onLong = { v -> gridMenu(v, app) },
                    showDot = true, inGrid = true, first = j == 0)
                cards.addView(c)
                if (app.pkg == gridFocusPkg) toFocus = c
            }
            gridList.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(ROW_PITCH)))
        }
        gridList.addView(View(this), LinearLayout.LayoutParams(1, px(100)))   // respiro no fim
        (toFocus ?: firstCard())?.requestFocus()
    }

    private fun firstCard(): View? =
        ((gridList.getChildAt(0) as? FrameLayout)?.getChildAt(0) as? LinearLayout)?.getChildAt(0)

    private fun showGrid() {
        gridFocusPkg = null
        gridScroller.scrollTo(0, 0)
        swap(show = grid, hide = home, grow = true)
        fillGrid()
    }

    /** Troca de tela com mola: a que sai some, a que entra assenta (sem desfoque, que pesa na TV). */
    private fun swap(show: FrameLayout, hide: FrameLayout, grow: Boolean) {
        if (show.visibility == View.VISIBLE && hide.visibility != View.VISIBLE) return
        val id = ++swapId
        hide.animate().cancel(); show.animate().cancel()
        show.visibility = View.VISIBLE
        show.alpha = 0f
        show.scaleX = if (grow) 1.06f else .94f; show.scaleY = show.scaleX
        show.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.DUR).setInterpolator(Motion.spring).start()
        hide.animate().alpha(0f).scaleX(if (grow) .94f else 1.06f).scaleY(if (grow) .94f else 1.06f)
            .setDuration(250).setInterpolator(null).withEndAction {
                if (id == swapId) { hide.visibility = View.GONE; hide.alpha = 1f; hide.scaleX = 1f; hide.scaleY = 1f }
            }.start()
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

    // ================= BOTÕES DO SISTEMA =================

    private fun loadCircleOrder() {
        val saved = getSharedPreferences("minimaltv", MODE_PRIVATE).getString("circles", null)
        val ids = saved?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableList()
        if (ids != null && ids.sorted() == listOf(0, 1, 2, 3, 4)) {
            // Ordem antiga (5 botões): Bluetooth entra antes de Configurações
            ids.add(ids.indexOf(4).coerceAtLeast(0), 5)
        }
        if (ids != null && ids.sorted() == listOf(0, 1, 2, 3, 4, 5)) circleOrder = ids
    }

    private fun saveCircleOrder() {
        getSharedPreferences("minimaltv", MODE_PRIVATE).edit()
            .putString("circles", circleOrder.joinToString(",")).apply()
    }

    /** Preenche o bloco 2x3 (Home) ou a coluna (Grade). Posições absolutas, com 8 de folga pro card crescer. */
    private fun fillCircles(bar: FrameLayout, side: Boolean) {
        bar.removeAllViews()
        circleOrder.forEachIndexed { pos, id ->
            val (_, icon, name) = circleItems.first { it.first == id }
            val moving = moveKind == MOVE_CIRCLE && id == movingCircle
            val r = px(BTN_RADIUS).toFloat()
            val c = FrameLayout(this).apply {
                tag = id
                isFocusable = true
                background = glass(r).apply { if (moving) setStroke(px(3), ACCENT) }
                contentDescription = name
                addView(ImageView(context).apply { setImageResource(icon); setColorFilter(TEXT, PorterDuff.Mode.SRC_IN) },
                    FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER))
                setOnFocusChangeListener { v, has ->
                    v.background = if (has) round(ACCENT, r) else glass(r)
                    ((v as FrameLayout).getChildAt(0) as ImageView).setColorFilter(if (has) Color.WHITE else TEXT, PorterDuff.Mode.SRC_IN)
                    val sc = if (has) 1.16f else 1f
                    v.animate().scaleX(sc).scaleY(sc).setDuration(Motion.DUR).setInterpolator(Motion.spring).start()
                }
                setOnClickListener { v ->
                    val sc = if (v.hasFocus()) 1.16f else 1f
                    v.animate().scaleX(.88f).scaleY(.88f).setDuration(90).setInterpolator(null).withEndAction {
                        v.animate().scaleX(sc).scaleY(sc).setDuration(Motion.DUR).setInterpolator(Motion.spring).start()
                    }.start()
                    onCircle(id, v)
                }
                setOnLongClickListener { circleMenu(this, id); true }
            }
            val lp = FrameLayout.LayoutParams(px(BTN), px(BTN))
            if (side) {
                lp.leftMargin = px(8); lp.topMargin = px(240 + pos * 105.2f)
            } else {
                lp.leftMargin = px(8 + (pos % 2) * (BTN + 28)); lp.topMargin = px(8 + (pos / 2) * (BTN + 29))
            }
            bar.addView(c, lp)
        }
    }

    /** Recria os dois conjuntos (Home e Grade) e devolve o foco pro botão [focusId]. */
    private fun refreshCircles(focusId: Int?) {
        fillCircles(homeBar, side = false)
        fillCircles(gridBar, side = true)
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
            5 -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
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
            background = round(CARD, px(36).toFloat()).apply { setStroke(px(2), EDGE) }
            setPadding(pad, pad, pad, pad)
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
     * Card de app. Quando focado, sobe 10 e cresce pro lado (256 -> 542) com mola, e mostra o banner
     * (logo da marca, banner do app, ou o ícone se não tiver nada). O nome aparece em cima, à esquerda.
     * Apps de marca (Brands) usam a cor e o logo próprios; os outros, a cor que mais aparece no ícone.
     * Cards sem app ("+" e "Todos os apps") são placas de vidro. [inGrid]: fileiras à direita.
     */
    private fun card(
        name: String, pkg: String?, label: TextView, hScroll: HorizontalScrollView?,
        onFocus: () -> Unit, onClick: () -> Unit, onLong: ((View) -> Unit)? = null,
        showDot: Boolean = false, moving: Boolean = false, inGrid: Boolean = false, first: Boolean = false,
        glyph: Int = R.drawable.ic_plus,
    ): View {
        val isFav = showDot && pkg != null && pkg in favorites
        val util = pkg == null
        val brand = pkg?.let { Brands.find(it, name) }
        val icon = pkg?.let { Apps.icon(this, it) }
        val c1 = brand?.c1 ?: (if (pkg != null && icon != null) Apps.color(pkg, icon) else CARD)
        val c2 = brand?.c2 ?: darken(c1)
        val r = px(CARD_RADIUS).toFloat()

        val c = FrameLayout(this).apply {
            isFocusable = true
            contentDescription = name
            clipChildren = false
            background = (if (util) glass(r) else round(c1, r)).apply { if (moving) setStroke(px(5), ACCENT) }

            // Banner (card aberto): degradê da cor do app (ou vidro, nos cards sem app)
            val banner = FrameLayout(context).apply {
                alpha = 0f
                visibility = View.INVISIBLE
                if (!util) background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2)).apply { cornerRadius = r }
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, r)
                    }
                }
            }
            addView(banner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            var bannerImg: ImageView? = null
            if (util) {
                val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                row.addView(ImageView(context).apply { setImageResource(glyph); setColorFilter(TEXT, PorterDuff.Mode.SRC_IN); alpha = .7f },
                    LinearLayout.LayoutParams(px(96), px(96)))
                row.addView(text(46f).apply { text = name }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = px(26) })
                banner.addView(row, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = px(38) })
            } else if (brand != null) {
                val d = this@MainActivity.getDrawable(brand.bannerLogo)!!.mutate()
                val h = px(brand.bannerH)
                val w = (h * d.intrinsicWidth.toFloat() / d.intrinsicHeight).roundToInt()
                banner.addView(ImageView(context).apply { setImageDrawable(d); setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) },
                    FrameLayout.LayoutParams(w, h, Gravity.BOTTOM or Gravity.START).apply { leftMargin = px(38); bottomMargin = px(30) })
            } else {
                bannerImg = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                banner.addView(bannerImg, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }

            // Ícone/logo do card fechado
            val img = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                when {
                    util -> { setImageResource(glyph); setColorFilter(TEXT, PorterDuff.Mode.SRC_IN); alpha = .7f }
                    brand != null -> {
                        val d = this@MainActivity.getDrawable(brand.cardLogo)!!.mutate()
                        setImageDrawable(d); setColorFilter(brand.ink, PorterDuff.Mode.SRC_IN)
                    }
                    else -> setImageDrawable(icon)
                }
            }
            val lp = when {
                util -> FrameLayout.LayoutParams(px(108), px(108), Gravity.CENTER)
                brand != null -> {
                    val d = img.drawable
                    val ratio = d.intrinsicHeight.toFloat() / d.intrinsicWidth
                    FrameLayout.LayoutParams(px(brand.cardW), min(px(brand.cardW * ratio), px(116)), Gravity.CENTER)
                }
                else -> FrameLayout.LayoutParams(px(ICON_SIZE), px(ICON_SIZE), Gravity.CENTER)
            }
            addView(img, lp)
            if (isFav) {
                // Bolinha azul = é favorito
                addView(View(context).apply {
                    background = round(ACCENT, px(8).toFloat()).apply { setStroke(px(2), Color.WHITE) }
                }, FrameLayout.LayoutParams(px(16), px(16), Gravity.TOP or Gravity.END).apply {
                    topMargin = px(22); rightMargin = px(22)
                })
            }

            setOnFocusChangeListener { v, has ->
                animateWidth(v, if (has) CARD_W_ON else CARD_W, label)
                v.animate().translationY(if (has) -px(LIFT).toFloat() else 0f).setDuration(Motion.DUR).setInterpolator(Motion.spring).start()
                var showBanner = util || brand != null
                if (pkg != null && brand == null) {
                    val b = if (has) Apps.banner(context, pkg) else null
                    bannerImg?.setImageDrawable(b)
                    showBanner = b != null
                }
                if (has) {
                    banner.visibility = if (showBanner) View.VISIBLE else View.INVISIBLE
                    banner.animate().alpha(if (showBanner) 1f else 0f).setDuration(220).start()
                    img.animate().alpha(if (showBanner) 0f else (if (util) .7f else 1f)).setDuration(180).start()
                    if (brand != null || pkg != null) setAura(c1, c2, true)
                    onFocus()
                    v.post { showLabel(label, name, v); keepVisible(hScroll, v) }
                } else {
                    banner.animate().alpha(0f).setDuration(180).start()
                    img.animate().alpha(if (util) .7f else 1f).setDuration(180).start()
                    if (label.tag === v) label.visibility = View.INVISIBLE
                }
            }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(this); true }
        }
        c.layoutParams = LinearLayout.LayoutParams(px(CARD_W), px(CARD_H)).apply {
            if (inGrid) { if (!first) leftMargin = px(GAP) } else rightMargin = px(GAP)
        }
        return c
    }

    /** Largura do card com mola; o nome em cima acompanha a borda esquerda dele. */
    private fun animateWidth(v: View, target: Int, label: TextView) {
        (v.tag as? ValueAnimator)?.cancel()
        val from = v.layoutParams.width
        val to = px(target)
        if (from == to) return
        val a = ValueAnimator.ofInt(from, to).apply {
            duration = Motion.DUR
            interpolator = Motion.spring
            addUpdateListener {
                val lp = v.layoutParams
                lp.width = it.animatedValue as Int
                v.layoutParams = lp
                if (label.tag === v) label.translationX = v.left.toFloat()
            }
        }
        v.tag = a
        a.start()
    }

    private fun darken(c: Int, f: Float = .45f) =
        Color.rgb((Color.red(c) * (1 - f)).toInt(), (Color.green(c) * (1 - f)).toInt(), (Color.blue(c) * (1 - f)).toInt())

    /** Nome do app: em cima do card focado, alinhado à esquerda dele; entra subindo 12 com fade. */
    private fun showLabel(label: TextView, name: String, card: View) {
        val sameRow = label.tag != null && label.visibility == View.VISIBLE
        label.text = name
        label.tag = card
        label.translationX = card.left.toFloat()
        if (!sameRow) {
            label.visibility = View.VISIBLE
            label.alpha = 0f; label.translationY = px(12).toFloat()
            label.animate().alpha(1f).translationY(0f).setDuration(320).start()
        } else {
            label.animate().cancel(); label.alpha = 1f; label.translationY = 0f
        }
    }

    /** Faixa da Home: rola pra manter o card focado (já com a largura nova) inteiro na tela. */
    private fun keepVisible(h: HorizontalScrollView?, v: View) {
        h ?: return
        val s = h.scrollX; val vw = h.width
        val target = px(CARD_W_ON)
        val right = v.left + max(v.width, target)
        when {
            right > s + vw -> h.smoothScrollTo(right - vw + px(GAP), 0)
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

    /** Placa de vidro: degradê branco translúcido + borda fina (sem sombra e sem desfoque ao vivo). */
    private fun glass(radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(GL1, GL2, GL3)).apply {
            cornerRadius = radius
            setStroke(max(1, px(2)), EDGE)
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
