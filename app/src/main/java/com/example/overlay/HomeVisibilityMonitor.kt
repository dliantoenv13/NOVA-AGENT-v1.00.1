package com.example.overlay

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NovaPermissionState(
    val hasRecordAudio: Boolean = false,
    val hasPostNotifications: Boolean = true,
    val hasOverlayPermission: Boolean = false,
    val hasAccessibilityEnabled: Boolean = false
) {
    val allCapsulePermissionsReady: Boolean
        get() = hasRecordAudio && hasOverlayPermission && hasAccessibilityEnabled
}

class HomeVisibilityMonitor(private val context: Context) {

    private val _isOnHomeScreen = MutableStateFlow(false)
    val isOnHomeScreen: StateFlow<Boolean> = _isOnHomeScreen.asStateFlow()

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _currentForegroundPackage = MutableStateFlow<String?>(null)
    val currentForegroundPackage: StateFlow<String?> = _currentForegroundPackage.asStateFlow()

    private val _permissionState = MutableStateFlow(readPermissionsSnapshot())
    val permissionState: StateFlow<NovaPermissionState> = _permissionState.asStateFlow()

    @Volatile
    private var isAppInForeground: Boolean = false

    @Volatile
    private var launcherPackages: Set<String> = resolveLauncherPackages()

    @Volatile
    private var imePackages: Set<String> = resolveImePackages()

    fun setAppInForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            _isOnHomeScreen.value = false
        }
    }

    fun refreshLauncherPackages() {
        launcherPackages = resolveLauncherPackages()
        imePackages = resolveImePackages()
    }

    fun refreshPermissions() {
        val snapshot = readPermissionsSnapshot()
        _permissionState.value = snapshot
        _isAccessibilityConnected.value = snapshot.hasAccessibilityEnabled
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
        if (!connected) {
            _isOnHomeScreen.value = false
        }
        refreshPermissions()
    }

    fun onForegroundPackageChanged(packageName: String?, isFullScreen: Boolean) {
        if (packageName.isNullOrBlank()) return

        // Ignore software keyboards so search widgets on home screen don't flicker
        if (packageName in imePackages) return

        // If our own overlay emits a window event while MainActivity is NOT open, ignore it
        if (packageName == context.packageName && !isAppInForeground) {
            return
        }

        // Ignore transient non-fullscreen SystemUI overlays (toasts, status bar updates, volume panel)
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isUnlockedAndAwake = (keyguard?.isKeyguardLocked != true) && (power?.isInteractive != false)

        if (!isUnlockedAndAwake) {
            _isOnHomeScreen.value = false
            return
        }

        if ((packageName == "com.android.systemui" || packageName == "android") && !isFullScreen) {
            return
        }

        _currentForegroundPackage.value = packageName
        val isHome = (packageName in launcherPackages) && !isAppInForeground
        _isOnHomeScreen.value = isHome
    }

    fun onScreenLockedOrOff() {
        _isOnHomeScreen.value = false
    }

    private fun readPermissionsSnapshot(): NovaPermissionState {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val overlayGranted = Settings.canDrawOverlays(context)
        val accessibilityGranted = isNovaAccessibilityServiceEnabled()

        return NovaPermissionState(
            hasRecordAudio = micGranted,
            hasPostNotifications = notifGranted,
            hasOverlayPermission = overlayGranted,
            hasAccessibilityEnabled = accessibilityGranted
        )
    }

    private fun isNovaAccessibilityServiceEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val expectedPackage = context.packageName
        return enabledServices.any { info ->
            info.resolveInfo?.serviceInfo?.packageName == expectedPackage
        }
    }

    private fun resolveLauncherPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val flags = PackageManager.MATCH_DEFAULT_ONLY or PackageManager.MATCH_ALL
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
        val defaultResolve = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

        val packages = mutableSetOf<String>()
        defaultResolve?.activityInfo?.packageName?.let { pkg ->
            if (pkg != "android" && pkg != context.packageName) {
                packages.add(pkg)
            }
        }
        resolved.forEach { info ->
            val pkg = info.activityInfo?.packageName ?: return@forEach
            if (pkg != "android" && pkg != "com.android.settings" && pkg != context.packageName) {
                packages.add(pkg)
            }
        }
        return packages
    }

    private fun resolveImePackages(): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptySet()
        return imm.enabledInputMethodList.mapNotNull { it.packageName }.toSet()
    }
}
