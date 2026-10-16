package dhp.tpl.turbo.v4k60fps.unsupported

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dhp.tpl.turbo.v4k60fps.unsupported.data.Store
import dhp.tpl.turbo.v4k60fps.unsupported.ui.HomeScreen
import dhp.tpl.turbo.v4k60fps.unsupported.ui.LabScreen
import dhp.tpl.turbo.v4k60fps.unsupported.ui.TurboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(this)
        setContent {
            TurboTheme {
                var lab by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = lab) { lab = false }
                if (lab) {
                    LabScreen(onBack = { lab = false })
                } else {
                    HomeScreen(store, onPlay = ::play, onOpenLab = { lab = true })
                }
            }
        }
    }

    private fun play(uri: Uri) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}
