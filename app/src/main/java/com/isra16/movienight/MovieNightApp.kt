package com.isra16.movienight

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.isra16.movienight.notify.createUploadChannels

class MovieNightApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createUploadChannels(this)
        registerActivityLifecycleCallbacks(VisibilityCallbacks())
    }

    /**
     * Lleva la cuenta de pantallas que se ven (para no avisar de una subida que la persona está mirando) y,
     * al volver a ver la app, borra el aviso viejo de subida.
     */
    private inner class VisibilityCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            container.visibility.onStart()
            container.uploadNotifier.onAppShown()
        }

        override fun onActivityStopped(activity: Activity) = container.visibility.onStop()
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
