package com.example.hitmapstargets

import android.app.Application

class TargetsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // WorkManager's default initializer schedules persisted requests across restarts.
        if (TargetAlerts.isEnabled(this)) {
            TargetAlerts.setEnabled(this, true)
        }
    }
}
