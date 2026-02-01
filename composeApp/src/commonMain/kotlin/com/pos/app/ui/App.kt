package com.pos.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.runtime.rememberCoroutineScope
import com.pos.app.data.AppContainer

val LocalStore = staticCompositionLocalOf<PosStore> { error("PosStore not provided") }

enum class PosTab { REGISTER, HISTORY, SETTINGS }

@Composable
fun App(container: AppContainer) {
    val store = remember { PosStore(container) }
    PosTheme {
        CompositionLocalProvider(LocalStore provides store) {
            RootScaffold()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootScaffold() {
    val store = LocalStore.current
    val state by store.state.collectAsState()
    var tab by remember { mutableStateOf(PosTab.REGISTER) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.settings.storeName) },
                actions = {
                    TextButton(onClick = { tab = PosTab.REGISTER }) { Text("Kasir") }
                    TextButton(onClick = { tab = PosTab.HISTORY }) { Text("Riwayat") }
                    TextButton(onClick = { tab = PosTab.SETTINGS }) { Text("Pengaturan") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                PosTab.REGISTER -> RegisterScreen(scope, snackbar)
                PosTab.HISTORY -> HistoryScreen(scope, snackbar)
                PosTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
