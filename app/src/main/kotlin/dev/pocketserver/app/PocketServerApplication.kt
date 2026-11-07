package dev.pocketserver.app

import android.app.Application
import dev.pocketserver.app.di.AppContainer

class PocketServerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.initialize()
    }
}
