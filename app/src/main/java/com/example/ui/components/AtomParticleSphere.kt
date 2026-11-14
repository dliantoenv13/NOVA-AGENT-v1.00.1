package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private enum class MorphShape {
    SPIRAL_NEBULA,
    BUTTERFLY_RIBBON,
    SCATTER_CONSTELLATION,
    TOROIDAL_WAVE,
    QUANTUM_GALAXY
}

private class MorphParticle(
    // Base 3D Fibonacci sphere coordinates (normalized radius ~0.42..1.0)
    val baseX: Float,
    val baseY: Float,
    val baseZ: Float,
    val sizeDp: Float,
    val phase: Float,
    val shadeIndex: Int,
    // Current smoothed normalized canvas offsets (-1..1)
    var curX: Float,
    var curY: Float,
    var curDepth: Float,
    // Random morph target offsets (-1..1)
    var morphTargetX: Float = 0f,
    var morphTargetY: Float = 0f,
    var morphTargetDepth: Float = 0f,
    // Secondary ripple velocity for silky pointer interaction
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
    val particleCount = when {
        compactMode && isLowEndMode -> 24
        compactMode -> 48
        isLowEndMode -> 72
        else -> 180
    }

    val particles = remember(particleCount, compactMode) {
        val rng = Random(2026)
        val goldenAngle = (PI * (3.0 - sqrt(5.0))).toFloat()
        List(particleCount) { idx ->
            // Distribute points cleanly across outer ring + volumetric 3D sphere shell
            val t = (idx + 0.5f) / particleCount.toFloat()
            val y = 1f - 2f * t
            val rSlice = sqrt((1f - y * y).coerceAtLeast(0f))
            val theta = goldenAngle * idx
            // Mix dense orbital shell (75%) with inner core particles (25%)
            val radialScale = if (idx % 4 == 0) {
                0.32f + rng.nextFloat() * 0.45f
            } else {
                0.78f + rng.nextFloat() * 0.24f
            }
            val bx = cos(theta) * rSlice * radialScale
            val by = y * radialScale
            val bz = sin(theta) * rSlice * radialScale

            val sizeDp = if (compactMode) {
                1.1f + rng.nextFloat() * 1.4f
            } else {
                1.3f + rng.nextFloat() * 2.5f
            }

            MorphParticle(
                baseX = bx,
                baseY = by,
                baseZ = bz,
                sizeDp = sizeDp,
                phase = rng.nextFloat() * (2f * PI.toFloat()),
                shadeIndex = idx % 3,
                curX = bx * 0.48f,
                curY = by * 0.48f,
                curDepth = bz
            )
        }
    }

    var isPointerHolding by remember { mutableStateOf(false) }
    var morphBlend by remember { mutableFloatStateOf(0f) } // 0f = sphere, 1f = random morphed shape
    var holdSustainTimer by remember { mutableFloatStateOf(0f) }
    var touchOriginNormalized by remember { mutableStateOf(Offset.Zero) }
    var touchPulse by remember { mutableFloatStateOf(0f) }
    var morphSeed by remember { mutableIntStateOf(0) }
    var timeSeconds by remember { mutableFloatStateOf(0f) }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    val targetScale = when {
        isListening -> 1.07f + audioLevel * 0.14f
        isSpeaking -> 1.06f + audioLevel * 0.12f
        isThinking -> 0.97f
        else -> 1f
    }

    val audioScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sphere_audio_scale"
    )

    // Assign a fresh random organic formation whenever morphSeed increments
    fun generateRandomMorphFormation(seed: Int, touchNorm: Offset) {
        val rng = Random(seed * 9973 + 131)
        val shapes = MorphShape.entries
        val chosenShape = shapes[rng.nextInt(shapes.size)]
        val rotationOffset = rng.nextFloat() * (2f * PI.toFloat())
        val aspectTilt = 0.65f + rng.nextFloat() * 0.5f
        val count = particles.size.coerceAtLeast(1)

        particles.forEachIndexed { idx, p ->
            val u = idx.toFloat() / count.toFloat()
            val angle = u * 2f * PI.toFloat() + rotationOffset
            val jitterR = 0.06f + rng.nextFloat() * 0.14f
            val jitterAngle = rng.nextFloat() * 2f * PI.toFloat()

            var tx = 0f
            var ty = 0f
            var tz = 0f

            when (chosenShape) {
                MorphShape.SPIRAL_NEBULA -> {
                    val arm = (idx % 3) * (2f * PI.toFloat() / 3f)
                    val radius = 0.18f + sqrt(u) * 0.78f
                    val spiralTheta = radius * 5.2f + arm + rotationOffset
                    tx = cos(spiralTheta) * radius
                    ty = sin(spiralTheta) * radius * aspectTilt
                    tz = (rng.nextFloat() - 0.5f) * 0.8f
                }

                MorphShape.BUTTERFLY_RIBBON -> {
                    val t = angle * 2f
                    val r = 0.42f + 0.38f * sin(t * 1.5f)
                    tx = cos(t) * r * 1.05f
                    ty = sin(t * 2f) * 0.62f + cos(t) * 0.18f
                    tz = sin(t) * 0.7f
                }

                MorphShape.SCATTER_CONSTELLATION -> {
                    val clusterIdx = idx % 5
                    val clusterAngle = clusterIdx * (2f * PI.toFloat() / 5f) + rotationOffset
                    val clusterDist = 0.34f + (clusterIdx % 2) * 0.28f
                    val cx = cos(clusterAngle) * clusterDist
                    val cy = sin(clusterAngle) * clusterDist
                    val localR = 0.08f + rng.nextFloat() * 0.28f
                    val localA = rng.nextFloat() * 2f * PI.toFloat()
                    tx = cx + cos(localA) * localR
                    ty = cy + sin(localA) * localR
                    tz = (rng.nextFloat() - 0.5f) * 0.9f
                }

                MorphShape.TOROIDAL_WAVE -> {
                    val majorR = 0.62f
                    val minorR = 0.24f
                    val phi = u * 2f * PI.toFloat() * 3f + rotationOffset
                    val theta = u * 2f * PI.toFloat() + rotationOffset
                    val ring = majorR + minorR * cos(phi)
                    tx = cos(theta) * ring
                    ty = sin(theta) * ring * 0.78f + sin(phi) * 0.16f
                    tz = sin(phi) * 0.8f
                }

                MorphShape.QUANTUM_GALAXY -> {
                    val r = 0.2f + rng.nextFloat() * 0.76f
                    val a = acos(1f - 2f * u) * 2.4f + rotationOffset
                    tx = cos(a) * r
                    ty = sin(a) * r * (0.7f + 0.3f * sin(a * 2f))
                    tz = cos(r * 4f) * 0.7f
                }
            }

            tx += cos(jitterAngle) * jitterR + touchNorm.x * 0.12f
            ty += sin(jitterAngle) * jitterR + touchNorm.y * 0.12f

            p.morphTargetX = tx.coerceIn(-0.98f, 0.98f)
            p.morphTargetY = ty.coerceIn(-0.98f, 0.98f)
            p.morphTargetDepth = tz.coerceIn(-1f, 1f)
        }
    }

    // Silky 60fps particle interpolation & 3D rotation loop
    LaunchedEffect(particleCount, isLowEndMode) {
        lastFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                val dt = if (lastFrameNanos == 0L) {
                    0.016f
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.04f)
                }
                lastFrameNanos = frameNanos
                timeSeconds += dt

                if (touchPulse > 0f) {
                    touchPulse = (touchPulse - dt * 1.6f).coerceAtLeast(0f)
                }

                // Smooth morph transition envelope: fast silky bloom on touch, lingering morph, graceful return
                if (isPointerHolding) {
                    holdSustainTimer = 0.75f
                    val alphaIn = 1f - exp(-8.5f * dt)
                    morphBlend += (1f - morphBlend) * alphaIn
                } else if (holdSustainTimer > 0f) {
                    holdSustainTimer = (holdSustainTimer - dt).coerceAtLeast(0f)
                    val alphaIn = 1f - exp(-6.5f * dt)
                    morphBlend += (1f - morphBlend) * alphaIn
                } else {
                    val alphaOut = 1f - exp(-3.4f * dt)
                    morphBlend += (0f - morphBlend) * alphaOut
                }

                val rotSpeed = if (isListening || isSpeaking) 0.72f else 0.42f
                val yaw = timeSeconds * rotSpeed
                val pitch = sin(timeSeconds * 0.31f) * 0.28f
                val cosY = cos(yaw)
                val sinY = sin(yaw)
                val cosP = cos(pitch)
                val sinP = sin(pitch)

                val sphereRadiusNorm = if (compactMode) 0.62f else 0.49f
                val followLerp = 1f - exp(-(if (morphBlend > 0.25f) 7.5f else 9.5f) * dt)
                val damping = exp(-6.5f * dt)

                particles.forEach { p ->
                    // Rotate base 3D sphere point smoothly
                    val rx = p.baseX * cosY - p.baseZ * sinY
                    val rz1 = p.baseX * sinY + p.baseZ * cosY
                    val ry = p.baseY * cosP - rz1 * sinP
                    val rz = p.baseY * sinP + rz1 * cosP

                    // Subtle organic breathing & micro-orbit drift
                    val drift = 0.018f * (1f + morphBlend * 1.8f)
                    val driftX = cos(timeSeconds * 1.4f + p.phase) * drift
                    val driftY = sin(timeSeconds * 1.25f + p.phase * 1.3f) * drift

                    val sphereX = rx * sphereRadiusNorm + driftX
                    val sphereY = ry * sphereRadiusNorm + driftY

                    // Smooth cubic ease between 3D sphere and random morphed constellation
                    val eased = morphBlend * morphBlend * (3f - 2f * morphBlend)
                    var targetX = sphereX * (1f - eased) + (p.morphTargetX + driftX * 1.6f) * eased
                    var targetY = sphereY * (1f - eased) + (p.morphTargetY + driftY * 1.6f) * eased
                    val targetDepth = rz * (1f - eased) + p.morphTargetDepth * eased

                    // Soft fluid repulsion near touch point while pointer is pressed
                    if (isPointerHolding) {
                        val dx = targetX - touchOriginNormalized.x * 0.65f
                        val dy = targetY - touchOriginNormalized.y * 0.65f
                        val distSq = dx * dx + dy * dy
                        if (distSq in 0.0001f..0.22f) {
                            val dist = sqrt(distSq)
                            val push = (0.47f - dist) * 0.24f
                            targetX += (dx / dist) * push
                            targetY += (dy / dist) * push
                        }
                    }

                    p.velX = (p.velX + (targetX - p.curX) * followLerp * 1.2f) * damping
                    p.velY = (p.velY + (targetY - p.curY) * followLerp * 1.2f) * damping
                    p.curX += (targetX - p.curX) * followLerp + p.velX
                    p.curY += (targetY - p.curY) * followLerp + p.velY
                    p.curDepth += (targetDepth - p.curDepth) * followLerp
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val targetSize: Dp = if (compactMode) {
            54.dp
        } else {
            min(maxWidth, maxHeight).coerceIn(200.dp, 330.dp)
        }

        Canvas(
            modifier = Modifier
                .size(targetSize)
                .testTag(if (compactMode) "mini_atom_sphere" else "atom_particle_sphere")
                .semantics {
                    contentDescription = "Lingkaran partikel Nova, sentuh untuk mengubah bentuk partikel acak"
                }
                .pointerInput(particleCount) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = (size.width.coerceAtMost(size.height) / 2f).coerceAtLeast(1f)
                        val normX = ((down.position.x - center.x) / maxR).coerceIn(-1f, 1f)
                        val normY = ((down.position.y - center.y) / maxR).coerceIn(-1f, 1f)

                        isPointerHolding = true
                        touchOriginNormalized = Offset(normX, normY)
                        touchPulse = 1f
                        morphSeed += 1
                        generateRandomMorphFormation(morphSeed, touchOriginNormalized)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                        // Add gentle radial impulse so transition feels alive immediately
                        particles.forEach { p ->
                            val dx = p.curX - normX * 0.5f
                            val dy = p.curY - normY * 0.5f
                            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.08f)
                            p.velX += (dx / dist) * 0.014f
                            p.velY += (dy / dist) * 0.014f
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
            val canvasRadius = size.minDimension / 2f * audioScale

            // 1. Soft monochrome outer orbital ring (fades smoothly during morph)
            val ringFade = (1f - morphBlend * 0.72f).coerceIn(0.18f, 1f)
            val audioBoost = if (isListening || isSpeaking) audioLevel * (canvasRadius * 0.14f) else 0f
            val breath = sin(timeSeconds * 2.0f) * (canvasRadius * 0.012f)
            val baseSphereR = canvasRadius * (if (compactMode) 0.62f else 0.49f)

            drawCircle(
                color = NovaHairline.copy(alpha = 0.75f * ringFade),
                radius = baseSphereR * 1.18f + audioBoost + breath,
                center = center,
                style = Stroke(width = if (compactMode) 0.9.dp.toPx() else 1.2.dp.toPx())
            )

            // 2. Soft dark volumetric core glow that dissolves into pure particles on touch
            val coreGlowAlpha = ((1f - morphBlend) * 0.14f).coerceIn(0f, 0.14f)
            if (coreGlowAlpha > 0.005f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NovaBlack.copy(alpha = coreGlowAlpha),
                            NovaSlate.copy(alpha = coreGlowAlpha * 0.45f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseSphereR * 1.05f
                    ),
                    radius = baseSphereR * 1.05f,
                    center = center
                )
            }

            // 3. Gentle expanding touch ripple ring
            if (touchPulse > 0f && !compactMode) {
                val progress = 1f - touchPulse
                drawCircle(
                    color = NovaBlack.copy(alpha = touchPulse * 0.28f),
                    radius = baseSphereR * 0.55f + canvasRadius * 0.48f * progress,
                    center = center,
                    style = Stroke(width = (1.8f * touchPulse).dp.toPx())
                )
            }

            // 4. Render all smooth volumetric particles (depth-shaded monochrome)
            val particleColors = arrayOf(NovaBlack, NovaCharcoal, NovaSlate)
            val maxReach = canvasRadius * 0.95f

            particles.forEachIndexed { idx, p ->
                val px = center.x + p.curX * maxReach
                val py = center.y + p.curY * maxReach
                val pos = Offset(px, py)

                // Depth factor (-1..1) controls smooth particle scale & contrast
                val depthNorm = ((p.curDepth + 1f) * 0.5f).coerceIn(0f, 1f)
                val dotRadius = p.sizeDp.dp.toPx() * (0.68f + depthNorm * 0.55f) *
                    (1f - morphBlend * 0.12f)
                val alpha = (0.32f + depthNorm * 0.65f).coerceIn(0.25f, 0.97f)

                // Subtle filament connections during morph for constellation beauty (skipped in low-end mode)
                if (!isLowEndMode && !compactMode && morphBlend > 0.22f && idx > 0 && idx % 7 == 0) {
                    val prev = particles[idx - 1]
                    val prevPos = Offset(
                        center.x + prev.curX * maxReach,
                        center.y + prev.curY * maxReach
                    )
                    val dx = pos.x - prevPos.x
                    val dy = pos.y - prevPos.y
                    if (dx * dx + dy * dy < (canvasRadius * 0.36f) * (canvasRadius * 0.36f)) {
                        drawLine(
                            color = NovaMuted.copy(alpha = morphBlend * 0.22f),
                            start = pos,
                            end = prevPos,
                            strokeWidth = 0.75.dp.toPx()
                        )
                    }
                }

                drawCircle(
                    color = particleColors[p.shadeIndex].copy(alpha = alpha),
                    radius = dotRadius,
                    center = pos
                )
            }
        }
    }
}
