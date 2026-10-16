@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dhp.tpl.turbo.v4k60fps.unsupported.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dhp.tpl.turbo.v4k60fps.unsupported.data.RecentVideo
import dhp.tpl.turbo.v4k60fps.unsupported.data.Store
import dhp.tpl.turbo.v4k60fps.unsupported.data.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(store: Store, onPlay: (Uri) -> Unit, onOpenLab: () -> Unit) {
    val context = LocalContext.current
    var recents by remember { mutableStateOf(store.recents()) }
    var config by remember { mutableStateOf(store.loadConfig()) }
    var showEngine by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        recents = store.recents()
        config = store.loadConfig()
        onPauseOrDispose { }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onPlay(uri)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Turbo4K") },
                actions = {
                    IconButton(onClick = onOpenLab) { Icon(Icons.Rounded.Science, "Decoder lab") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("video/*")) },
                icon = { Icon(Icons.Rounded.FolderOpen, null) },
                text = { Text("Open video") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                EngineCard(
                    summary = config.summary(),
                    onEngine = { showEngine = true },
                    onLab = onOpenLab,
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Recent",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 12.dp, start = 4.dp),
                    )
                    if (recents.isNotEmpty()) {
                        TextButton(onClick = { store.clearRecents(); recents = emptyList() }) {
                            Text("Clear")
                        }
                    }
                }
            }
            if (recents.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Tap Open video, or choose Turbo4K in the Gallery's \"Open with\" menu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(recents, key = { it.uri }) { video ->
                RecentRow(
                    video = video,
                    onClick = { onPlay(Uri.parse(video.uri)) },
                    onRemove = {
                        store.removeRecent(video.uri)
                        recents = store.recents()
                    },
                )
            }
        }
    }

    if (showEngine) {
        ModalBottomSheet(
            onDismissRequest = { showEngine = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Row(
                Modifier.padding(start = 24.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Decoder engine", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { showEngine = false }) { Icon(Icons.Rounded.Close, "Close") }
            }
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                EngineSettings(config, onChange = { config = it; store.saveConfig(it) }, showTitle = false)
            }
        }
    }
}

@Composable
private fun EngineCard(summary: String, onEngine: () -> Unit, onLab: () -> Unit) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hardware decoder, unlocked", style = MaterialTheme.typography.titleLarge)
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onEngine) {
                    Icon(Icons.Rounded.Bolt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Engine")
                }
                TextButton(onClick = onLab) { Text("Test my decoder") }
            }
        }
    }
}

@Composable
private fun RecentRow(video: RecentVideo, onClick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(null, video.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver
                    .loadThumbnail(Uri.parse(video.uri), android.util.Size(320, 180), null)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 112.dp, height = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                val bitmap = thumbnail
                if (bitmap != null) {
                    Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                } else {
                    Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (video.durationMs > 0) {
                    Text(
                        "${formatTime(video.positionMs)} / ${formatTime(video.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearWavyProgressIndicator(
                        progress = { (video.positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove") }
        }
    }
}
