package com.alex.minimaltv

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Passo 1: tela mínima só para provar que o app instala e vira Home.
class MainActivity : Activity() {

    private lateinit var clock: TextView
    private val tick = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date())
            clock.postDelayed(this, 10_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clock = TextView(this).apply {
            textSize = 96f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
        }
        setContentView(clock)
    }

    override fun onResume() { super.onResume(); tick.run() }
    override fun onPause() { super.onPause(); clock.removeCallbacks(tick) }

    // Voltar na Home não faz nada (não sai do launcher).
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {}
}
