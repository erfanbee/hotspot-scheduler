package com.iranjan.hotspotscheduler

import android.app.Application
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HotspotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AttemptLog.init(this)
    }
}
