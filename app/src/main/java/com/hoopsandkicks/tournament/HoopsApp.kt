package com.hoopsandkicks.tournament

import android.app.Application
import com.hoopsandkicks.tournament.data.Repository
import com.hoopsandkicks.tournament.data.remote.ViewerPrefs

class HoopsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        viewerPrefs = ViewerPrefs(this)
    }

    companion object {
        lateinit var repo: Repository
            private set
        lateinit var viewerPrefs: ViewerPrefs
            private set
    }
}
