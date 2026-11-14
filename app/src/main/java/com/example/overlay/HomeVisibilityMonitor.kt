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
    private var launcherPackages: Set<String> = resolveLauncherPackages()

    @Volatile
    private var imePackages: Set<String> = resolveImePackages()

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

    fun onForegroundPackageChanged(packageName: String?) {
        if (packageName.isNullOrBlank()) return
        // Ignore software keyboard windows so they don't flicker home state
        if (packageName in imePackages) return

        _currentForegroundPackage.value = packageName

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isUnlockedAndAwake = (keyguard?.isKeyguardLocked != true) && (power?.isInteractive != false)

        if (!isUnlockedAndAwake) {
            _isOnHomeScreen.value = false
            return
        }

        // Ignore our own overlay service window if it ever emits an event,
        // EXCEPT MainActivity is part of context.packageName -> if MainActivity is active, hide popup!
        val isHome = packageName in launcherPackages
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
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
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
