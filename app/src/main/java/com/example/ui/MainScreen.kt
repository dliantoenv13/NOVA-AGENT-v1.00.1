package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ChatRepository
import com.example.data.local.ChatMessageEntity
import com.example.ui.components.AtomParticleSphere
import com.example.ui.components.NovaDrawerContent
import com.example.ui.components.PermissionsGuideSheet
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaCharcoal
import com.example.ui.theme.NovaError
import com.example.ui.theme.NovaErrorContainer
import com.example.ui.theme.NovaHairline
import com.example.ui.theme.NovaMuted
import com.example.ui.theme.NovaPaper
import com.example.ui.theme.NovaSlate
import com.example.ui.theme.NovaSurfaceVariant
import com.example.ui.theme.NovaWarning
import com.example.ui.theme.NovaWhite
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: NovaViewModel,
    onRequestMicPermission: (onGranted: () -> Unit) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val assistantStatus by viewModel.assistantStatus.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val composerText by viewModel.composerText.collectAsStateWithLifecycle()
    val showPermissionsSheet by viewModel.showPermissionsSheet.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NovaDrawerContent(
                settings = settings,
                permissionState = permissionState,
                sessions = sessions,
                activeSessionId = activeSessionId,
                onNewChat = {
                    viewModel.startNewChat()
                    scope.launch { drawerState.close() }
                },
                onSelectSession = { id ->
                    viewModel.selectSession(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteSession = { id -> viewModel.deleteSession(id) },
                onToggleCapsule = { enabled -> viewModel.setCapsuleEnabled(enabled) },
                onToggleWakeWord = { enabled -> viewModel.setWakeWordEnabled(enabled) },
                onToggleTts = { enabled -> viewModel.setTtsEnabled(enabled) },
                onToggleLowEndMode = { enabled -> viewModel.setLowEndModeEnabled(enabled) },
                onSelectVoiceLang = { tag -> viewModel.setVoiceLanguageTag(tag) },
                onOpenPermissions = {
                    scope.launch { drawerState.close() }
                    viewModel.openPermissionsSheet()
                }
            )
        }
    ) {
        Scaffold(
            containerColor = NovaWhite,
            topBar = {
                NovaTopBar(
                    assistantStatus = assistantStatus,
                    hasMessages = messages.isNotEmpty(),
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onNewChat = { viewModel.startNewChat() }
                )
            },
            bottomBar = {
                NovaBottomComposerBar(
                    text = composerText,
                    isRecording = assistantStatus is AssistantStatus.Listening,
                    isThinking = assistantStatus is AssistantStatus.Thinking,
                    isSpeaking = assistantStatus is AssistantStatus.Speaking,
                    onTextChange = { viewModel.updateComposerText(it) },
                    onSend = { viewModel.sendTextMessage() },
                    onVoiceClick = {
                        if (permissionState.hasRecordAudio) {
                            viewModel.toggleVoiceRecording()
                        } else {
                            onRequestMicPermission {
                                viewModel.refreshPermissionsAndSyncService()
                                viewModel.toggleVoiceRecording()
                            }
                        }
                    },
                    onStopSpeaking = { viewModel.stopSpeaking() }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NovaWhite)
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                ) {
                    // Compact capsule permission reminder banner when capsule is on but permissions incomplete
                    AnimatedVisibility(
                        visible = settings.isCapsuleEnabled && !permissionState.allCapsulePermissionsReady
                    ) {
                        CapsuleSetupBanner(
                            onClick = { viewModel.openPermissionsSheet() },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // Transient error banner
                    val errorState = assistantStatus as? AssistantStatus.Error
                    AnimatedVisibility(
                        visible = errorState != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        if (errorState != null) {
                            ErrorBanner(
                                message = errorState.message,
                                onDismiss = { viewModel.clearError() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (messages.isEmpty()) {
                        // Hero Center Atom-Particle Sphere Stage
                        SphereHeroStage(
                            assistantStatus = assistantStatus,
                            audioLevel = audioLevel,
                            isLowEndMode = settings.isLowEndModeEnabled,
                            onPromptSelected = { viewModel.sendQuickPrompt(it) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    } else {
                        // Active Chat Stream + Mini Interactive Orb Header
                        CompactOrbStrip(
                            assistantStatus = assistantStatus,
                            audioLevel = audioLevel,
                            isLowEndMode = settings.isLowEndModeEnabled,
                            onStopSpeaking = { viewModel.stopSpeaking() }
                        )
                        HorizontalDivider(color = NovaHairline)
                        ChatMessageList(
                            messages = messages,
                            isThinking = assistantStatus is AssistantStatus.Thinking,
                            onSpeak = { viewModel.speakMessage(it) },
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Nova", text))
                                Toast.makeText(context, "Teks disalin", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showPermissionsSheet) {
        PermissionsGuideSheet(
            permissionState = permissionState,
            onRequestMic = {
                onRequestMicPermission { viewModel.refreshPermissionsAndSyncService() }
            },
            onRequestNotifications = onRequestNotificationPermission,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onDismiss = {
                viewModel.refreshPermissionsAndSyncService()
                viewModel.dismissPermissionsSheet()
            }
        )
    }
}

@Composable
private fun NovaTopBar(
    assistantStatus: AssistantStatus,
    hasMessages: Boolean,
    onOpenMenu: () -> Unit,
    onNewChat: () -> Unit
) {
    Surface(
        color = NovaWhite,
        contentColor = NovaBlack
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3-line hamburger menu on top bar
            IconButton(
                onClick = onOpenMenu,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("menu_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.menu_button_desc),
                    tint = NovaBlack
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = stringResource(R.string.nova_title),
                style = MaterialTheme.typography.headlineMedium,
                color = NovaBlack
            )

            Spacer(modifier = Modifier.width(10.dp))

            val statusLabel = when (assistantStatus) {
                is AssistantStatus.Idle -> stringResource(R.string.status_ready)
                is AssistantStatus.Listening -> stringResource(R.string.status_listening)
                is AssistantStatus.Thinking -> stringResource(R.string.status_thinking)
                is AssistantStatus.Speaking -> stringResource(R.string.status_speaking)
                is AssistantStatus.Error -> "Kendala"
            }

            val isActive = assistantStatus !is AssistantStatus.Idle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) NovaBlack else NovaPaper)
                    .border(
                        width = 1.dp,
                        color = if (isActive) NovaBlack else NovaHairline,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isActive) NovaWhite else NovaSlate
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (hasMessages) {
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.new_chat_desc),
                        tint = NovaBlack
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleSetupBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NovaPaper)
            .border(1.dp, NovaHairline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("capsule_setup_banner"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Security,
            contentDescription = null,
            tint = NovaWarning,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Aktifkan izin Kapsul Home Screen & \"Hi Nova\"",
            style = MaterialTheme.typography.labelLarge,
            color = NovaBlack,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Atur",
            style = MaterialTheme.typography.labelLarge,
            color = NovaBlack
        )
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NovaErrorContainer)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = NovaError,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Tutup",
            style = MaterialTheme.typography.labelMedium,
            color = NovaError
        )
    }
}

@Composable
private fun SphereHeroStage(
    assistantStatus: AssistantStatus,
    audioLevel: Float,
    isLowEndMode: Boolean,
    onPromptSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.6f))

        AtomParticleSphere(
            isListening = assistantStatus is AssistantStatus.Listening,
            isThinking = assistantStatus is AssistantStatus.Thinking,
            isSpeaking = assistantStatus is AssistantStatus.Speaking,
            audioLevel = audioLevel,
            isLowEndMode = isLowEndMode,
            compactMode = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f, fill = false)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val listeningState = assistantStatus as? AssistantStatus.Listening
        if (listeningState != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(NovaBlack)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = listeningState.partialTranscript.ifBlank { "Mendengarkan suara Anda…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaWhite,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Text(
                text = stringResource(R.string.orb_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = NovaBlack,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.orb_hint_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = NovaSlate,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        val starterPrompts = listOf(
            "Bantu susun jadwal produktif hari ini",
            "Jelaskan atom & partikel secara sederhana",
            "Write a concise English email"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            starterPrompts.forEach { prompt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(NovaPaper)
                        .border(1.dp, NovaHairline, RoundedCornerShape(999.dp))
                        .clickable { onPromptSelected(prompt) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaBlack
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.8f))
    }
}

@Composable
private fun CompactOrbStrip(
    assistantStatus: AssistantStatus,
    audioLevel: Float,
    isLowEndMode: Boolean,
    onStopSpeaking: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NovaWhite)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AtomParticleSphere(
            isListening = assistantStatus is AssistantStatus.Listening,
            isThinking = assistantStatus is AssistantStatus.Thinking,
            isSpeaking = assistantStatus is AssistantStatus.Speaking,
            audioLevel = audioLevel,
            isLowEndMode = isLowEndMode,
            compactMode = true
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            val subtitle = when (assistantStatus) {
                is AssistantStatus.Listening ->
                    assistantStatus.partialTranscript.ifBlank { "Mendengarkan suara Anda…" }
                is AssistantStatus.Thinking -> "Nova sedang menyusun jawaban…"
                is AssistantStatus.Speaking -> "Nova sedang berbicara…"
                else -> "Sentuh bola mini untuk efek atom"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = NovaSlate,
                maxLines = 1
            )
        }
        if (assistantStatus is AssistantStatus.Speaking) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(NovaBlack)
                    .clickable(onClick = onStopSpeaking)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Hentikan",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaWhite
                )
            }
        }
    }
}

@Composable
private fun ChatMessageList(
    messages: List<ChatMessageEntity>,
    isThinking: Boolean,
    onSpeak: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { msg ->
            if (msg.role == ChatRepository.ROLE_USER) {
                UserMessageBubble(message = msg)
            } else {
                NovaMessageBubble(
                    message = msg,
                    isStreaming = isThinking && msg.id == messages.lastOrNull()?.id && msg.content.isBlank(),
                    onSpeak = { onSpeak(msg.content) },
                    onCopy = { onCopy(msg.content) }
                )
            }
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessageEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(NovaBlack)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (message.isVoiceMessage) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = NovaHairline,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Suara",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaHairline
                    )
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = NovaWhite
            )
        }
    }
}

@Composable
private fun NovaMessageBubble(
    message: ChatMessageEntity,
    isStreaming: Boolean,
    onSpeak: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
                .background(if (message.isError) NovaErrorContainer else NovaPaper)
                .border(
                    width = 1.dp,
                    color = if (message.isError) NovaError.copy(alpha = 0.35f) else NovaHairline,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Nova",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (message.isError) NovaError else NovaBlack,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (isStreaming) {
                Text(
                    text = "Sedang mengetik…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaMuted
                )
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isError) NovaError else NovaCharcoal
                )
            }

            if (!isStreaming && message.content.isNotBlank() && !message.isError) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = stringResource(R.string.speak_reply_desc),
                            tint = NovaSlate,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.copy_reply_desc),
                            tint = NovaSlate,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovaBottomComposerBar(
    text: String,
    isRecording: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    onStopSpeaking: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    val micBgColor by animateColorAsState(
        targetValue = if (isRecording) NovaBlack else NovaSurfaceVariant,
        label = "mic_bg"
    )
    val micTintColor by animateColorAsState(
        targetValue = if (isRecording) NovaWhite else NovaBlack,
        label = "mic_tint"
    )

    Surface(
        color = NovaWhite,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(NovaPaper)
                    .border(1.dp, NovaHairline, RoundedCornerShape(28.dp))
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (text.isEmpty()) {
                        Text(
                            text = if (isRecording) "Mendengarkan suara…" else stringResource(R.string.input_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = NovaMuted
                        )
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = NovaBlack),
                        cursorBrush = SolidColor(NovaBlack),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { onSend() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_input")
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Voice recording button (also stops speaking if tapped during TTS)
                IconButton(
                    onClick = {
                        if (isSpeaking) onStopSpeaking() else onVoiceClick()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(micBgColor)
                        .testTag("voice_record_button")
                ) {
                    Icon(
                        imageVector = if (isRecording || isSpeaking) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = if (isRecording) {
                            stringResource(R.string.stop_voice_desc)
                        } else {
                            stringResource(R.string.record_voice_desc)
                        },
                        tint = micTintColor,
                        modifier = Modifier.size(21.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                val canSend = text.isNotBlank() && !isThinking
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSend) NovaBlack else NovaHairline)
                        .testTag("send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.send_message_desc),
                        tint = if (canSend) NovaWhite else NovaMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
