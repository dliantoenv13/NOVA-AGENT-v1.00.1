package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.NovaSettings
import com.example.data.local.ChatSessionEntity
import com.example.overlay.NovaPermissionState
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaHairline
import com.example.ui.theme.NovaMuted
import com.example.ui.theme.NovaPaper
import com.example.ui.theme.NovaSlate
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaWarning
import com.example.ui.theme.NovaWhite

@Composable
fun NovaDrawerContent(
    settings: NovaSettings,
    permissionState: NovaPermissionState,
    sessions: List<ChatSessionEntity>,
    activeSessionId: Long?,
    onNewChat: () -> Unit,
    onSelectSession: (Long) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onToggleCapsule: (Boolean) -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onToggleTts: (Boolean) -> Unit,
    onToggleLowEndMode: (Boolean) -> Unit,
    onSelectVoiceLang: (String) -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = NovaWhite,
        drawerContentColor = NovaBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Brand header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NovaBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(NovaWhite)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.nova_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = NovaBlack
                    )
                    Text(
                        text = stringResource(R.string.drawer_header_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("drawer_new_chat_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NovaBlack,
                    contentColor = NovaWhite
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.drawer_new_chat),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = NovaHairline)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.drawer_section_capsule),
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DrawerSettingSwitch(
                        icon = Icons.Rounded.Layers,
                        title = stringResource(R.string.drawer_toggle_capsule),
                        subtitle = "Muncul di kamera depan hanya saat di Home Screen",
                        checked = settings.isCapsuleEnabled,
                        onCheckedChange = onToggleCapsule,
                        testTag = "switch_capsule"
                    )
                    DrawerSettingSwitch(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.drawer_toggle_wakeword),
                        subtitle = "Ucapkan \"Hi Nova\" saat di Home Screen",
                        checked = settings.isWakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        testTag = "switch_wake_word"
                    )
                    DrawerSettingSwitch(
                        icon = Icons.Rounded.VolumeUp,
                        title = stringResource(R.string.drawer_toggle_tts),
                        subtitle = "Bacakan jawaban Gemini secara otomatis",
                        checked = settings.isTtsEnabled,
                        onCheckedChange = onToggleTts,
                        testTag = "switch_tts"
                    )
                    DrawerSettingSwitch(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.drawer_toggle_low_end),
                        subtitle = "Ringankan partikel atom untuk RAM kecil",
                        checked = settings.isLowEndModeEnabled,
                        onCheckedChange = onToggleLowEndMode,
                        testTag = "switch_low_end"
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bahasa Pengenal Suara",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaSlate,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = settings.voiceLanguageTag == "id-ID",
                            onClick = { onSelectVoiceLang("id-ID") },
                            label = { Text("Indonesia") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NovaBlack,
                                selectedLabelColor = NovaWhite
                            )
                        )
                        FilterChip(
                            selected = settings.voiceLanguageTag == "en-US",
                            onClick = { onSelectVoiceLang("en-US") },
                            label = { Text("English") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NovaBlack,
                                selectedLabelColor = NovaWhite
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = NovaHairline)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.drawer_section_permissions),
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val grantedCount = listOf(
                        permissionState.hasRecordAudio,
                        permissionState.hasOverlayPermission,
                        permissionState.hasAccessibilityEnabled,
                        permissionState.hasPostNotifications
                    ).count { it }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NovaPaper)
                            .border(1.dp, NovaHairline, RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenPermissions)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("drawer_permissions_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = if (permissionState.allCapsulePermissionsReady) NovaSuccess else NovaWarning
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.drawer_open_permissions),
                                style = MaterialTheme.typography.labelLarge,
                                color = NovaBlack
                            )
                            Text(
                                text = "$grantedCount dari 4 izin sistem aktif",
                                style = MaterialTheme.typography.bodySmall,
                                color = NovaSlate
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = NovaHairline)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.drawer_section_history),
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (sessions.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.drawer_empty_history),
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(sessions, key = { it.id }) { session ->
                        val isSelected = session.id == activeSessionId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NovaPaper else NovaWhite)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NovaBlack else NovaHairline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectSession(session.id) }
                                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = null,
                                tint = NovaBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = NovaBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Hapus percakapan",
                                    tint = NovaMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSettingSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NovaBlack,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = NovaBlack
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NovaSlate
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = NovaWhite,
                checkedTrackColor = NovaBlack,
                uncheckedThumbColor = NovaBlack,
                uncheckedTrackColor = NovaPaper,
                uncheckedBorderColor = NovaHairline
            )
        )
    }
}
