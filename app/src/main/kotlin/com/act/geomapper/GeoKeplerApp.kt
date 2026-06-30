package com.act.geomapper

import android.app.Application
import com.act.geomapper.security.SecurityChecker

class GeoKeplerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SecurityChecker.verificar(this)
    }
}
