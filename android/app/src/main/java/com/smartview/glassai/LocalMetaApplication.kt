package com.smartview.glassai

import android.app.Application

class LocalMetaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @JvmStatic
        lateinit var instance: LocalMetaApplication
    }
}
