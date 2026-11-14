package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.overlay.NovaPermissionState
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaHairline
import com.example.ui.theme.NovaPaper
import com.example.ui.theme.NovaSlate
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaSuccessContainer
import com.example.ui.theme.NovaWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsGuideSheet(
    permissionState: NovaPermissionState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NovaWhite,
        contentColor = NovaBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aktivasi Kapsul Home Screen & \"Hi Nova\"",
                style = MaterialTheme.typography.headlineSmall,
                color = NovaBlack
            )
            Text(
                text = "Agar popup kamera depan dan perintah suara \"Hi Nova\" berjalan permanen khusus di Home Screen (dan otomatis menghilang saat membuka aplikasi lain), aktifkan izin berikut:",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaSlate
            )

            PermissionItemCard(
                icon = Icons.Rounded.Mic,
                title = "1. Izin Mikrofon",
                description = "Dibutuhkan untuk obrolan suara, Tap to Talk, dan mengenali ucapan \"Hi Nova\".",
                isGranted = permissionState.hasRecordAudio,
                buttonText = "Izinkan",
                testTag = "perm_mic_button",
                onAction = onRequestMic
            )

            PermissionItemCard(
                icon = Icons.Rounded.Layers,
                title = "2. Tampil di Atas Aplikasi",
                description = "Memungkinkan kapsul Nova mengambang di area kamera depan saat di Home Screen.",
                isGranted = permissionState.hasOverlayPermission,
                buttonText = "Buka Pengaturan",
                testTag = "perm_overlay_button",
                onAction = onOpenOverlaySettings
            )

            PermissionItemCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "3. Deteksi Layar Utama (Aksesibilitas)",
                description = "Mendeteksi kapan Anda di Home Screen agar popup & Hi Nova langsung mati saat membuka aplikasi lain.",
                isGranted = permissionState.hasAccessibilityEnabled,
                buttonText = "Aktifkan Layanan",
                testTag = "perm_accessibility_button",
                onAction = onOpenAccessibilitySettings
            )

            PermissionItemCard(
                icon = Icons.Rounded.Notifications,
                title = "4. Notifikasi Layanan",
                description = "Menjaga layanan kapsul tetap stabil di latar belakang tanpa dimatikan sistem.",
                isGranted = permissionState.hasPostNotifications,
                buttonText = "Izinkan",
                testTag = "perm_notif_button",
                onAction = onRequestNotifications
            )

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("perm_done_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaBlack,
                    contentColor = NovaWhite
                )
            ) {
                Text(
                    text = if (permissionState.allCapsulePermissionsReady) "Selesai & Aktifkan" else "Tutup",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PermissionItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    buttonText: String,
    testTag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NovaPaper)
            .border(1.dp, NovaHairline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isGranted) NovaSuccessContainer else NovaWhite)
                .border(1.dp, NovaHairline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (isGranted) NovaSuccess else NovaBlack,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = NovaBlack
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = NovaSlate
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        if (isGranted) {
            Text(
                text = "Aktif",
                style = MaterialTheme.typography.labelLarge,
                color = NovaSuccess
            )
        } else {
            Button(
                onClick = onAction,
                modifier = Modifier.testTag(testTag),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaBlack,
                    contentColor = NovaWhite
                )
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
