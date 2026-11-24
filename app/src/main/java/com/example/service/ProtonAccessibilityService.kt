package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import com.example.domain.AccessibilityNavAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Privacy-preserving AccessibilityService for Proton 2.0.
 * - Executes ONLY explicit user-requested global navigation/UI actions.
 * - `canRetrieveWindowContent` is false in XML config; never inspects passwords,
 *   private messages, or other apps' screen content.
 */
class ProtonAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: Proton never monitors other apps or reads screen content.
    }

    override fun onInterrupt() {
        // No continuous state or speech held here.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnected.value = false
        if (instance === this) {
            instance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isConnected.value = false
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun executeExplicitAction(action: AccessibilityNavAction): Boolean {
        val globalAction = when (action) {
            AccessibilityNavAction.BACK -> GLOBAL_ACTION_BACK
            AccessibilityNavAction.HOME -> GLOBAL_ACTION_HOME
            AccessibilityNavAction.RECENTS -> GLOBAL_ACTION_RECENTS
            AccessibilityNavAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            AccessibilityNavAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            AccessibilityNavAction.TAKE_SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    GLOBAL_ACTION_TAKE_SCREENSHOT
                } else {
                    return false
                }
            }
        }
        return performGlobalAction(globalAction)
    }

    companion object {
        @Volatile
        private var instance: ProtonAccessibilityService? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        fun performUserRequestedAction(action: AccessibilityNavAction): Boolean {
            return instance?.executeExplicitAction(action) ?: false
        }

        fun requestDisableService(): Boolean {
            val active = instance ?: return false
            active.disableSelf()
            _isConnected.value = false
            instance = null
            return true
        }

        fun isServiceEnabled(context: Context): Boolean {
            if (_isConnected.value && instance != null) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val expectedComponent = ComponentName(context, ProtonAccessibilityService::class.java)
            val enabledList = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
            val foundInManager = enabledList.any { info ->
                val svcInfo = info.resolveInfo?.serviceInfo
                svcInfo != null &&
                    svcInfo.packageName == expectedComponent.packageName &&
                    svcInfo.name == expectedComponent.className
            }
            if (foundInManager) return true

            val enabledSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            val flattened = expectedComponent.flattenToString()
            val shortFlattened = expectedComponent.flattenToShortString()
            return enabledSetting.split(':').any {
                it.equals(flattened, ignoreCase = true) || it.equals(shortFlattened, ignoreCase = true)
            }
        }

        fun createAccessibilitySettingsIntent(): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
