package dev.pocketserver.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.pocketserver.app.di.AppContainer

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ServerViewModel::class.java) ->
            ServerViewModel(container) as T
        modelClass.isAssignableFrom(WorkspaceViewModel::class.java) ->
            WorkspaceViewModel(container) as T
        modelClass.isAssignableFrom(ToolingViewModel::class.java) ->
            ToolingViewModel(container) as T
        else -> error("Unknown ViewModel class: ${modelClass.name}")
    }
}
