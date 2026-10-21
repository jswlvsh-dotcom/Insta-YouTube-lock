package com.jaivish.gowork.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jaivish.gowork.engine.LockActions
import com.jaivish.gowork.engine.LockRequest
import com.jaivish.gowork.ui.lock.LockScreen
import com.jaivish.gowork.ui.theme.GoWorkTheme

/**
 * Full-screen lock drawn as an accessibility overlay. That window type sits above the
 * app, the status bar and the navigation bar, and needs no extra "draw over apps" permission.
 */
class LockOverlay(
    private val service: AccessibilityService,
    private val actions: LockActions,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val current = mutableStateOf<LockRequest?>(null)
    private var root: OverlayRoot? = null
    private var owner: OverlayOwner? = null

    val isShowing: Boolean get() = root != null

    fun show(request: LockRequest) {
        current.value = request
        if (root != null) return
        val lifecycleOwner = OverlayOwner().also { it.start() }
        val view = OverlayRoot(service) { current.value?.let(actions::back) }
        view.setViewTreeLifecycleOwner(lifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        view.setViewTreeViewModelStoreOwner(lifecycleOwner)
        val compose = ComposeView(service).apply {
            setContent {
                GoWorkTheme {
                    current.value?.let { LockScreen(request = it, actions = actions) }
                }
            }
        }
        view.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        try {
            windowManager.addView(view, layoutParams())
            root = view
            owner = lifecycleOwner
            view.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Could not add lock overlay", e)
            lifecycleOwner.stop()
            current.value = null
        }
    }

    fun hide() {
        val view = root ?: return
        root = null
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "Overlay already removed", e)
        }
        owner?.stop()
        owner = null
        current.value = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "GoWork lock"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
    }

    private companion object {
        const val TAG = "GoWorkOverlay"
    }
}

/** Root view of the overlay; turns the system Back button into "leave the app". */
private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** Lifecycle + saved state for a ComposeView that lives outside any Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
