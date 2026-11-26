package com.tjo7.stamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import com.tjo7.stamp.ui.*
import com.tjo7.stamp.domain.*

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var pendingExport: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        vm.autoCheck()
        setContent {
            val st by vm.ui.collectAsState()
            val snack = remember { SnackbarHostState() }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it, false) }
            val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                val all = pendingExport; pendingExport = null
                if (ok && all != null) vm.export(all)
            }
            LaunchedEffect(st.busy) {
                if (st.busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

            fun requestExport(all: Boolean) {
                if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    pendingExport = all; perm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else vm.export(all)
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Tjo7Theme(st.dark) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snack) },
                        topBar = { if (st.tab == Tab.EDITOR) AppHeader(st.dark) { vm.setDark(!st.dark) } },
                        bottomBar = {
                          Column {
                            val g = st.global
                            val fmtName = if (g.format == OutFormat.AUTO) stringResource(R.string.save_status_fmt).substringBefore(" · ") else g.format.name
                            val sizeName = when (g.size) { SizeSel.ORIG -> stringResource(R.string.size_orig); SizeSel.W1080 -> "1080p"; SizeSel.W720 -> "720p"; SizeSel.W540 -> "540p" }
                            val n = st.items.size
                            SaveBar(
                                if (n == 0) stringResource(R.string.save_idle).substringBefore(" · ") else stringResource(R.string.save_status_n, n),
                                "$fmtName · $sizeName",
                                if (n > 1 && !g.transparent) stringResource(R.string.download_all) else stringResource(R.string.download_one),
                                enabled = n > 0 || g.transparent, busy = st.busy,
                            ) { requestExport(n > 1 && !g.transparent) }
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                val tabs = listOf(
                                    Triple(Tab.EDITOR, R.string.tab_editor, OldIcons.Editor),
                                    Triple(Tab.CREDIT, R.string.tab_credit, OldIcons.Credit),
                                    Triple(Tab.OUTPUT, R.string.tab_output, OldIcons.Output),
                                    Triple(Tab.SETTINGS, R.string.tab_settings, OldIcons.Settings),
                                )
                                for ((t, label, icon) in tabs) NavigationBarItem(
                                    selected = st.tab == t, onClick = { vm.setTab(t) },
                                    modifier = Modifier.testTag("tab_" + t.name.lowercase()),
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer, unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    icon = {
                                        BadgedBox(badge = { if (t == Tab.SETTINGS && st.update is UpdateUi.Available) Badge(containerColor = androidx.compose.ui.graphics.Color(0xFFF2BD69)) }) { Icon(icon, null, Modifier.size(22.dp)) }
                                    },
                                    label = { Text(stringResource(label)) },
                                )
                            }
                          }
                        },
                    ) { pad ->
                        Box(Modifier.padding(pad).fillMaxSize()) {
                            when (st.tab) {
                                Tab.EDITOR -> EditorTab(st, vm,
                                    onPick = { picker.launch(arrayOf("image/*", "video/*", "application/zip", "application/octet-stream", "application/x-zip-compressed")) },
                                    onExport = { requestExport(it) },
                                    onShare = { e -> vm.shareIntent(e)?.let { startActivity(it) } })
                                Tab.CREDIT -> CreditTab(st, vm, onPick = { picker.launch(arrayOf("image/*", "video/*", "application/zip", "application/octet-stream", "application/x-zip-compressed")) })
                                Tab.OUTPUT -> OutputTab(st, vm)
                                Tab.SETTINGS -> SettingsTab(st, vm) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/qr/BTFMWNAXQXM3L1"))) }
                            }
                        }
                        UpdateSheet(st, vm) { vm.installIntent()?.let { startActivity(it) } }
                        ResultsSheet(st, vm) { e -> vm.shareIntent(e)?.let { startActivity(it) } }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Files shared into the app from the gallery / other apps. */
    private fun handleIntent(i: Intent?) {
        i ?: return
        val uris = when (i.action) {
            Intent.ACTION_SEND -> listOfNotNull(streamUri(i))
            Intent.ACTION_SEND_MULTIPLE -> streamUris(i)
            else -> emptyList()
        }
        if (uris.isNotEmpty()) { vm.importUris(uris, true); i.action = null }
    }

    @Suppress("DEPRECATION")
    private fun streamUri(i: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun streamUris(i: Intent): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)) ?: emptyList()
}
