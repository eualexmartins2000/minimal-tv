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
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Home. Medidas copiadas do modelo do Canva (design/home.png, 1920x1080)
 * e convertidas pro tamanho real da tela com px().
 */
class MainActivity : Activity() {

    // Cores (tema claro do modelo)
    private val BG = Color.WHITE
    private val TEXT = Color.BLACK
    private val CARD = Color.parseColor("#C8D0DA")
    private val CARD_ON = Color.parseColor("#AEB8C5")
    private val ICON = Color.parseColor("#3C4450")

    // Medidas do modelo (px em 1920x1080)
    private val MARGIN = 108
    private val CARD_W = 308
    private val CARD_H = 308
    private val CARD_H_ON = 410
    private val CARD_GAP = 41
    private val CARD_RADIUS = 56
    private val STRIP_TOP = 561

    private var scale = 1f
    private fun px(v: Number) = (v.toFloat() * scale).roundToInt()

    private lateinit var root: FrameLayout
    private lateinit var date: TextView
    private lateinit var clock: TextView
    private lateinit var weather: TextView
    private lateinit var label: TextView
    private lateinit var scroller: HorizontalScrollView
    private lateinit var row: LinearLayout

    private var favorites = mutableListOf<String>()
    private var focusedCard: View? = null

    private val locale = Locale("pt", "BR")
    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            clock.text = SimpleDateFormat("HH:mm", locale).format(now)
            val day = SimpleDateFormat("EEE", locale).format(now).replace(".", "").uppercase(locale)
            val month = SimpleDateFormat("MMM", locale).format(now).replace(".", "").uppercase(locale)
            date.text = "$day, ${SimpleDateFormat("d", locale).format(now)} de $month"
            clock.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = resources.displayMetrics
        scale = max(dm.widthPixels, dm.heightPixels) / 1920f

        root = FrameLayout(this).apply { setBackgroundColor(BG); clipChildren = false }
        setContentView(root)

        // Data (esquerda), relógio (centro), clima (direita, V2)
        date = text(40f, light = false).also { place(it, left = MARGIN, top = 111) }
        weather = text(40f, light = false).also {
            place(it, top = 111, gravity = Gravity.END, right = MARGIN)
        }
        clock = text(185f, light = true).also { place(it, top = 56, gravity = Gravity.CENTER_HORIZONTAL) }

        buildCircles()
        buildStrip()

        // Nome do app focado
        label = text(56f, light = false).also { place(it, left = 0, top = 566) }
    }

    override fun onResume() {
        super.onResume()
        tick.run()
        favorites = Favorites.load(this)
        fillStrip()
    }

    override fun onPause() {
        super.onPause()
        clock.removeCallbacks(tick)
    }

    // Apertar Home já estando aqui: volta pro primeiro favorito.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        scroller.scrollTo(0, 0)
        row.getChildAt(0)?.requestFocus()
    }

    // Voltar na Home não faz nada (não sai do launcher).
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}

    // ---------- Círculos ----------

    private fun buildCircles() {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }
        place(bar, top = 270, gravity = Gravity.CENTER_HORIZONTAL)

        val items = listOf(
            R.drawable.ic_grid to "Todos os apps",
            R.drawable.ic_theme to "Tema",
            R.drawable.ic_boost to "Acelerador",
            R.drawable.ic_hdmi to "HDMI",
            R.drawable.ic_settings to "Configurações",
        )
        items.forEachIndexed { i, (icon, name) ->
            val c = FrameLayout(this).apply {
                isFocusable = true
                background = round(CARD, px(50).toFloat())
                contentDescription = name
                addView(ImageView(context).apply {
                    setImageResource(icon)
                    setColorFilter(ICON)
                }, FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER))
                setOnFocusChangeListener { v, has ->
                    (v.background as GradientDrawable).setColor(if (has) CARD_ON else CARD)
                    v.animate().scaleX(if (has) 1.12f else 1f).scaleY(if (has) 1.12f else 1f).setDuration(120).start()
                    if (has) { focusedCard = null; showLabel(null) }
                }
                setOnClickListener { onCircle(i) }
                setOnLongClickListener { onCircleLong(i) }
            }
            bar.addView(c, LinearLayout.LayoutParams(px(100), px(100)).apply {
                if (i > 0) leftMargin = px(40)
            })
        }
    }

    private fun onCircle(i: Int) {
        when (i) {
            0 -> toast("Grade de apps: próximo passo")
            1 -> toast("Tema claro/escuro: em breve")
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

    // ---------- Faixa de favoritos ----------

    private fun buildStrip() {
        scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            clipToPadding = false
            setOnScrollChangeListener { _, _, _, _, _ -> focusedCard?.let { showLabel(it) } }
        }
        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        scroller.addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, px(CARD_H_ON)))
        root.addView(scroller, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H_ON)
        ).apply {
            topMargin = px(STRIP_TOP); leftMargin = px(MARGIN); rightMargin = px(MARGIN)
        })
    }

    private fun fillStrip() {
        val hadFocus = row.indexOfChild(focusedCard).coerceAtLeast(0)
        row.removeAllViews()
        favorites.forEach { pkg -> row.addView(card(Apps.label(this, pkg), pkg)) }
        if (favorites.size < Favorites.MAX) row.addView(card("Adicionar", null))
        row.getChildAt(hadFocus.coerceAtMost(row.childCount - 1))?.requestFocus()
    }

    private fun card(name: String, pkg: String?): View {
        val c = FrameLayout(this).apply {
            isFocusable = true
            contentDescription = name
            background = round(CARD, px(CARD_RADIUS).toFloat())
            tag = name
            val img = ImageView(context).apply {
                if (pkg != null) setImageDrawable(Apps.icon(context, pkg))
                else { setImageResource(R.drawable.ic_plus); setColorFilter(Color.WHITE) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val s = if (pkg != null) 150 else 124
            addView(img, FrameLayout.LayoutParams(px(s), px(s), Gravity.CENTER))

            setOnFocusChangeListener { v, has ->
                setCardHeight(v, if (has) CARD_H_ON else CARD_H)
                if (has) { focusedCard = v; v.post { showLabel(v) } }
            }
            setOnClickListener {
                if (pkg == null) toast("Escolher favoritos: próximo passo")
                else if (!Apps.open(context, pkg)) toast("Não deu pra abrir $name")
            }
        }
        val lp = LinearLayout.LayoutParams(px(CARD_W), px(CARD_H))
        lp.rightMargin = px(CARD_GAP)
        c.layoutParams = lp
        return c
    }

    private fun setCardHeight(v: View, h: Int) {
        val lp = v.layoutParams
        lp.height = px(h)
        v.layoutParams = lp
    }

    /** Mostra o nome à direita do card focado (ou à esquerda se não couber). */
    private fun showLabel(card: View?) {
        if (card == null) { label.visibility = View.INVISIBLE; return }
        label.text = card.tag as String
        val w = label.paint.measureText(label.text.toString())
        val cardLeft = px(MARGIN) + card.left - scroller.scrollX
        var x = cardLeft + px(CARD_W) + px(45).toFloat()
        if (x + w > root.width - px(MARGIN)) x = cardLeft - px(45) - w
        label.translationX = x
        label.visibility = View.VISIBLE
    }

    // ---------- Ajudantes ----------

    private fun text(sizePx1080: Float, light: Boolean) = TextView(this).apply {
        setTextColor(TEXT)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx1080 * scale)
        includeFontPadding = false
        typeface = resources.getFont(if (light) R.font.inter_light else R.font.inter_regular)
        isFocusable = false
    }

    private fun place(v: View, left: Int = 0, top: Int = 0, right: Int = 0, gravity: Int = Gravity.START) {
        root.addView(v, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity or Gravity.TOP
        ).apply { leftMargin = px(left); topMargin = px(top); rightMargin = px(right) })
    }

    private fun round(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radius
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
