package com.noros.pay

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).crossfade(true).build()
}
