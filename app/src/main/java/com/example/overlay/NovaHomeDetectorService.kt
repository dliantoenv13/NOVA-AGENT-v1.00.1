package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.NovaApplication

class NovaHomeDetectorService : AccessibilityService() {

    private val monitor: HomeVisibilityMonitor by lazy {
        (application as NovaApplication).container.homeVisibilityMonitor
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        monitor.refreshLauncherPackages()
        monitor.setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            monitor.onForegroundPackageChanged(
                packageName = pkg,
                isFullScreen = event.isFullScreen
            )
        }
    }

    override fun onInterrupt() {
        monitor.setAccessibilityConnected(false)
    }

    override fun onDestroy() {
        monitor.setAccessibilityConnected(false)
        super.onDestroy()
    }
}
