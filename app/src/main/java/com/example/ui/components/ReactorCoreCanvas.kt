package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.CoreState
import com.example.domain.OrbVisualState
import com.example.domain.ResolvedPerformanceTier
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudBorderCyan
import com.example.ui.theme.HudPanelSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ProtonAmber
import com.example.ui.theme.ProtonCrimson
import com.example.ui.theme.ProtonCyan
import com.example.ui.theme.ProtonEmerald
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val CrystalBrightCyan = Color(0xFFA8F9FF)
private val CrystalRoyalBlue = Color(0xFF2979FF)
private val CrystalDeepViolet = Color(0xFF8E2DE2)
private val CrystalBrightViolet = Color(0xFFC77DFF)
private val CrystalOfflineBlue = Color(0xFF3872A8)

private data class SphereParticleSeed(
    val radiusFrac: Float,
    val angleDeg: Float,
    val speedMul: Float,
    val sizeMul: Float,
    val colorBand: Int
)

/**
 * Strictly circular-clipped 3D Crystal-Glass Holographic Reactor Core for the HUD screen.
 * Every glow, energy layer, ring, circuit trace, scan wave, and particle stays strictly
 * inside the circular glass boundary (`CircleShape` + `clipPath`).
 */
@Composable
fun ProtonReactorCore(
    coreState: CoreState,
    orbState: OrbVisualState,
    audioLevel: Float,
    contentDescriptionText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coreSize: Dp = 220.dp,
    performanceTier: ResolvedPerformanceTier = ResolvedPerformanceTier.BALANCED
) {
    val infiniteTransition = rememberInfiniteTransition(label = "crystal_sphere_anim")

    val cwRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbVisualState.THINKING -> 4_200
                    OrbVisualState.LISTENING -> 6_200
                    OrbVisualState.OFFLINE -> 15_000
                    else -> 9_000
                },
                easing = LinearEasing
            )
        ),
        label = "cw_rot"
    )

    val ccwRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (orbState) {
                    OrbVisualState.THINKING -> 3_200
                    OrbVisualState.LISTENING -> 5_000
                    OrbVisualState.OFFLINE -> 12_000
                    else -> 7_200
                },
                easing = LinearEasing
            )
        ),
        label = "ccw_rot"
    )

    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (orbState == OrbVisualState.LISTENING) 1_400 else 2_400,
                easing = LinearEasing
            )
        ),
        label = "scan_wave"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "core_pulse"
    )

    val reactiveBoost by animateFloatAsState(
        targetValue = when (orbState) {
            OrbVisualState.LISTENING -> 0.12f + (audioLevel * 0.22f)
            OrbVisualState.SPEAKING -> 0.16f + (audioLevel * 0.20f)
            OrbVisualState.THINKING -> 0.10f
            else -> 0f
        },
        animationSpec = spring(stiffness = 280f),
        label = "reactive_boost"
    )

    val particles = remember {
        List(32) { idx ->
            SphereParticleSeed(
                radiusFrac = 0.27f + ((idx * 17) % 62) / 100f,
                angleDeg = (idx * 137.5f) % 360f,
                speedMul = (if (idx % 2 == 0) 1f else -1f) * (0.55f + ((idx * 7) % 9) * 0.10f),
                sizeMul = 0.75f + ((idx * 11) % 6) * 0.18f,
                colorBand = idx % 3
            )
        }
    }

    val primaryCyan = when (orbState) {
        OrbVisualState.ERROR -> ProtonCrimson
        OrbVisualState.OFFLINE -> CrystalOfflineBlue
        else -> ProtonCyan
    }
    val primaryViolet = when (orbState) {
        OrbVisualState.ERROR -> Color(0xFFFF7A96)
        OrbVisualState.OFFLINE -> Color(0xFF28507D)
        else -> CrystalBrightViolet
    }
    val dimFactor = if (orbState == OrbVisualState.OFFLINE) 0.45f else 1.0f

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(coreSize)
            .clip(CircleShape)
            .semantics { contentDescription = contentDescriptionText }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, color = primaryCyan),
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val center = Offset(size.width / 2f, size.height / 2f)
            // Sphere fills nearly the entire circular Box with ZERO overflow outside sphereR
            val sphereR = minDim * 0.49f

            val sphereClip = Path().apply {
                addOval(
                    Rect(
                        center.x - sphereR,
                        center.y - sphereR,
                        center.x + sphereR,
                        center.y + sphereR
                    )
                )
            }

            clipPath(sphereClip) {
                // 1. Dark transparent crystal-glass interior & internal rim depth
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xE8101438),
                            Color(0xF00C0826),
                            Color(0xF6030714)
                        ),
                        center = Offset(center.x - sphereR * 0.18f, center.y - sphereR * 0.22f),
                        radius = sphereR * 1.15f
                    ),
                    radius = sphereR,
                    center = center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        0.84f to (if (orbState == OrbVisualState.ERROR) Color(0x40A01C3C) else CrystalDeepViolet.copy(alpha = 0.18f * dimFactor)),
                        1.0f to primaryCyan.copy(alpha = 0.34f * dimFactor),
                        center = center,
                        radius = sphereR
                    ),
                    radius = sphereR,
                    center = center
                )

                // 2. Opposite-rotating violet (CW) & cyan/royal-blue (CCW) holographic energy layers
                rotate(degrees = cwRotation * 0.7f, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.00f to Color.Transparent,
                            0.18f to CrystalDeepViolet.copy(alpha = 0.38f * dimFactor),
                            0.32f to primaryViolet.copy(alpha = 0.32f * dimFactor),
                            0.48f to Color.Transparent,
                            0.74f to CrystalDeepViolet.copy(alpha = 0.28f * dimFactor),
                            1.00f to Color.Transparent,
                            center = center
                        ),
                        radius = sphereR * 0.96f,
                        center = center
                    )
                }

                rotate(degrees = ccwRotation * 1.05f, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.00f to Color.Transparent,
                            0.18f to primaryCyan.copy(alpha = 0.36f * dimFactor),
                            0.35f to CrystalRoyalBlue.copy(alpha = 0.30f * dimFactor),
                            0.52f to Color.Transparent,
                            0.80f to primaryCyan.copy(alpha = 0.28f * dimFactor),
                            1.00f to Color.Transparent,
                            center = center
                        ),
                        radius = sphereR * 0.96f,
                        center = center
                    )
                }

                // 3. Detailed animated digital circuits inside the glass (adaptive by performanceTier)
                val traceStroke = (sphereR * 0.013f).coerceAtLeast(1.1f)
                val tracePath = Path()
                if (performanceTier != ResolvedPerformanceTier.LOW_END || orbState == OrbVisualState.THINKING) {
                    rotate(degrees = cwRotation * 0.35f, pivot = center) {
                        val busCount = if (performanceTier == ResolvedPerformanceTier.HIGH_PERFORMANCE) 12 else 8
                        for (i in 0 until busCount) {
                            val baseDeg = i * (360f / busCount)
                            val r1 = baseDeg * (PI / 180.0)
                            val r2 = (baseDeg + 14f) * (PI / 180.0)
                            val rStart = sphereR * (0.48f + (i % 3) * 0.08f)
                            val rMid = sphereR * (0.72f + (i % 2) * 0.09f)
                            val rEnd = sphereR * 0.93f

                            val x0 = center.x + (rStart * cos(r1)).toFloat()
                            val y0 = center.y + (rStart * sin(r1)).toFloat()
                            val x1 = center.x + (rMid * cos(r1)).toFloat()
                            val y1 = center.y + (rMid * sin(r1)).toFloat()
                            val x2 = center.x + (rMid * cos(r2)).toFloat()
                            val y2 = center.y + (rMid * sin(r2)).toFloat()
                            val x3 = center.x + (rEnd * cos(r2)).toFloat()
                            val y3 = center.y + (rEnd * sin(r2)).toFloat()

                            tracePath.reset()
                            tracePath.moveTo(x0, y0)
                            tracePath.lineTo(x1, y1)
                            tracePath.lineTo(x2, y2)
                            tracePath.lineTo(x3, y3)

                            drawPath(
                                path = tracePath,
                                color = (if (i % 2 == 0) primaryCyan else CrystalRoyalBlue).copy(alpha = 0.56f * dimFactor),
                                style = Stroke(width = traceStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                            drawCircle(
                                color = CrystalBrightCyan.copy(alpha = 0.72f * dimFactor),
                                radius = traceStroke * 1.4f,
                                center = Offset(x1, y1)
                            )
                        }
                    }
                }

                rotate(degrees = ccwRotation * 0.6f, pivot = center) {
                    for (i in 0 until 8) {
                        val baseDeg = i * 45f + 22.5f
                        val r1 = baseDeg * (PI / 180.0)
                        val r2 = (baseDeg - 16f) * (PI / 180.0)
                        val r0 = sphereR * 0.36f
                        val rMid = sphereR * 0.56f
                        val rEnd = sphereR * 0.78f

                        tracePath.reset()
                        tracePath.moveTo(center.x + (r0 * cos(r1)).toFloat(), center.y + (r0 * sin(r1)).toFloat())
                        tracePath.lineTo(center.x + (rMid * cos(r1)).toFloat(), center.y + (rMid * sin(r1)).toFloat())
                        tracePath.lineTo(center.x + (rMid * cos(r2)).toFloat(), center.y + (rMid * sin(r2)).toFloat())
                        tracePath.lineTo(center.x + (rEnd * cos(r2)).toFloat(), center.y + (rEnd * sin(r2)).toFloat())

                        drawPath(
                            path = tracePath,
                            color = primaryViolet.copy(alpha = 0.54f * dimFactor),
                            style = Stroke(width = traceStroke * 0.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        val packetT = (scanProgress + i * 0.125f) % 1f
                        val pr = r0 + (rEnd - r0) * packetT
                        val pa = if (packetT < 0.5f) r1 else r2
                        drawCircle(
                            color = Color.White.copy(alpha = 0.75f * dimFactor),
                            radius = traceStroke * 1.25f,
                            center = Offset(
                                center.x + (pr * cos(pa)).toFloat(),
                                center.y + (pr * sin(pa)).toFloat()
                            )
                        )
                    }
                }

                // 4. Multiple concentric rotating neon rings + cyan telemetry dot ring inside sphere
                val dotRingR = sphereR * 0.39f
                val dotRadius = (sphereR * 0.017f).coerceAtLeast(1.3f)
                for (i in 0 until 32) {
                    val theta = (cwRotation * 0.5f + i * (360f / 32f)) * (PI / 180.0)
                    drawCircle(
                        color = CrystalBrightCyan.copy(alpha = 0.84f * dimFactor),
                        radius = dotRadius,
                        center = Offset(
                            center.x + (dotRingR * cos(theta)).toFloat(),
                            center.y + (dotRingR * sin(theta)).toFloat()
                        )
                    )
                }

                val ringStroke = (sphereR * 0.014f).coerceAtLeast(1.1f)
                val ringConfigs = listOf(
                    Triple(0.49f, ccwRotation * 1.1f, primaryViolet to (3 to 76f)),
                    Triple(0.60f, cwRotation * 0.85f, primaryCyan to (4 to 58f)),
                    Triple(0.71f, ccwRotation * 0.65f, CrystalRoyalBlue to (5 to 42f)),
                    Triple(0.82f, cwRotation * 0.5f, primaryViolet to (3 to 82f)),
                    Triple(0.90f, ccwRotation * 0.4f, primaryCyan to (6 to 36f))
                )
                for ((rFrac, deg, styleSpec) in ringConfigs) {
                    val rr = sphereR * rFrac
                    val (ringColor, segPair) = styleSpec
                    val (segments, sweep) = segPair
                    val step = 360f / segments
                    for (s in 0 until segments) {
                        drawArc(
                            color = ringColor.copy(alpha = 0.68f * dimFactor),
                            startAngle = (deg + s * step) % 360f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - rr, center.y - rr),
                            size = Size(rr * 2f, rr * 2f),
                            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                        )
                    }
                }

                // 5. Internal scanning radar & expanding pulse waves (clipped inside circle)
                rotate(degrees = cwRotation * 1.15f, pivot = center) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0.00f to Color.Transparent,
                            0.10f to primaryCyan.copy(alpha = 0.28f * dimFactor),
                            0.22f to Color.Transparent,
                            center = center
                        ),
                        radius = sphereR * 0.96f,
                        center = center
                    )
                }

                val waveCount = if (orbState == OrbVisualState.LISTENING) 3 else 2
                for (w in 0 until waveCount) {
                    val p = (scanProgress + w.toFloat() / waveCount) % 1f
                    val wr = sphereR * (0.24f + 0.70f * p)
                    drawCircle(
                        color = (if (w % 2 == 0) primaryCyan else primaryViolet).copy(
                            alpha = (1f - p) * (if (orbState == OrbVisualState.LISTENING) 0.85f else 0.50f) * dimFactor
                        ),
                        radius = wr,
                        center = center,
                        style = Stroke(width = (sphereR * 0.018f).coerceAtLeast(1.3f))
                    )
                }

                if (orbState == OrbVisualState.SPEAKING) {
                    val bars = 28
                    for (i in 0 until bars) {
                        val theta = (i * (360f / bars) + cwRotation * 0.4f) * (PI / 180.0)
                        val wave = 0.3f + 0.7f * abs(sin(scanProgress * 4f * PI.toFloat() + i * 0.6f))
                        val rIn = sphereR * 0.30f
                        val rOut = rIn + sphereR * (0.08f + (0.14f + audioLevel * 0.14f) * wave)
                        drawLine(
                            color = (if (i % 2 == 0) CrystalBrightCyan else primaryViolet).copy(alpha = 0.85f),
                            start = Offset(
                                center.x + (rIn * cos(theta)).toFloat(),
                                center.y + (rIn * sin(theta)).toFloat()
                            ),
                            end = Offset(
                                center.x + (rOut * cos(theta)).toFloat(),
                                center.y + (rOut * sin(theta)).toFloat()
                            ),
                            strokeWidth = (sphereR * 0.020f).coerceAtLeast(1.5f),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 6. Tiny glowing particles moving strictly within the sphere (adaptive count)
                val activeParticleCount = when (performanceTier) {
                    ResolvedPerformanceTier.LOW_END -> 10
                    ResolvedPerformanceTier.BALANCED -> 20
                    ResolvedPerformanceTier.HIGH_PERFORMANCE -> particles.size
                }
                for (i in 0 until activeParticleCount.coerceAtMost(particles.size)) {
                    val p = particles[i]
                    val theta = (p.angleDeg + cwRotation * p.speedMul) * (PI / 180.0)
                    val r = sphereR * p.radiusFrac
                    val shimmer = 0.55f + 0.45f * sin(scanProgress * 2f * PI.toFloat() + i)
                    val pColor = when (p.colorBand) {
                        0 -> CrystalBrightCyan
                        1 -> primaryViolet
                        else -> Color.White
                    }
                    drawCircle(
                        color = pColor.copy(alpha = 0.78f * shimmer * dimFactor),
                        radius = (sphereR * 0.012f * p.sizeMul).coerceAtLeast(1.0f),
                        center = Offset(
                            center.x + (r * cos(theta)).toFloat(),
                            center.y + (r * sin(theta)).toFloat()
                        )
                    )
                }

                // 7. Smoothly pulsing cyan-white central core
                val coreScale = (pulse + reactiveBoost).coerceIn(0.86f, 1.32f)
                val haloR = sphereR * 0.36f * coreScale
                val coreR = sphereR * 0.18f * coreScale

                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to CrystalBrightCyan.copy(alpha = 0.88f * dimFactor),
                        0.52f to primaryViolet.copy(alpha = 0.56f * dimFactor),
                        1.00f to Color.Transparent,
                        center = center,
                        radius = haloR
                    ),
                    radius = haloR,
                    center = center
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to (if (orbState == OrbVisualState.OFFLINE) CrystalBrightCyan else Color.White),
                        0.62f to (if (orbState == OrbVisualState.ERROR) Color(0xFFFFC3D0) else CrystalBrightCyan),
                        1.00f to primaryCyan,
                        center = Offset(center.x - coreR * 0.18f, center.y - coreR * 0.18f),
                        radius = coreR
                    ),
                    radius = coreR,
                    center = center
                )

                // 8. Crystal glass specular highlights & crisp circular rim strictly inside sphereR
                val rimStroke = (sphereR * 0.024f).coerceAtLeast(1.8f)
                drawCircle(
                    color = primaryCyan.copy(alpha = 0.76f * dimFactor),
                    radius = sphereR - rimStroke * 0.5f,
                    center = center,
                    style = Stroke(width = rimStroke)
                )
                val specR = sphereR * 0.90f
                drawArc(
                    color = Color.White.copy(alpha = 0.45f * dimFactor),
                    startAngle = 198f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(center.x - specR, center.y - specR),
                    size = Size(specR * 2f, specR * 2f),
                    style = Stroke(width = (sphereR * 0.020f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
                )
                drawArc(
                    color = CrystalBrightCyan.copy(alpha = 0.54f * dimFactor),
                    startAngle = 315f,
                    sweepAngle = 44f,
                    useCenter = false,
                    topLeft = Offset(center.x - specR, center.y - specR),
                    size = Size(specR * 2f, specR * 2f),
                    style = Stroke(width = (sphereR * 0.016f).coerceAtLeast(1.2f), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
fun AudioWaveformBar(
    coreState: CoreState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 29
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = LinearEasing)
        ),
        label = "wave_phase"
    )

    val waveColor = when (coreState) {
        CoreState.ERROR -> ProtonCrimson
        CoreState.SPEAKING -> ProtonEmerald
        CoreState.WAKE_ARMED -> ProtonAmber
        else -> ProtonCyan
    }

    val amplitudeFactor = when (coreState) {
        CoreState.LISTENING -> (0.28f + audioLevel * 0.72f).coerceIn(0.25f, 1f)
        CoreState.SPEAKING -> 0.78f
        CoreState.PROCESSING -> 0.56f
        CoreState.WAKE_ARMED -> 0.30f
        else -> 0.18f
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
    ) {
        val midX = (barCount - 1) / 2f
        val barWidth = (size.width / (barCount * 1.9f)).coerceIn(2.5f, 6f)
        val spacing = (size.width - barWidth * barCount) / (barCount - 1).coerceAtLeast(1)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val distFromCenter = abs(i - midX) / midX.coerceAtLeast(1f)
            val envelope = (1f - distFromCenter * 0.75f).coerceIn(0.15f, 1f)
            val harmonic = 0.5f + 0.5f * sin(phase + i * 0.55f)
            val barHeight = (size.height * 0.92f * envelope * amplitudeFactor * (0.35f + 0.65f * harmonic))
                .coerceAtLeast(3.dp.toPx())

            val x = i * (barWidth + spacing)
            val top = centerY - barHeight / 2f
            val alpha = (0.35f + 0.65f * envelope * amplitudeFactor).coerceIn(0.25f, 1f)

            drawRoundRect(
                color = waveColor.copy(alpha = alpha),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun HudBracketCard(
    modifier: Modifier = Modifier,
    accentColor: Color = HudBorderBright,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HudPanelSurface.copy(alpha = 0.88f),
                        ObsidianVoid.copy(alpha = 0.92f)
                    )
                ),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = HudBorderCyan.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val bracketLen = 12.dp.toPx()
            val stroke = 2.dp.toPx()
            drawLine(accentColor, Offset(0f, bracketLen), Offset(0f, 0f), stroke)
            drawLine(accentColor, Offset(0f, 0f), Offset(bracketLen, 0f), stroke)
            drawLine(accentColor, Offset(size.width - bracketLen, 0f), Offset(size.width, 0f), stroke)
            drawLine(accentColor, Offset(size.width, 0f), Offset(size.width, bracketLen), stroke)
            drawLine(accentColor, Offset(0f, size.height - bracketLen), Offset(0f, size.height), stroke)
            drawLine(accentColor, Offset(0f, size.height), Offset(bracketLen, size.height), stroke)
            drawLine(accentColor, Offset(size.width - bracketLen, size.height), Offset(size.width, size.height), stroke)
            drawLine(accentColor, Offset(size.width, size.height - bracketLen), Offset(size.width, size.height), stroke)
        }
        Box(modifier = Modifier.padding(12.dp), content = content)
    }
}
