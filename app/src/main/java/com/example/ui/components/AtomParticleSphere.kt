package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.ui.theme.NovaBlack
import com.example.ui.theme.NovaCharcoal
import com.example.ui.theme.NovaHairline
import com.example.ui.theme.NovaMuted
import com.example.ui.theme.NovaSlate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private class AtomParticle(
    val homeAngle: Float,
    val homeRadiusRatio: Float,
    val baseSizeDp: Float,
    val orbitSpeed: Float,
    val shadeIndex: Int,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var velX: Float = 0f,
    var velY: Float = 0f
)

@Composable
fun AtomParticleSphere(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    audioLevel: Float,
    isLowEndMode: Boolean,
    compactMode: Boolean = false,
    onSphereTapped: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val particleCount = if (compactMode) {
        if (isLowEndMode) 14 else 28
    } else {
        if (isLowEndMode) 28 else 72
    }

    val particles = remember(particleCount) {
        val rng = Random(42)
        List(particleCount) { idx ->
            AtomParticle(
                homeAngle = (idx.toFloat() / particleCount) * (2f * PI.toFloat()) + rng.nextFloat() * 0.4f,
                homeRadiusRatio = 0.22f + rng.nextFloat() * 0.78f,
                baseSizeDp = if (compactMode) 1.8f + rng.nextFloat() * 2.2f else 2.2f + rng.nextFloat() * 4.0f,
                orbitSpeed = (if (idx % 2 == 0) 1f else -1f) * (0.4f + rng.nextFloat() * 0.9f),
                shadeIndex = idx % 3
            )
        }
    }

    var isPointerHolding by remember { mutableStateOf(false) }
    var shatterEnergy by remember { mutableFloatStateOf(0f) }
    var shockwaveProgress by remember { mutableFloatStateOf(1f) }
    var touchOriginNormalized by remember { mutableStateOf(Offset.Zero) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    val targetCoreScale = when {
        isPointerHolding -> 0.52f
        shatterEnergy > 0.35f -> 0.68f
        isListening -> 1.06f + (audioLevel * 0.14f)
        isSpeaking -> 1.05f + (audioLevel * 0.12f)
        isThinking -> 0.96f
        else -> 1f
    }

    val coreScale by animateFloatAsState(
        targetValue = targetCoreScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "core_scale"
    )

    // Physics & orbital animation loop
    LaunchedEffect(particleCount, isLowEndMode) {
        lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos == 0L) {
                    0.016f
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                }
                lastFrameNanos = frameNanos
                timeSeconds += dt

                if (shockwaveProgress < 1f) {
                    shockwaveProgress = (shockwaveProgress + dt * 1.9f).coerceAtMost(1f)
                }

                if (!isPointerHolding && shatterEnergy > 0f) {
                    shatterEnergy = (shatterEnergy - dt * 0.85f).coerceAtLeast(0f)
                }

                // Update particle spring physics
                val burstFactor = if (isPointerHolding) 1f else shatterEnergy
                particles.forEach { p ->
                    val targetSpread = burstFactor * (0.9f + p.homeRadiusRatio * 0.85f)
                    val angle = p.homeAngle + timeSeconds * p.orbitSpeed * (1f + burstFactor * 1.4f)
                    val targetX = cos(angle) * targetSpread + touchOriginNormalized.x * burstFactor * 0.35f
                    val targetY = sin(angle) * targetSpread + touchOriginNormalized.y * burstFactor * 0.35f

                    val stiffness = if (isPointerHolding || burstFactor > 0.45f) 14f else 18f
                    val damping = 0.84f
                    p.velX = (p.velX + (targetX - p.offsetX) * stiffness * dt) * damping
                    p.velY = (p.velY + (targetY - p.offsetY) * stiffness * dt) * damping
                    p.offsetX += p.velX
                    p.offsetY += p.velY
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val targetSize: Dp = if (compactMode) {
            52.dp
        } else {
            min(maxWidth, maxHeight).coerceIn(190.dp, 310.dp)
        }

        Canvas(
            modifier = Modifier
                .size(targetSize)
                .testTag(if (compactMode) "mini_atom_sphere" else "atom_particle_sphere")
                .semantics {
                    contentDescription = "Bola partikel atom Nova, sentuh untuk memecah partikel"
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = (size.width.coerceAtMost(size.height) / 2f).coerceAtLeast(1f)
                        val normX = ((down.position.x - center.x) / maxR).coerceIn(-1f, 1f)
                        val normY = ((down.position.y - center.y) / maxR).coerceIn(-1f, 1f)

                        isPointerHolding = true
                        touchOriginNormalized = Offset(normX, normY)
                        shatterEnergy = 1f
                        shockwaveProgress = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        // Impulse burst outward on touch
                        particles.forEachIndexed { i, p ->
                            val angle = p.homeAngle + (i % 5) * 0.25f
                            val speed = 0.08f + p.homeRadiusRatio * 0.18f
                            p.velX += cos(angle) * speed + normX * 0.04f
                            p.velY += sin(angle) * speed + normY * 0.04f
                        }

                        var movedDistance = 0f
                        var prevPos = down.position
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            val delta = change.position - prevPos
                            movedDistance += sqrt(delta.x * delta.x + delta.y * delta.y)
                            prevPos = change.position
                            touchOriginNormalized = Offset(
                                ((change.position.x - center.x) / maxR).coerceIn(-1f, 1f),
                                ((change.position.y - center.y) / maxR).coerceIn(-1f, 1f)
                            )
                        } while (event.changes.any { it.pressed })

                        isPointerHolding = false
                        if (movedDistance < 28f) {
                            onSphereTapped()
                        }
                    }
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val canvasRadius = size.minDimension / 2f
            val baseSphereRadius = canvasRadius * (if (compactMode) 0.55f else 0.44f)
            val currentSphereRadius = baseSphereRadius * coreScale

            // 1. Subtle monochrome ambient ring / audio-reactive halo
            val audioBoost = if (isListening || isSpeaking) audioLevel * (canvasRadius * 0.18f) else 0f
            val breath = sin(timeSeconds * 2.2f) * (canvasRadius * 0.015f)
            drawCircle(
                color = NovaHairline.copy(alpha = if (isListening || isSpeaking) 0.9f else 0.65f),
                radius = currentSphereRadius * 1.28f + audioBoost + breath,
                center = center,
                style = Stroke(width = if (compactMode) 1.dp.toPx() else 1.4.dp.toPx())
            )

            if (!compactMode && (isListening || isSpeaking || isThinking)) {
                drawCircle(
                    color = NovaMuted.copy(alpha = 0.35f),
                    radius = currentSphereRadius * 1.52f + audioBoost * 1.35f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 2. Touch shockwave ring
            if (shockwaveProgress < 1f) {
                val waveRadius = currentSphereRadius + (canvasRadius * 0.65f * shockwaveProgress)
                val waveAlpha = (1f - shockwaveProgress) * 0.55f
                drawCircle(
                    color = NovaBlack.copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = (2.5f * (1f - shockwaveProgress)).dp.toPx())
                )
            }

            // 3. Central high-contrast 3D obsidian sphere (fades slightly while shattered)
            val coreAlpha = (1f - shatterEnergy * 0.58f).coerceIn(0.28f, 1f)
            val highlightOffset = Offset(
                center.x - currentSphereRadius * 0.32f,
                center.y - currentSphereRadius * 0.32f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4A4A54).copy(alpha = coreAlpha),
                        NovaCharcoal.copy(alpha = coreAlpha),
                        NovaBlack.copy(alpha = coreAlpha)
                    ),
                    center = highlightOffset,
                    radius = (currentSphereRadius * 1.65f).coerceAtLeast(1f)
                ),
                radius = currentSphereRadius,
                center = center
            )

            // Crisp specular rim on sphere
            if (!compactMode) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f * coreAlpha),
                    radius = currentSphereRadius * 0.96f,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 4. Atomic particles & quantum electron fragments
            val spreadLimit = canvasRadius * 0.92f
            val particleColors = arrayOf(NovaBlack, NovaCharcoal, NovaSlate)
            val burstVisibility = (0.18f + shatterEnergy * 0.82f).coerceIn(0.18f, 1f)

            particles.forEachIndexed { idx, p ->
                val orbitalR = currentSphereRadius * (1.06f + p.homeRadiusRatio * 0.22f)
                val idleAngle = p.homeAngle + timeSeconds * p.orbitSpeed * 0.55f
                val idleX = cos(idleAngle) * orbitalR
                val idleY = sin(idleAngle) * orbitalR

                val px = center.x + idleX * (1f - shatterEnergy * 0.6f) + p.offsetX * spreadLimit
                val py = center.y + idleY * (1f - shatterEnergy * 0.6f) + p.offsetY * spreadLimit
                val particlePos = Offset(px, py)
                val dotRadius = p.baseSizeDp.dp.toPx() * (0.85f + shatterEnergy * 0.45f)

                // Subtle connecting quantum bond line for select particles when shattered
                if (!isLowEndMode && !compactMode && shatterEnergy > 0.25f && idx % 4 == 0) {
                    drawLine(
                        color = NovaSlate.copy(alpha = shatterEnergy * 0.24f),
                        start = center,
                        end = particlePos,
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                drawCircle(
                    color = particleColors[p.shadeIndex].copy(
                        alpha = if (shatterEnergy > 0.1f) burstVisibility else 0.45f
                    ),
                    radius = dotRadius,
                    center = particlePos
                )

                // Outer electron ring around larger atom particles when shattered
                if (!isLowEndMode && !compactMode && shatterEnergy > 0.35f && idx % 6 == 0) {
                    drawCircle(
                        color = NovaBlack.copy(alpha = shatterEnergy * 0.35f),
                        radius = dotRadius * 2.1f,
                        center = particlePos,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                }
            }
        }
    }
}
