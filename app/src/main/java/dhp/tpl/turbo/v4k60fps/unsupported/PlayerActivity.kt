package dhp.tpl.turbo.v4k60fps.unsupported

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dhp.tpl.turbo.v4k60fps.unsupported.data.RecentVideo
import dhp.tpl.turbo.v4k60fps.unsupported.data.Store
import dhp.tpl.turbo.v4k60fps.unsupported.data.displayName
import dhp.tpl.turbo.v4k60fps.unsupported.engine.TurboPlayer
import dhp.tpl.turbo.v4k60fps.unsupported.ui.PlayerScreen
import dhp.tpl.turbo.v4k60fps.unsupported.ui.TurboTheme

class PlayerActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var turbo: TurboPlayer
    private var title by mutableStateOf("")
    private var rotationLocked by mutableStateOf(false)

    private val pickSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) turbo.loadSubtitle(uri, displayName(this, uri))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        store = Store(this)
        turbo = TurboPlayer(this, store)
        handleIntent(intent)

        setContent {
            TurboTheme {
                PlayerScreen(
                    turbo = turbo,
                    title = title,
                    showStatsInitially = store.showStats,
                    onShowStatsChanged = { store.showStats = it },
                    rotationLocked = rotationLocked,
                    onToggleRotationLock = ::toggleRotationLock,
                    onPickSubtitle = { pickSubtitle.launch(arrayOf("*/*")) },
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        savePosition()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri = videoUri(intent) ?: run { finish(); return }
        val key = uri.toString()
        title = displayName(this, uri)
        val previous = store.recent(key)
        val resumeAt = previous?.takeIf { it.durationMs - it.positionMs > 5_000 }?.positionMs ?: 0L
        store.upsertRecent(
            RecentVideo(
                uri = key,
                name = title,
                positionMs = previous?.positionMs ?: 0,
                durationMs = previous?.durationMs ?: 0,
                lastPlayed = System.currentTimeMillis(),
            ),
        )
        turbo.open(uri, resumeAt)
    }

    /** "Open with" puts the video in data; "Share" puts it in EXTRA_STREAM (or ClipData). */
    private fun videoUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return intent.data
        return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun toggleRotationLock() {
        rotationLocked = !rotationLocked
        requestedOrientation = if (rotationLocked) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        turbo.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        turbo.release()
    }

    private fun savePosition() {
        val uri = turbo.currentUri ?: return
        val player = turbo.player ?: return
        val duration = player.duration.takeIf { it > 0 } ?: return
        store.updatePosition(uri.toString(), player.currentPosition, duration)
    }
}
