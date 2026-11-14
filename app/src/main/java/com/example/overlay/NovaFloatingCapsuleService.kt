package com.example.overlay

import android.Manifest
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.NovaApplication
import com.example.R
import com.example.data.NovaSettings
import com.example.voice.SpeechMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "NovaCapsuleService"
private const val NOTIFICATION_ID = 4091
private const val CHANNEL_ID = "nova_capsule_channel"

sealed interface CapsuleVisualState {
    data object Idle : CapsuleVisualState
    data object Listening : CapsuleVisualState
    data object Thinking : CapsuleVisualState
    data object Speaking : CapsuleVisualState
    data class ErrorNotice(val shortLabel: String) : CapsuleVisualState
}

class NovaFloatingCapsuleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val container by lazy { (application as NovaApplication).container }
    private val speechManager by lazy { container.speechManager }

    private var windowManager: WindowManager? = null
    private var capsuleRoot: LinearLayout? = null
    private var capsuleIcon: ImageView? = null
    private var capsuleLabel: TextView? = null
    private var statusDot: View? = null
    private val waveBars = mutableListOf<View>()

    private var waveAnimator: ValueAnimator? = null
    private var iconSpinAnimator: ValueAnimator? = null
    private var isForegroundStarted = false
    private var activeVoiceJob: Job? = null

    private val visualState = MutableStateFlow<CapsuleVisualState>(CapsuleVisualState.Idle)
    private var currentSettings = NovaSettings()
    private var currentlyOnHome = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    container.homeVisibilityMonitor.onScreenLockedOrOff()
                    speechManager.stopListening()
                    container.ttsManager.stop()
                }
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_SCREEN_ON -> {
                    container.homeVisibilityMonitor.refreshPermissions()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }

        observeHomeAndSettings()
        observeVisualState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_CAPSULE) {
            serviceScope.launch {
                container.settingsRepository.setCapsuleEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (!ensureForegroundStarted()) {
            stopSelf()
            return START_NOT_STICKY
        }

        container.homeVisibilityMonitor.refreshPermissions()
        return START_STICKY
    }

    private fun ensureForegroundStarted(): Boolean {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            Log.w(TAG, "Cannot start microphone foreground service without RECORD_AUDIO permission")
            return false
        }

        if (!isForegroundStarted) {
            try {
                val notification = buildNotification(wakeWordActive = currentSettings.isWakeWordEnabled)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
                return false
            }
        }
        return true
    }

    private fun observeHomeAndSettings() {
        serviceScope.launch {
            combine(
                container.homeVisibilityMonitor.isOnHomeScreen,
                container.settingsRepository.settings,
                container.ttsManager.isSpeaking
            ) { onHome, settings, isSpeaking ->
                Triple(onHome, settings, isSpeaking)
            }.collectLatest { (onHome, settings, isSpeaking) ->
                currentSettings = settings
                currentlyOnHome = onHome

                if (!settings.isCapsuleEnabled) {
                    hideCapsuleView()
                    speechManager.stopListening()
                    stopSelf()
                    return@collectLatest
                }

                val canOverlay = Settings.canDrawOverlays(this@NovaFloatingCapsuleService)
                if (onHome && canOverlay) {
                    showCapsuleView()
                    if (!isSpeaking &&
                        visualState.value == CapsuleVisualState.Idle &&
                        settings.isWakeWordEnabled &&
                        speechManager.hasRecordPermission()
                    ) {
                        startWakeWordStandby(settings.voiceLanguageTag)
                    } else if (!settings.isWakeWordEnabled &&
                        (speechManager.mode.value == SpeechMode.WakeWordVadStandby ||
                            speechManager.mode.value == SpeechMode.WakeWordSpeechVerify)
                    ) {
                        speechManager.stopListening()
                    }
                } else {
                    // Strictly hide popup & stop passive listening outside Home Screen
                    hideCapsuleView()
                    if (speechManager.mode.value == SpeechMode.WakeWordVadStandby ||
                        speechManager.mode.value == SpeechMode.WakeWordSpeechVerify
                    ) {
                        speechManager.stopListening()
                    }
                    if (!onHome && visualState.value == CapsuleVisualState.Listening) {
                        visualState.value = CapsuleVisualState.Idle
                    }
                }
            }
        }
    }

    private fun observeVisualState() {
        serviceScope.launch {
            visualState.collectLatest { state ->
                updateCapsuleAppearance(state)
            }
        }
    }

    private fun startWakeWordStandby(languageTag: String) {
        if (!currentlyOnHome || !currentSettings.isWakeWordEnabled) return
        speechManager.startWakeWordStandby(
            languageTag = languageTag,
            onWakeWord = { commandAfterWake ->
                if (!currentlyOnHome) return@startWakeWordStandby
                if (commandAfterWake.isNotBlank()) {
                    handleCapturedVoicePrompt(commandAfterWake)
                } else {
                    triggerCommandCapture()
                }
            },
            onError = { err ->
                Log.w(TAG, "Wake word standby error: $err")
            }
        )
    }

    private fun triggerCommandCapture() {
        if (!currentlyOnHome) return
        container.ttsManager.stop()
        visualState.value = CapsuleVisualState.Listening
        animateCapsulePulse(expand = true)

        speechManager.startCommandListening(
            languageTag = currentSettings.voiceLanguageTag,
            onPartial = { partial ->
                capsuleLabel?.text = partial.take(24)
            },
            onResult = { spokenText ->
                handleCapturedVoicePrompt(spokenText)
            },
            onError = { _ ->
                showTransientErrorAndReset("Coba lagi")
            }
        )
    }

    private fun handleCapturedVoicePrompt(prompt: String) {
        activeVoiceJob?.cancel()
        activeVoiceJob = serviceScope.launch {
            visualState.value = CapsuleVisualState.Thinking
            animateCapsulePulse(expand = true)

            val sessionId = container.chatRepository.getOrCreateDefaultSession(
                currentSettings.activeSessionId
            )
            container.settingsRepository.setActiveSessionId(sessionId)
            val history = container.chatRepository.getRecentMessages(sessionId)
            container.chatRepository.addUserMessage(
                sessionId = sessionId,
                text = prompt,
                isVoiceMessage = true
            )
            val novaMsgId = container.chatRepository.insertNovaPlaceholder(
                sessionId = sessionId,
                isVoiceMessage = true
            )

            try {
                val reply = container.aiRepository.generateVoiceBackgroundReply(history, prompt)
                container.chatRepository.updateNovaMessage(novaMsgId, reply, isError = false)

                visualState.value = CapsuleVisualState.Speaking
                if (currentSettings.isTtsEnabled) {
                    container.ttsManager.speak(reply) {
                        serviceScope.launch {
                            resetCapsuleToIdle()
                        }
                    }
                } else {
                    resetCapsuleToIdle()
                }
            } catch (e: TimeoutCancellationException) {
                val msg = "Koneksi ke Nova melebihi batas waktu."
                container.chatRepository.updateNovaMessage(novaMsgId, msg, isError = true)
                speakFallbackOrReset("Koneksi timeout", msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Background AI reply failed", e)
                val detail = e.localizedMessage ?: "Gagal terhubung ke Nova."
                container.chatRepository.updateNovaMessage(novaMsgId, detail, isError = true)
                speakFallbackOrReset("Firebase belum siap", "Maaf, Nova gagal memproses permintaan.")
            }
        }
    }

    private fun speakFallbackOrReset(shortLabel: String, spokenMessage: String) {
        if (currentSettings.isTtsEnabled) {
            visualState.value = CapsuleVisualState.ErrorNotice(shortLabel)
            container.ttsManager.speak(spokenMessage) {
                serviceScope.launch {
                    resetCapsuleToIdle()
                }
            }
        } else {
            showTransientErrorAndReset(shortLabel)
        }
    }

    private fun showTransientErrorAndReset(shortLabel: String) {
        serviceScope.launch {
            visualState.value = CapsuleVisualState.ErrorNotice(shortLabel)
            delay(2000L)
            resetCapsuleToIdle()
        }
    }

    private fun resetCapsuleToIdle() {
        visualState.value = CapsuleVisualState.Idle
        animateCapsulePulse(expand = false)
        if (currentlyOnHome && currentSettings.isWakeWordEnabled) {
            startWakeWordStandby(currentSettings.voiceLanguageTag)
        }
    }

    private fun showCapsuleView() {
        if (capsuleRoot != null) {
            capsuleRoot?.visibility = View.VISIBLE
            return
        }
        val root = buildCapsuleView()
        capsuleRoot = root

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager?.addView(root, params)
            root.alpha = 0f
            root.scaleX = 0.85f
            root.scaleY = 0.85f
            root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to add floating capsule overlay", e)
            capsuleRoot = null
        }
    }

    private fun hideCapsuleView() {
        capsuleRoot?.visibility = View.GONE
        stopWaveAnimation()
    }

    private fun removeCapsuleView() {
        stopWaveAnimation()
        capsuleRoot?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing capsule view", e)
            }
        }
        capsuleRoot = null
        waveBars.clear()
    }

    private fun buildCapsuleView(): LinearLayout {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(999).toFloat()
            setColor(Color.parseColor("#0A0A0C"))
            setStroke(dp(1), Color.parseColor("#2E2E38"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            this.background = background
            elevation = dp(10).toFloat()
            setPadding(dp(14), dp(8), dp(14), dp(8))
            minimumHeight = dp(38)
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_nova_capsule_logo)
            setColorFilter(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply {
                marginEnd = dp(8)
            }
        }
        capsuleIcon = icon

        val label = TextView(this).apply {
            text = getString(R.string.capsule_tap_to_talk)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(175)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(8)
            }
        }
        capsuleLabel = label

        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#FFFFFF"))
        }
        val dot = View(this).apply {
            this.background = dotDrawable
            layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                marginEnd = dp(4)
            }
        }
        statusDot = dot

        val waveContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        waveBars.clear()
        repeat(4) { index ->
            val barDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(Color.WHITE)
            }
            val bar = View(this).apply {
                this.background = barDrawable
                layoutParams = LinearLayout.LayoutParams(dp(3), dp(10)).apply {
                    if (index < 3) marginEnd = dp(3)
                }
                visibility = View.GONE
            }
            waveBars.add(bar)
            waveContainer.addView(bar)
        }

        root.addView(icon)
        root.addView(label)
        root.addView(dot)
        root.addView(waveContainer)

        attachTouchHandler(root)
        return root
    }

    private fun attachTouchHandler(target: View) {
        var pressStartTime = 0L
        var holdTriggered = false
        val holdCheckRunnable = Runnable {
            holdTriggered = true
            if (visualState.value == CapsuleVisualState.Idle) {
                triggerCommandCapture()
            }
        }

        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressStartTime = System.currentTimeMillis()
                    holdTriggered = false
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                    view.postDelayed(holdCheckRunnable, 260L)
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(holdCheckRunnable)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()

                    val elapsed = System.currentTimeMillis() - pressStartTime
                    if (holdTriggered || elapsed >= 260L) {
                        if (visualState.value == CapsuleVisualState.Listening) {
                            speechManager.stopListening()
                        }
                    } else {
                        when (visualState.value) {
                            CapsuleVisualState.Speaking -> {
                                container.ttsManager.stop()
                                resetCapsuleToIdle()
                            }
                            CapsuleVisualState.Listening -> {
                                speechManager.stopListening()
                                resetCapsuleToIdle()
                            }
                            CapsuleVisualState.Thinking,
                            is CapsuleVisualState.ErrorNotice -> Unit
                            CapsuleVisualState.Idle -> triggerCommandCapture()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun updateCapsuleAppearance(state: CapsuleVisualState) {
        when (state) {
            CapsuleVisualState.Idle -> {
                capsuleLabel?.text = getString(R.string.capsule_tap_to_talk)
                statusDot?.visibility = if (currentSettings.isWakeWordEnabled) View.VISIBLE else View.GONE
                waveBars.forEach { it.visibility = View.GONE }
                stopWaveAnimation()
            }
            CapsuleVisualState.Listening -> {
                capsuleLabel?.text = getString(R.string.capsule_listening)
                statusDot?.visibility = View.GONE
                waveBars.forEach { it.visibility = View.VISIBLE }
                startWaveAnimation(speedMs = 340L)
            }
            CapsuleVisualState.Thinking -> {
                capsuleLabel?.text = getString(R.string.capsule_thinking)
                statusDot?.visibility = View.GONE
                waveBars.forEach { it.visibility = View.VISIBLE }
                startWaveAnimation(speedMs = 560L)
            }
            CapsuleVisualState.Speaking -> {
                capsuleLabel?.text = getString(R.string.capsule_speaking)
                statusDot?.visibility = View.GONE
                waveBars.forEach { it.visibility = View.VISIBLE }
                startWaveAnimation(speedMs = 280L)
            }
            is CapsuleVisualState.ErrorNotice -> {
                capsuleLabel?.text = state.shortLabel
                statusDot?.visibility = View.GONE
                waveBars.forEach { it.visibility = View.GONE }
                stopWaveAnimation()
            }
        }
    }

    private fun animateCapsulePulse(expand: Boolean) {
        val root = capsuleRoot ?: return
        val targetScale = if (expand) 1.04f else 1.0f
        root.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(200L)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    private fun startWaveAnimation(speedMs: Long) {
        stopWaveAnimation()
        waveAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = speedMs
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val phase = anim.animatedValue as Float
                waveBars.forEachIndexed { idx, bar ->
                    val factor = (kotlin.math.sin(phase + idx * 1.1) + 1.0) / 2.0
                    val lp = bar.layoutParams
                    lp.height = dp(5) + (dp(11) * factor).toInt()
                    bar.layoutParams = lp
                }
            }
            start()
        }
        iconSpinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                capsuleIcon?.rotation = anim.animatedValue as Float
            }
            start()
        }
    }

    private fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
        iconSpinAnimator?.cancel()
        iconSpinAnimator = null
        capsuleIcon?.rotation = 0f
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(wakeWordActive: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, NovaFloatingCapsuleService::class.java).apply {
                action = ACTION_STOP_CAPSULE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (wakeWordActive) {
            getString(R.string.notification_text_wakeword)
        } else {
            getString(R.string.notification_text_paused)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nova_capsule_logo)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        activeVoiceJob?.cancel()
        if (speechManager.mode.value == SpeechMode.WakeWordVadStandby ||
            speechManager.mode.value == SpeechMode.WakeWordSpeechVerify
        ) {
            speechManager.stopListening()
        }
        removeCapsuleView()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_CAPSULE = "com.example.overlay.ACTION_STOP_CAPSULE"

        fun startIfPermitted(context: Context) {
            val hasMic = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic || !Settings.canDrawOverlays(context)) return

            val intent = Intent(context, NovaFloatingCapsuleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NovaFloatingCapsuleService::class.java))
        }
    }
}
