package com.intelligentapps.arspeedlimit

import android.app.Application
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.utils.SystemInfoUtils

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()


        if (SystemInfoUtils.isVisionSupported()) {
            VisionManager.init(this, getString(R.string.mapbox_access_token))
        }
    }
}
