package jatx.audiobookplayer

import android.app.Application

class App: Application() {

    override fun onCreate() {
        super.onCreate()
        settings = Settings(applicationContext)
    }

    companion object {
        lateinit var settings: Settings
        val activityProvider = ActivityProvider()
    }
}

class ActivityProvider {
    var currentActivity: MainActivity? = null
}