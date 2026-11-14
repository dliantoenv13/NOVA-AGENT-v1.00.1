package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.MainScreen
import com.example.ui.NovaViewModel
import com.example.ui.theme.NovaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModel, or Firebase operation
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge monochrome UI
        enableEdgeToEdge()

        val appContainer = (application as NovaApplication).container

        setContent {
            val viewModel: NovaViewModel = viewModel(
                factory = NovaViewModel.Factory(application, appContainer)
            )

            var pendingMicCallback by remember { mutableStateOf<(() -> Unit)?>(null) }

            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.refreshPermissionsAndSyncService()
                if (granted) {
                    pendingMicCallback?.invoke()
                }
                pendingMicCallback = null
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                viewModel.refreshPermissionsAndSyncService()
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshPermissionsAndSyncService()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                viewModel.refreshPermissionsAndSyncService()
            }

            NovaTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestMicPermission = { onGranted ->
                        pendingMicCallback = onGranted
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.refreshPermissionsAndSyncService()
                        }
                    },
                    onOpenOverlaySettings = {
                        val overlayIntent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(overlayIntent)
                    },
                    onOpenAccessibilitySettings = {
                        val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(accIntent)
                    }
                )
            }
        }
    }
}
