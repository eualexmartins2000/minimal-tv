package com.alex.minimaltv

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Faz o botão Home abrir o Minimal TV.
 *
 * Quando o launcher original da TCL aparece, abrimos o nosso por cima.
 * Só escuta eventos desse pacote (veja res/xml/accessibility_service.xml)
 * e não lê o conteúdo da tela.
 */
class HomeService : AccessibilityService() {

    private var last = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (e.packageName?.toString() != TCL_LAUNCHER) return
        val now = SystemClock.uptimeMillis()
        if (now - last < 1000) return          // evita abrir várias vezes seguidas
        last = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onInterrupt() {}

    companion object {
        const val TCL_LAUNCHER = "com.google.android.tvlauncher"
    }
}
