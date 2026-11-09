package com.osglass.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * O Google TV sempre abre o próprio launcher no botão Home, mesmo com outro launcher instalado.
 * Este serviço observa só a janela do launcher do Google TV e, quando a tela inicial dele
 * aparece, traz o OSGlass pra frente. (Mesma técnica usada pelo Projectivy.)
 *
 * Também é por aqui que a Central de controle abre o menu de energia e roda o timer para dormir,
 * já que essas ações globais só estão disponíveis para serviços de acessibilidade.
 */
class HomeOverrideService : AccessibilityService() {
    private var lastLaunch = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        sleepAt = null
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    }

    override fun onServiceConnected() { instance = this }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable)
        if (instance === this) instance = null
        sleepAt = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName?.toString() != GOOGLE_TV_LAUNCHER) return
        val cls = event.className?.toString().orEmpty()
        if (!cls.contains(".home.") && !cls.endsWith("HomeActivity")) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 600) return
        lastLaunch = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"

        @Volatile private var instance: HomeOverrideService? = null
        @Volatile private var sleepAt: Long? = null

        /** Abre o menu de energia do Android. Falso se o serviço não estiver ativo. */
        fun showPowerMenu(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) == true

        /** Agenda a TV para dormir em [minutes] (0 cancela). Falso se o serviço não estiver ativo. */
        fun setSleepTimer(minutes: Int): Boolean {
            val s = instance ?: return false
            s.handler.removeCallbacks(s.sleepRunnable)
            if (minutes <= 0) {
                sleepAt = null
            } else {
                sleepAt = SystemClock.elapsedRealtime() + minutes * 60_000L
                s.handler.postDelayed(s.sleepRunnable, minutes * 60_000L)
            }
            return true
        }

        fun sleepMinutesLeft(): Int? = sleepAt?.let {
            (((it - SystemClock.elapsedRealtime()) + 59_999) / 60_000).toInt().coerceAtLeast(1)
        }
    }
}
