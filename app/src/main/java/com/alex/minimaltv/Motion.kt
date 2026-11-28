package com.alex.minimaltv

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.Interpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mola estilo Apple: a mesma conta do protótipo (massa 1, rigidez 260, amortecimento 22).
 * Sem biblioteca extra: é só uma curva calculada uma vez.
 */
class SpringInterpolator : Interpolator {
    private val w0 = sqrt(260.0)
    private val z = 22.0 / (2 * sqrt(260.0))
    private val wd = w0 * sqrt(1 - z * z)
    private val total: Double
    val durationMs: Long

    private fun pos(t: Double) = 1 - exp(-z * w0 * t) * (cos(wd * t) + (z * w0 / wd) * sin(wd * t))

    init {
        var last = 0.0
        var t = 0.0
        while (t < 3.0) { if (abs(1 - pos(t)) > 0.002) last = t; t += 0.005 }
        total = last + 0.02
        durationMs = (total * 1000).toLong()
    }

    override fun getInterpolation(input: Float): Float =
        if (input >= 1f) 1f else if (input <= 0f) 0f else pos(input * total).toFloat()
}

object Motion {
    val spring = SpringInterpolator()
    val DUR: Long get() = spring.durationMs
}

/**
 * Fundo do tema Vidro: três manchas de cor suaves (círculos com degradê radial, sem desfoque de verdade,
 * então é leve pra TV). As cores acompanham o app focado, com transição suave.
 */
class AuraView(ctx: Context, private val strength: Float) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eval = ArgbEvaluator()
    private var from = intArrayOf(0, 0, 0)
    private var to = intArrayOf(0, 0, 0)
    private var cur = intArrayOf(0, 0, 0)
    private var anim: ValueAnimator? = null

    fun setColors(a: Int, b: Int, c: Int, animate: Boolean) {
        val next = intArrayOf(a, b, c)
        if (next.contentEquals(to)) return
        anim?.cancel()
        if (!animate || to[0] == 0) { to = next; cur = next.copyOf(); invalidate(); return }
        from = cur.copyOf(); to = next
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            addUpdateListener {
                val f = it.animatedFraction
                for (i in 0..2) cur[i] = eval.evaluate(f, from[i], to[i]) as Int
                invalidate()
            }
            start()
        }
    }

    private fun blob(c: Canvas, cx: Float, cy: Float, r: Float, col: Int) {
        val a = (strength * 255).toInt()
        val base = Color.rgb(Color.red(col), Color.green(col), Color.blue(col))
        paint.shader = RadialGradient(
            cx, cy, r,
            intArrayOf((a shl 24) or (base and 0xFFFFFF), ((a * 0.7f).toInt() shl 24) or (base and 0xFFFFFF), base and 0xFFFFFF),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, paint)
    }

    override fun onDraw(c: Canvas) {
        val s = width / 1920f
        blob(c, 1500 * s, 170 * s, 600 * s, cur[0])
        blob(c, 190 * s, 870 * s, 540 * s, cur[1])
        blob(c, 970 * s, 910 * s, 420 * s, cur[2])
    }
}
