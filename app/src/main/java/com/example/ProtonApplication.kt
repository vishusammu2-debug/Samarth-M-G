package com.example

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Bundle
import android.util.Log
import com.example.service.ProtonCoreStateBus
import java.lang.ref.WeakReference

private const val TAG = "ProtonApplication"

class ProtonApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Proton 5.0 application initialized (memory-safe & adaptive mode ready)")
        // Note: Firebase App Check is initialized strictly inside MainActivity.onCreate(intent)
        // before UI/ViewModel access per Firebase AI Logic security contract.

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                resumedActivityRef = WeakReference(activity)
            }
            override fun onActivityPaused(activity: Activity) {
                if (resumedActivityRef.get() === activity) {
                    resumedActivityRef = WeakReference(null)
                }
            }
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.w(TAG, "System memory trim requested (level=$level); throttling orb & caches")
            ProtonCoreStateBus.notifyMemoryPressure(true)
        } else if (level <= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            ProtonCoreStateBus.notifyMemoryPressure(false)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory triggered; switching orb to lightweight mode")
        ProtonCoreStateBus.notifyMemoryPressure(true)
    }

    companion object {
        @Volatile
        private var resumedActivityRef: WeakReference<Activity?> = WeakReference(null)

        fun currentResumedActivity(): Activity? = resumedActivityRef.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }
}
