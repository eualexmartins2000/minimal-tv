package com.alex.minimaltv

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Clima da cidade (Open-Meteo, grátis e sem chave). Atualiza no máximo a cada 20 minutos.
 * Se não tiver internet, mostra o último valor guardado.
 */
object Weather {
    const val CITY = "Colméia-TO"
    private const val LAT = "-8.72"
    private const val LON = "-48.76"
    private const val EVERY_MS = 20 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("minimaltv", Context.MODE_PRIVATE)

    /** Ex.: "Colméia-TO, 29°C" (ou só a cidade, enquanto não houver temperatura). */
    fun cached(ctx: Context): String {
        val p = prefs(ctx)
        return if (p.contains("temp")) "$CITY, ${p.getInt("temp", 0)}°C" else CITY
    }

    fun refresh(ctx: Context, done: () -> Unit) {
        val app = ctx.applicationContext
        val p = prefs(app)
        if (System.currentTimeMillis() - p.getLong("temp_at", 0) < EVERY_MS) return
        Thread {
            try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&current=temperature_2m&timezone=auto")
                val c = url.openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000
                val body = c.inputStream.bufferedReader().use { it.readText() }
                val t = JSONObject(body).getJSONObject("current").getDouble("temperature_2m").roundToInt()
                p.edit().putInt("temp", t).putLong("temp_at", System.currentTimeMillis()).apply()
                Handler(Looper.getMainLooper()).post(done)
            } catch (e: Exception) { /* sem internet: fica o último valor */ }
        }.start()
    }
}
