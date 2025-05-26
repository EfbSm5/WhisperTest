package com.example.whispertest

import android.app.Application

class WhisperApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppUtil.init(applicationContext)
    }
}