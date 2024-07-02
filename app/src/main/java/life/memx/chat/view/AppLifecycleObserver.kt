import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class AppLifecycleObserver(
    private val onEnterForeground: () -> Unit,
    private val onEnterBackground: () -> Unit
) : LifecycleObserver {

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        Log.d("AppLifecycleObserver", "App entered foreground")
        onEnterForeground()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        Log.d("AppLifecycleObserver", "App entered background")
        onEnterBackground()
    }
}