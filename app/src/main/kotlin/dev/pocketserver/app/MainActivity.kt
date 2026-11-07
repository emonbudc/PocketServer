package dev.pocketserver.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pocketserver.app.ui.PocketServerApp
import dev.pocketserver.app.viewmodel.AppViewModelFactory
import dev.pocketserver.app.viewmodel.ServerViewModel
import dev.pocketserver.ui.theme.PocketServerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PocketServerApplication).container
        setContent {
            val factory = remember { AppViewModelFactory(container) }
            val serverViewModel: ServerViewModel = viewModel(factory = factory)
            val settings by serverViewModel.settings.collectAsStateWithLifecycle()
            PocketServerTheme(themeMode = settings.themeMode) {
                PocketServerApp(
                    serverViewModel = serverViewModel,
                    factory = factory,
                )
            }
        }
    }
}
