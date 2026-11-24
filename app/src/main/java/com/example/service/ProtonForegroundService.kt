package com.example.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ProtonPreferencesRepository
import com.example.data.SafePhoneControlExecutor
import com.example.domain.AccessibilityNavAction
import com.example.domain.FloatingBubbleConfig
import com.example.domain.OrbVisualState
import com.example.domain.PhoneControlAction
import com.example.domain.ResolvedPerformanceTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "ProtonForegroundService"

class ProtonForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsRepo: ProtonPreferencesRepository
    private lateinit var controlExecutor: SafePhoneControlExecutor
    private var windowManager: WindowManager? = null

    private var bubbleView: FloatingNeuralBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var quickControlsView: View? = null
    private var edgeSnapAnimator: ValueAnimator? = null

    private var currentConfig = FloatingBubbleConfig()
    private var currentOrbState = OrbVisualState.IDLE
    private var currentAudioLevel = 0f
    private var currentPerformanceTier = ResolvedPerformanceTier.BALANCED
    private var bubbleHiddenByUser = false

    override fun onCreate() {
        super.onCreate()
        prefsRepo = ProtonPreferencesRepository(applicationContext)
        controlExecutor = SafePhoneControlExecutor(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForegroundSafely()
        _isServiceRunning.value = true

        serviceScope.launch {
            prefsRepo.preferencesFlow.collect { prefs ->
                currentConfig = prefs.floatingBubble
                applyConfigUpdate(prefs.floatingBubble)
            }
        }

        serviceScope.launch {
            ProtonCoreStateBus.orbState.collect { orbState ->
                currentOrbState = orbState
                bubbleView?.orbState = orbState
            }
        }

        serviceScope.launch {
            ProtonCoreStateBus.audioLevel.collect { level ->
                currentAudioLevel = level
                bubbleView?.audioLevel = level
            }
        }

        serviceScope.launch {
            ProtonCoreStateBus.performanceTier.collect { tier ->
                currentPerformanceTier = tier
                bubbleView?.performanceTier = tier
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE_BUBBLE -> {
                bubbleHiddenByUser = true
                removeQuickControls()
                hideBubbleView()
                updateNotification()
            }
            ACTION_RESTORE_BUBBLE -> {
                bubbleHiddenByUser = false
                if (OverlayPermissionHelper.canDrawOverlays(this)) {
                    showOrUpdateBubbleView()
                }
                updateNotification()
            }
            ACTION_STOP_SERVICE -> {
                serviceScope.launch {
                    prefsRepo.setFloatingBubbleEnabled(false)
                }
                stopSelf()
            }
            else -> {
                if (OverlayPermissionHelper.canDrawOverlays(this) && !bubbleHiddenByUser) {
                    showOrUpdateBubbleView()
                }
                updateNotification()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _isServiceRunning.value = false
        _isBubbleVisible.value = false
        edgeSnapAnimator?.cancel()
        removeQuickControls()
        removeBubbleView()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun applyConfigUpdate(config: FloatingBubbleConfig) {
        if (!config.enabled) {
            removeQuickControls()
            removeBubbleView()
            stopSelf()
            return
        }

        if (!OverlayPermissionHelper.canDrawOverlays(this)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW revoked; removing bubble cleanly")
            removeQuickControls()
            removeBubbleView()
            updateNotification()
            return
        }

        if (bubbleHiddenByUser) {
            hideBubbleView()
        } else {
            showOrUpdateBubbleView()
        }
        updateNotification()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fgs_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val dashboardIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_PROTON_ACTION, ACTION_UI_DASHBOARD)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            this,
            101,
            dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleActionIntent = Intent(this, ProtonForegroundService::class.java).apply {
            action = if (bubbleHiddenByUser) ACTION_RESTORE_BUBBLE else ACTION_HIDE_BUBBLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            102,
            toggleActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ProtonForegroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (bubbleHiddenByUser || !OverlayPermissionHelper.canDrawOverlays(this)) {
            getString(R.string.fgs_notif_hidden)
        } else {
            getString(R.string.fgs_notif_visible)
        }

        val toggleLabel = if (bubbleHiddenByUser) {
            getString(R.string.fgs_action_restore)
        } else {
            getString(R.string.fgs_action_hide)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.fgs_notif_title))
            .setContentText(contentText)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleLabel, togglePendingIntent)
            .addAction(0, getString(R.string.fgs_action_dashboard), dashboardPendingIntent)
            .addAction(0, getString(R.string.fgs_action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showOrUpdateBubbleView() {
        if (!OverlayPermissionHelper.canDrawOverlays(this)) return
        val wm = windowManager ?: return
        val sizePx = dpToPx(currentConfig.sizeDp)

        if (bubbleView == null) {
            val view = FloatingNeuralBubbleView(this).apply {
                orbState = currentOrbState
                audioLevel = currentAudioLevel
                performanceTier = currentPerformanceTier
                rotatingRingsEnabled = currentConfig.rotatingRingsEnabled
                electricArcsEnabled = currentConfig.electricArcsEnabled
                animationSpeed = currentConfig.animationSpeed
                bubbleOpacity = currentConfig.opacity
                glowIntensity = currentConfig.glowIntensity
                particleIntensity = currentConfig.particleIntensity
            }

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dpToPx(14f)
                y = dpToPx(220f)
            }

            attachBubbleGestures(view, params, wm)

            try {
                wm.addView(view, params)
                bubbleView = view
                bubbleParams = params
                _isBubbleVisible.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to attach overlay orb", e)
                _isBubbleVisible.value = false
            }
        } else {
            val view = bubbleView ?: return
            val params = bubbleParams ?: return
            view.visibility = View.VISIBLE
            view.resumeAnimations()
            view.orbState = currentOrbState
            view.audioLevel = currentAudioLevel
            view.performanceTier = currentPerformanceTier
            view.rotatingRingsEnabled = currentConfig.rotatingRingsEnabled
            view.electricArcsEnabled = currentConfig.electricArcsEnabled
            view.animationSpeed = currentConfig.animationSpeed
            view.bubbleOpacity = currentConfig.opacity
            view.glowIntensity = currentConfig.glowIntensity
            view.particleIntensity = currentConfig.particleIntensity
            params.width = sizePx
            params.height = sizePx
            try {
                wm.updateViewLayout(view, params)
                _isBubbleVisible.value = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating overlay layout", e)
            }
        }
    }

    private fun hideBubbleView() {
        bubbleView?.let { view ->
            view.pauseAnimations()
            view.visibility = View.GONE
        }
        _isBubbleVisible.value = false
    }

    private fun removeBubbleView() {
        val view = bubbleView ?: return
        view.pauseAnimations()
        try {
            windowManager?.removeViewImmediate(view)
        } catch (e: Exception) {
            Log.w(TAG, "Error removing orb view", e)
        } finally {
            bubbleView = null
            bubbleParams = null
            _isBubbleVisible.value = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachBubbleGestures(
        view: FloatingNeuralBubbleView,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    removeQuickControls()
                    launchMainAction(ACTION_UI_ACTIVATE_VOICE)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    removeQuickControls()
                    launchMainAction(ACTION_UI_DASHBOARD)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    toggleQuickControlsOverlay()
                }
            }
        )

        var startX = 0
        var startY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        val touchSlopPx = dpToPx(8f)

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    edgeSnapAnimator?.cancel()
                    startX = params.x
                    startY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                        isDragging = true
                        removeQuickControls()
                    }
                    if (isDragging) {
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)
                        params.x = (startX + dx).coerceIn(0, maxX)
                        params.y = (startY + dy).coerceIn(0, maxY)
                        try {
                            wm.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        snapBubbleToNearestEdge(view, params, wm)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun snapBubbleToNearestEdge(
        view: FloatingNeuralBubbleView,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        val screenW = resources.displayMetrics.widthPixels
        val marginPx = dpToPx(12f)
        val center = params.x + params.width / 2
        val targetX = if (center < screenW / 2) {
            marginPx
        } else {
            (screenW - params.width - marginPx).coerceAtLeast(marginPx)
        }

        edgeSnapAnimator?.cancel()
        edgeSnapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 240L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                try {
                    if (view.isAttachedToWindow) {
                        wm.updateViewLayout(view, params)
                    }
                } catch (_: Exception) {
                }
            }
            start()
        }
    }

    private fun toggleQuickControlsOverlay() {
        if (quickControlsView != null) {
            removeQuickControls()
            return
        }
        if (!OverlayPermissionHelper.canDrawOverlays(this)) return
        val wm = windowManager ?: return
        val anchorParams = bubbleParams ?: return

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(10f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14f).toFloat()
                setColor(Color.parseColor("#EC051118"))
                setStroke(dpToPx(1.2f), Color.parseColor("#00FF88"))
            }
        }

        fun addControlItem(label: String, accentHex: String = "#E6F8FF", onClick: () -> Unit) {
            val tv = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor(accentHex))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val hPad = dpToPx(12f)
                val vPad = dpToPx(9f)
                setPadding(hPad, vPad, hPad, vPad)
                minWidth = dpToPx(176f)
                setOnClickListener {
                    removeQuickControls()
                    onClick()
                }
            }
            container.addView(tv)
        }

        addControlItem("Voice Ask", "#00FF88") { launchMainAction(ACTION_UI_ACTIVATE_VOICE) }
        addControlItem("Open Dashboard", "#38E8FF") { launchMainAction(ACTION_UI_DASHBOARD) }
        addControlItem("Toggle Flashlight") {
            controlExecutor.execute(PhoneControlAction.ToggleFlashlight(null))
        }

        if (ProtonAccessibilityService.isServiceEnabled(this)) {
            addControlItem("Nav: Back") {
                ProtonAccessibilityService.performUserRequestedAction(AccessibilityNavAction.BACK)
            }
            addControlItem("Nav: Home") {
                ProtonAccessibilityService.performUserRequestedAction(AccessibilityNavAction.HOME)
            }
            addControlItem("Nav: Recents") {
                ProtonAccessibilityService.performUserRequestedAction(AccessibilityNavAction.RECENTS)
            }
            addControlItem("Disable Accessibility", "#FF5E7E") {
                ProtonAccessibilityService.requestDisableService()
            }
        } else {
            addControlItem("Accessibility Settings", "#FFB74D") {
                launchMainAction(ACTION_UI_ACCESSIBILITY)
            }
        }

        addControlItem("Hide Orb", "#89B9D1") {
            bubbleHiddenByUser = true
            hideBubbleView()
            updateNotification()
        }
        addControlItem("Close Menu", "#4E7A93") { }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val screenW = resources.displayMetrics.widthPixels
        val panelWidth = dpToPx(204f)
        val panelX = if (anchorParams.x < screenW / 2) {
            (anchorParams.x + anchorParams.width + dpToPx(8f)).coerceAtMost(screenW - panelWidth - dpToPx(8f))
        } else {
            (anchorParams.x - panelWidth - dpToPx(8f)).coerceAtLeast(dpToPx(8f))
        }

        val panelParams = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelX
            y = anchorParams.y.coerceAtLeast(dpToPx(48f))
        }

        container.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removeQuickControls()
                true
            } else {
                false
            }
        }

        try {
            wm.addView(container, panelParams)
            quickControlsView = container
        } catch (e: Exception) {
            Log.e(TAG, "Unable to attach quick controls overlay", e)
        }
    }

    private fun removeQuickControls() {
        val view = quickControlsView ?: return
        try {
            windowManager?.removeViewImmediate(view)
        } catch (_: Exception) {
        } finally {
            quickControlsView = null
        }
    }

    private fun launchMainAction(uiAction: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_PROTON_ACTION, uiAction)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }

    companion object {
        private const val CHANNEL_ID = "proton_neural_core_channel"
        private const val NOTIFICATION_ID = 2048

        const val ACTION_HIDE_BUBBLE = "com.example.action.HIDE_BUBBLE"
        const val ACTION_RESTORE_BUBBLE = "com.example.action.RESTORE_BUBBLE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_PROTON_ACTION = "extra_proton_action"
        const val ACTION_UI_ACTIVATE_VOICE = "activate_voice"
        const val ACTION_UI_DASHBOARD = "open_dashboard"
        const val ACTION_UI_ACCESSIBILITY = "open_accessibility"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isBubbleVisible = MutableStateFlow(false)
        val isBubbleVisible: StateFlow<Boolean> = _isBubbleVisible.asStateFlow()

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, ProtonForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProtonForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
