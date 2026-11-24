package com.example.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import com.example.domain.OrbVisualState
import com.example.domain.ResolvedPerformanceTier
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Strictly-clipped 3D Crystal-Glass Holographic Sphere for Android WindowManager overlay.
 *
 * CRITICAL BOUNDARY GUARANTEE:
 * - Zero light, glow, particles, arcs, rings, or rays are rendered outside the circle.
 * - Both ViewOutlineProvider (`clipToOutline = true`) and `canvas.clipPath(circle)` mask
 *   every single animation layer strictly within `(cx, cy, sphereR)`.
 *
 * Internal Visual Layers:
 * - Translucent dark crystal-glass interior with iridescent cyan / royal-blue / deep-violet depth
 * - Counter-rotating violet and electric-cyan holographic energy layers
 * - Animated internal micro-circuits & holographic mesh inside the glass
 * - Multiple concentric neon rings & dotted cyan telemetry collar
 * - Internal radial scanning waves & state-reactive waveform/pulses
 * - Micro-particles orbiting strictly within the sphere radius
 * - Smoothly pulsing cyan-white central plasma core
 */
class FloatingNeuralBubbleView(context: Context) : View(context) {

    var orbState: OrbVisualState = OrbVisualState.IDLE
        set(value) {
            if (field != value) {
                field = value
                cachedShaderKey = -1
                updateAnimatorDuration()
                invalidate()
            }
        }

    var audioLevel: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
        }

    var rotatingRingsEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var electricArcsEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var animationSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.4f, 2.0f)
            updateAnimatorDuration()
        }

    var bubbleOpacity: Float = 0.92f
        set(value) {
            field = value.coerceIn(0.35f, 1.0f)
            alpha = field
        }

    var glowIntensity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.2f, 2.0f)
            cachedShaderKey = -1
            invalidate()
        }

    var particleIntensity: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.5f)
            invalidate()
        }

    var performanceTier: ResolvedPerformanceTier = ResolvedPerformanceTier.BALANCED
        set(value) {
            if (field != value) {
                field = value
                cachedShaderKey = -1
                invalidate()
            }
        }

    private var lastFrameDrawMs: Long = 0L
    private var phase: Float = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        duration = 6400L
        addUpdateListener { anim ->
            val minIntervalMs = when (performanceTier) {
                ResolvedPerformanceTier.LOW_END -> 34L
                ResolvedPerformanceTier.BALANCED -> 22L
                ResolvedPerformanceTier.HIGH_PERFORMANCE -> 0L
            }
            val now = android.os.SystemClock.uptimeMillis()
            if (minIntervalMs == 0L || (now - lastFrameDrawMs) >= minIntervalMs) {
                lastFrameDrawMs = now
                phase = anim.animatedValue as Float
                invalidate()
            }
        }
    }

    // Palette matching luxury crystal-glass holographic reference (cyan, royal blue, deep violet, white)
    private val electricCyan = Color.rgb(56, 232, 255)
    private val brightCyan = Color.rgb(168, 249, 255)
    private val royalBlue = Color.rgb(41, 121, 255)
    private val deepViolet = Color.rgb(142, 45, 226)
    private val brightViolet = Color.rgb(199, 125, 255)
    private val offlineBlue = Color.rgb(56, 114, 168)
    private val errorRed = Color.rgb(255, 72, 102)

    // Pre-allocated paints & paths to avoid per-frame GC churn on Realme C53
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerRimGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val violetEnergyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cyanEnergyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepWavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val circuitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val scanRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val coreHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val coreOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val clipPath = Path()
    private val circuitPath = Path()
    private val rectF = RectF()

    private var cachedWidth = -1
    private var cachedHeight = -1
    private var cachedShaderKey = -1

    // Precomputed deterministic internal particle seeds (radiusFrac, angleOffsetDeg, speedMul, sizeMul, colorBand)
    private data class InternalParticle(
        val radiusFrac: Float,
        val angleOffsetDeg: Float,
        val speedMul: Float,
        val sizeMul: Float,
        val colorBand: Int
    )

    private val internalParticles = List(36) { idx ->
        val rFrac = 0.27f + ((idx * 17) % 62) / 100f // strictly 0.27..0.88 inside sphere
        val ang = (idx * 137.5f) % 360f
        val spd = (if (idx % 2 == 0) 1f else -1f) * (0.5f + ((idx * 7) % 10) * 0.11f)
        val sz = 0.7f + ((idx * 13) % 7) * 0.18f
        InternalParticle(rFrac, ang, spd, sz, idx % 3)
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        contentDescription = "PROTON holographic crystal sphere"
        alpha = bubbleOpacity
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val w = view.width
                val h = view.height
                if (w <= 0 || h <= 0) return
                val side = min(w, h)
                val r = (side * 0.485f).toInt()
                val cx = w / 2
                val cy = h / 2
                outline.setOval(cx - r, cy - r, cx + r, cy + r)
            }
        }
        clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w
        cachedHeight = h
        cachedShaderKey = -1
        invalidateOutline()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimatorState()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateAnimatorState()
    }

    fun pauseAnimations() {
        if (animator.isRunning) {
            animator.cancel()
        }
    }

    fun resumeAnimations() {
        updateAnimatorState()
    }

    private fun updateAnimatorDuration() {
        val stateMultiplier = when (orbState) {
            OrbVisualState.THINKING -> 2.0f
            OrbVisualState.LISTENING -> 1.45f
            OrbVisualState.SPEAKING -> 1.55f
            OrbVisualState.ERROR -> 1.7f
            OrbVisualState.OFFLINE -> 0.55f
            OrbVisualState.IDLE -> 1.0f
        }
        val durationMs = (6400f / (animationSpeed * stateMultiplier)).toLong().coerceIn(1100L, 14_000L)
        if (animator.duration != durationMs) {
            animator.duration = durationMs
        }
    }

    private fun updateAnimatorState() {
        val shouldAnimate = isAttachedToWindow &&
            visibility == VISIBLE &&
            ValueAnimator.areAnimatorsEnabled()

        if (shouldAnimate) {
            updateAnimatorDuration()
            if (!animator.isStarted) {
                animator.start()
            }
        } else if (animator.isRunning) {
            animator.cancel()
        }
    }

    private fun ensureShaders(cx: Float, cy: Float, sphereR: Float) {
        val key = (sphereR.toInt() * 31) + orbState.ordinal * 7 + (glowIntensity * 10).toInt()
        if (key == cachedShaderKey) return
        cachedShaderKey = key

        val glowScale = glowIntensity.coerceIn(0.2f, 2.0f)
        val dimFactor = if (orbState == OrbVisualState.OFFLINE) 0.45f else 1.0f

        glassPaint.shader = RadialGradient(
            cx - sphereR * 0.18f,
            cy - sphereR * 0.22f,
            sphereR * 1.15f,
            intArrayOf(
                Color.argb(232, 16, 20, 56),
                Color.argb(240, 12, 8, 38),
                Color.argb(246, 3, 7, 20)
            ),
            floatArrayOf(0.0f, 0.58f, 1.0f),
            Shader.TileMode.CLAMP
        )

        val rimPrimary = when (orbState) {
            OrbVisualState.ERROR -> errorRed
            OrbVisualState.OFFLINE -> offlineBlue
            else -> electricCyan
        }
        val rimSecondary = when (orbState) {
            OrbVisualState.ERROR -> Color.rgb(160, 28, 60)
            OrbVisualState.OFFLINE -> Color.rgb(24, 52, 88)
            else -> deepViolet
        }
        val rimAlpha = (78 * glowScale * dimFactor).toInt().coerceIn(20, 160)
        innerRimGlowPaint.shader = RadialGradient(
            cx,
            cy,
            sphereR,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb((rimAlpha * 0.45f).toInt(), Color.red(rimSecondary), Color.green(rimSecondary), Color.blue(rimSecondary)),
                Color.argb(rimAlpha, Color.red(rimPrimary), Color.green(rimPrimary), Color.blue(rimPrimary))
            ),
            floatArrayOf(0.55f, 0.84f, 1.0f),
            Shader.TileMode.CLAMP
        )

        val violetAlpha = (95 * glowScale * dimFactor).toInt().coerceIn(18, 175)
        val vPrimary = if (orbState == OrbVisualState.ERROR) errorRed else deepViolet
        val vBright = if (orbState == OrbVisualState.ERROR) Color.rgb(255, 130, 150) else brightViolet
        violetEnergyPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(violetAlpha, Color.red(vPrimary), Color.green(vPrimary), Color.blue(vPrimary)),
                Color.argb((violetAlpha * 0.85f).toInt(), Color.red(vBright), Color.green(vBright), Color.blue(vBright)),
                Color.TRANSPARENT,
                Color.argb((violetAlpha * 0.7f).toInt(), Color.red(vPrimary), Color.green(vPrimary), Color.blue(vPrimary)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.16f, 0.30f, 0.48f, 0.74f, 1.0f)
        )

        val cyanAlpha = (90 * glowScale * dimFactor).toInt().coerceIn(18, 175)
        val cPrimary = if (orbState == OrbVisualState.OFFLINE) offlineBlue else electricCyan
        val cDeep = if (orbState == OrbVisualState.OFFLINE) Color.rgb(24, 58, 96) else royalBlue
        cyanEnergyPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(cyanAlpha, Color.red(cPrimary), Color.green(cPrimary), Color.blue(cPrimary)),
                Color.argb((cyanAlpha * 0.8f).toInt(), Color.red(cDeep), Color.green(cDeep), Color.blue(cDeep)),
                Color.TRANSPARENT,
                Color.argb((cyanAlpha * 0.75f).toInt(), Color.red(cPrimary), Color.green(cPrimary), Color.blue(cPrimary)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.18f, 0.34f, 0.52f, 0.80f, 1.0f)
        )

        val scanColor = if (orbState == OrbVisualState.ERROR) errorRed else electricCyan
        sweepWavePaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb((72 * dimFactor).toInt(), Color.red(scanColor), Color.green(scanColor), Color.blue(scanColor)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.10f, 0.22f)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        // Fill almost the full view diameter; ZERO external glow or overflow allowed outside sphereR
        val sphereR = min(w, h) * 0.485f

        ensureShaders(cx, cy, sphereR)

        val pulse = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
        val rotDeg = phase * 360f
        val dimFactor = if (orbState == OrbVisualState.OFFLINE) 0.45f else 1.0f

        val primaryCyan = when (orbState) {
            OrbVisualState.ERROR -> errorRed
            OrbVisualState.OFFLINE -> offlineBlue
            else -> electricCyan
        }
        val primaryViolet = when (orbState) {
            OrbVisualState.ERROR -> Color.rgb(255, 110, 140)
            OrbVisualState.OFFLINE -> Color.rgb(42, 82, 130)
            else -> brightViolet
        }

        // STRICT CIRCULAR CLIP: Every single visual element below stays inside circle(cx, cy, sphereR)
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, sphereR, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // 1. Crystal glass abyssal base + spherical internal edge luminescence
        canvas.drawCircle(cx, cy, sphereR, glassPaint)
        canvas.drawCircle(cx, cy, sphereR, innerRimGlowPaint)

        // 2. Opposite-rotating violet (clockwise) & cyan/royal-blue (counter-clockwise) energy layers
        canvas.save()
        canvas.rotate(rotDeg * 0.7f, cx, cy)
        canvas.drawCircle(cx, cy, sphereR * 0.96f, violetEnergyPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-rotDeg * 1.05f, cx, cy)
        canvas.drawCircle(cx, cy, sphereR * 0.96f, cyanEnergyPaint)
        canvas.restore()

        // 3. Detailed animated digital circuits inside the glass (adaptive on LOW_END)
        if (electricArcsEnabled && (performanceTier != ResolvedPerformanceTier.LOW_END || orbState == OrbVisualState.THINKING)) {
            drawInternalDigitalCircuits(canvas, cx, cy, sphereR, rotDeg, primaryCyan, primaryViolet, dimFactor)
        }

        // 4. Multiple concentric rotating neon rings & dotted cyan collar inside the sphere
        if (rotatingRingsEnabled) {
            drawConcentricInternalRings(canvas, cx, cy, sphereR, rotDeg, primaryCyan, primaryViolet, dimFactor)
        }

        // 5. Internal scanning waves & reactive audio/state patterns (clipped inside circle)
        drawInternalScanningWaves(canvas, cx, cy, sphereR, rotDeg, primaryCyan, primaryViolet, dimFactor)

        // 6. Tiny glowing particles moving strictly inside the sphere
        drawInternalParticles(canvas, cx, cy, sphereR, rotDeg, primaryCyan, primaryViolet, dimFactor)

        // 7. Smoothly pulsing cyan-white central plasma core
        drawCentralCyanWhiteCore(canvas, cx, cy, sphereR, pulse, primaryCyan, primaryViolet, dimFactor)

        // 8. Crystal glass specular reflection arcs & crisp circular boundary inside clip
        drawCrystalGlassRim(canvas, cx, cy, sphereR, primaryCyan, dimFactor)

        canvas.restore()
    }

    private fun drawInternalDigitalCircuits(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        rotDeg: Float,
        cyan: Int,
        violet: Int,
        dimFactor: Float
    ) {
        val glitchShift = if (orbState == OrbVisualState.ERROR && ((phase * 18f).toInt() % 3 == 0)) {
            2.2f
        } else {
            0f
        }
        val strokeBase = (sphereR * 0.014f).coerceAtLeast(1.1f)

        // Outer circuit layer rotating slowly clockwise
        canvas.save()
        canvas.rotate(rotDeg * 0.35f + glitchShift, cx, cy)
        circuitPaint.strokeWidth = strokeBase
        val busCount = 12
        for (i in 0 until busCount) {
            val baseAngle = i * (360f / busCount)
            val rad1 = baseAngle * (PI / 180.0)
            val rad2 = (baseAngle + 14f) * (PI / 180.0)
            val rStart = sphereR * (0.48f + (i % 3) * 0.08f)
            val rMid = sphereR * (0.72f + (i % 2) * 0.09f)
            val rEnd = sphereR * 0.93f

            circuitPath.reset()
            val x0 = cx + (rStart * cos(rad1)).toFloat()
            val y0 = cy + (rStart * sin(rad1)).toFloat()
            val x1 = cx + (rMid * cos(rad1)).toFloat()
            val y1 = cy + (rMid * sin(rad1)).toFloat()
            val x2 = cx + (rMid * cos(rad2)).toFloat()
            val y2 = cy + (rMid * sin(rad2)).toFloat()
            val x3 = cx + (rEnd * cos(rad2)).toFloat()
            val y3 = cy + (rEnd * sin(rad2)).toFloat()

            circuitPath.moveTo(x0, y0)
            circuitPath.lineTo(x1, y1)
            circuitPath.lineTo(x2, y2)
            circuitPath.lineTo(x3, y3)

            circuitPaint.color = if (i % 2 == 0) cyan else royalBlue
            circuitPaint.alpha = ((95 + 55 * glowIntensity) * dimFactor).toInt().coerceIn(25, 210)
            canvas.drawPath(circuitPath, circuitPaint)

            // Circuit pad nodes
            dotPaint.color = if (i % 2 == 0) brightCyan else cyan
            dotPaint.alpha = (180 * dimFactor).toInt().coerceIn(30, 230)
            canvas.drawCircle(x1, y1, strokeBase * 1.45f, dotPaint)
            canvas.drawCircle(x2, y2, strokeBase * 1.2f, dotPaint)
        }
        canvas.restore()

        // Inner violet holographic circuit filaments rotating counter-clockwise
        canvas.save()
        canvas.rotate(-rotDeg * 0.6f, cx, cy)
        circuitPaint.strokeWidth = strokeBase * 0.9f
        for (i in 0 until 8) {
            val baseAngle = i * 45f + 22.5f
            val rad1 = baseAngle * (PI / 180.0)
            val rad2 = (baseAngle - 16f) * (PI / 180.0)
            val r0 = sphereR * 0.36f
            val r1 = sphereR * 0.56f
            val r2 = sphereR * 0.78f

            circuitPath.reset()
            circuitPath.moveTo(cx + (r0 * cos(rad1)).toFloat(), cy + (r0 * sin(rad1)).toFloat())
            circuitPath.lineTo(cx + (r1 * cos(rad1)).toFloat(), cy + (r1 * sin(rad1)).toFloat())
            circuitPath.lineTo(cx + (r1 * cos(rad2)).toFloat(), cy + (r1 * sin(rad2)).toFloat())
            circuitPath.lineTo(cx + (r2 * cos(rad2)).toFloat(), cy + (r2 * sin(rad2)).toFloat())

            circuitPaint.color = violet
            circuitPaint.alpha = ((105 + 45 * glowIntensity) * dimFactor).toInt().coerceIn(25, 200)
            canvas.drawPath(circuitPath, circuitPaint)

            // Animated data pulse traveling along inner trace
            val packetT = (phase * 2f + i * 0.125f) % 1f
            val pr = r0 + (r2 - r0) * packetT
            val pa = if (packetT < 0.5f) rad1 else rad2
            dotPaint.color = Color.WHITE
            dotPaint.alpha = (185 * dimFactor).toInt().coerceIn(20, 220)
            canvas.drawCircle(
                cx + (pr * cos(pa)).toFloat(),
                cy + (pr * sin(pa)).toFloat(),
                strokeBase * 1.3f,
                dotPaint
            )
        }
        canvas.restore()
    }

    private fun drawConcentricInternalRings(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        rotDeg: Float,
        cyan: Int,
        violet: Int,
        dimFactor: Float
    ) {
        val strokeThin = (sphereR * 0.015f).coerceAtLeast(1.1f)

        // Innermost luminous dotted cyan collar (matches reference image ring around core)
        val dotRingR = sphereR * 0.39f
        val dotCount = 32
        dotPaint.color = brightCyan
        dotPaint.alpha = (210 * dimFactor).toInt().coerceIn(40, 235)
        val dotSize = (sphereR * 0.018f).coerceAtLeast(1.3f)
        for (i in 0 until dotCount) {
            val theta = (rotDeg * 0.5f + i * (360f / dotCount)) * (PI / 180.0)
            canvas.drawCircle(
                cx + (dotRingR * cos(theta)).toFloat(),
                cy + (dotRingR * sin(theta)).toFloat(),
                dotSize,
                dotPaint
            )
        }

        // Multiple counter-rotating segmented concentric neon rings inside the glass
        data class RingSpec(
            val rFrac: Float,
            val speed: Float,
            val color: Int,
            val segments: Int,
            val sweep: Float,
            val strokeScale: Float
        )
        val specs = listOf(
            RingSpec(0.49f, -1.1f, violet, 3, 76f, 1.3f),
            RingSpec(0.60f, 0.85f, cyan, 4, 58f, 1.15f),
            RingSpec(0.71f, -0.65f, royalBlue, 5, 42f, 1.4f),
            RingSpec(0.82f, 0.5f, violet, 3, 82f, 1.1f),
            RingSpec(0.90f, -0.4f, cyan, 6, 36f, 1.0f)
        )

        for (spec in specs) {
            val r = sphereR * spec.rFrac
            rectF.set(cx - r, cy - r, cx + r, cy + r)
            ringPaint.color = spec.color
            ringPaint.strokeWidth = strokeThin * spec.strokeScale
            ringPaint.alpha = (165 * dimFactor).toInt().coerceIn(35, 220)
            val step = 360f / spec.segments
            for (s in 0 until spec.segments) {
                val startAngle = (rotDeg * spec.speed + s * step) % 360f
                canvas.drawArc(rectF, startAngle, spec.sweep, false, ringPaint)
            }
        }

        // Precision degree ticks at 0.76R
        val tickR1 = sphereR * 0.745f
        val tickR2 = sphereR * 0.775f
        ringPaint.color = cyan
        ringPaint.strokeWidth = strokeThin * 0.8f
        ringPaint.alpha = (115 * dimFactor).toInt().coerceIn(25, 170)
        for (i in 0 until 36) {
            val theta = (-rotDeg * 0.3f + i * 10f) * (PI / 180.0)
            val cosT = cos(theta).toFloat()
            val sinT = sin(theta).toFloat()
            canvas.drawLine(
                cx + tickR1 * cosT,
                cy + tickR1 * sinT,
                cx + tickR2 * cosT,
                cy + tickR2 * sinT,
                ringPaint
            )
        }
    }

    private fun drawInternalScanningWaves(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        rotDeg: Float,
        cyan: Int,
        violet: Int,
        dimFactor: Float
    ) {
        // Rotating holographic sweep wedge strictly inside sphereR
        canvas.save()
        canvas.rotate(rotDeg * 1.15f, cx, cy)
        canvas.drawCircle(cx, cy, sphereR * 0.96f, sweepWavePaint)
        canvas.restore()

        // Expanding radial scanning waves inside sphereR (fades out before hitting rim)
        val waveCount = if (orbState == OrbVisualState.LISTENING) 3 else 2
        scanRingPaint.strokeWidth = (sphereR * 0.020f).coerceAtLeast(1.4f)
        for (wIdx in 0 until waveCount) {
            val p = (phase * (if (orbState == OrbVisualState.LISTENING) 2f else 1f) + wIdx.toFloat() / waveCount) % 1f
            val waveR = sphereR * (0.24f + 0.70f * p)
            val fadeAlpha = ((1f - p) * (if (orbState == OrbVisualState.LISTENING) 215f else 125f) * dimFactor)
                .toInt()
                .coerceIn(0, 230)
            scanRingPaint.color = if (wIdx % 2 == 0) cyan else violet
            scanRingPaint.alpha = fadeAlpha
            canvas.drawCircle(cx, cy, waveR, scanRingPaint)
        }

        // State-specific internal audio waveform inside glass sphere (SPEAKING)
        if (orbState == OrbVisualState.SPEAKING) {
            val barCount = 28
            ringPaint.strokeWidth = (sphereR * 0.022f).coerceAtLeast(1.6f)
            for (i in 0 until barCount) {
                val theta = (i * (360f / barCount) + rotDeg * 0.4f) * (PI / 180.0)
                val mag = 0.3f + 0.7f * abs(sin(phase * 6f * PI.toFloat() + i * 0.6f))
                val rIn = sphereR * 0.30f
                val rOut = rIn + sphereR * (0.08f + (0.14f + audioLevel * 0.14f) * mag)
                ringPaint.color = if (i % 2 == 0) brightCyan else violet
                ringPaint.alpha = 215
                canvas.drawLine(
                    cx + (rIn * cos(theta)).toFloat(),
                    cy + (rIn * sin(theta)).toFloat(),
                    cx + (rOut * cos(theta)).toFloat(),
                    cy + (rOut * sin(theta)).toFloat(),
                    ringPaint
                )
            }
        }
    }

    private fun drawInternalParticles(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        rotDeg: Float,
        cyan: Int,
        violet: Int,
        dimFactor: Float
    ) {
        val tierCap = when (performanceTier) {
            ResolvedPerformanceTier.LOW_END -> 10
            ResolvedPerformanceTier.BALANCED -> 22
            ResolvedPerformanceTier.HIGH_PERFORMANCE -> internalParticles.size
        }
        val count = (tierCap * (particleIntensity / 1.5f)).toInt()
            .coerceIn(0, tierCap)
        if (count == 0) return

        val stateSpeed = if (orbState == OrbVisualState.THINKING) 2.1f else 1.0f
        for (i in 0 until count) {
            val p = internalParticles[i]
            val theta = (p.angleOffsetDeg + rotDeg * p.speedMul * stateSpeed) * (PI / 180.0)
            val r = sphereR * p.radiusFrac
            val px = cx + (r * cos(theta)).toFloat()
            val py = cy + (r * sin(theta)).toFloat()

            dotPaint.color = when (p.colorBand) {
                0 -> brightCyan
                1 -> violet
                else -> Color.WHITE
            }
            val shimmer = 0.55f + 0.45f * sin(phase * 4f * PI.toFloat() + i)
            dotPaint.alpha = (195 * shimmer * dimFactor).toInt().coerceIn(20, 235)
            canvas.drawCircle(px, py, (sphereR * 0.013f * p.sizeMul).coerceAtLeast(1.0f), dotPaint)
        }
    }

    private fun drawCentralCyanWhiteCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        pulse: Float,
        cyan: Int,
        violet: Int,
        dimFactor: Float
    ) {
        val boost = when (orbState) {
            OrbVisualState.LISTENING -> 0.12f + audioLevel * 0.18f
            OrbVisualState.SPEAKING -> 0.14f + audioLevel * 0.20f
            OrbVisualState.THINKING -> 0.10f
            else -> 0f
        }
        val coreScale = (0.94f + 0.12f * pulse + boost).coerceIn(0.85f, 1.32f)
        val haloR = sphereR * 0.36f * coreScale
        val coreR = sphereR * 0.18f * coreScale

        // Internal plasma halo (strictly contained inside 0.45*sphereR)
        coreHaloPaint.shader = RadialGradient(
            cx,
            cy,
            haloR,
            intArrayOf(
                Color.argb((220 * dimFactor).toInt(), Color.red(brightCyan), Color.green(brightCyan), Color.blue(brightCyan)),
                Color.argb((145 * dimFactor * glowIntensity.coerceAtMost(1.3f)).toInt(), Color.red(violet), Color.green(violet), Color.blue(violet)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.52f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, haloR, coreHaloPaint)

        // Bright cyan-white core sphere
        val centerColor = if (orbState == OrbVisualState.OFFLINE) brightCyan else Color.WHITE
        val midColor = if (orbState == OrbVisualState.ERROR) Color.rgb(255, 195, 208) else brightCyan
        coreOrbPaint.shader = RadialGradient(
            cx - coreR * 0.18f,
            cy - coreR * 0.18f,
            coreR,
            intArrayOf(centerColor, midColor, cyan),
            floatArrayOf(0.0f, 0.62f, 1.0f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreR, coreOrbPaint)
    }

    private fun drawCrystalGlassRim(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        sphereR: Float,
        cyan: Int,
        dimFactor: Float
    ) {
        // Crisp circular glass rim strictly inside clipPath
        val rimStroke = (sphereR * 0.026f).coerceAtLeast(1.8f)
        val rimRadius = sphereR - rimStroke * 0.5f
        glassRimPaint.color = cyan
        glassRimPaint.alpha = (185 * dimFactor).toInt().coerceIn(55, 230)
        glassRimPaint.strokeWidth = rimStroke
        canvas.drawCircle(cx, cy, rimRadius, glassRimPaint)

        // Upper-left bright crystal glass specular curve
        val specR = sphereR * 0.90f
        rectF.set(cx - specR, cy - specR, cx + specR, cy + specR)
        glassRimPaint.color = Color.WHITE
        glassRimPaint.alpha = (115 * dimFactor).toInt().coerceIn(30, 150)
        glassRimPaint.strokeWidth = (sphereR * 0.022f).coerceAtLeast(1.5f)
        canvas.drawArc(rectF, 198f, 64f, false, glassRimPaint)

        // Subtle iridescent upper-right highlight on crystal rim
        glassRimPaint.color = brightCyan
        glassRimPaint.alpha = (135 * dimFactor).toInt().coerceIn(30, 175)
        glassRimPaint.strokeWidth = (sphereR * 0.018f).coerceAtLeast(1.2f)
        canvas.drawArc(rectF, 315f, 44f, false, glassRimPaint)
    }
}
