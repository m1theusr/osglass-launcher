package com.osglass.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.osglass.launcher.ui.GlassApp
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    /** Botão Home com o OSGlass já aberto: volta ao topo. */
    private val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Voltando de outro app: recarrega destaques e permissões. */
    private val resumeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlassApp(homeEvents, resumeEvents) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInteraction = SystemClock.elapsedRealtime()
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        lastInteraction = SystemClock.elapsedRealtime()
        resumeEvents.tryEmit(Unit)
    }

    override fun onPause() {
        isResumed = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lastInteraction = SystemClock.elapsedRealtime()
        homeEvents.tryEmit(Unit)
    }

    companion object {
        /** Usado pelo StandBy automático: tempo parado na tela inicial. */
        @Volatile var lastInteraction: Long = SystemClock.elapsedRealtime()
        @Volatile var isResumed: Boolean = false
    }
}
