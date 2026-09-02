package zed.rainxch.core.data.services

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var installed = false

    private var resumed: WeakReference<Activity> = WeakReference(null)

    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun activity(): Activity? {
        val current = resumed.get() ?: return null
        if (current.isFinishing || current.isDestroyed) return null
        return current
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) {
            resumed = WeakReference(null)
        }
    }

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        if (resumed.get() === activity) {
            resumed = WeakReference(null)
        }
    }
}
