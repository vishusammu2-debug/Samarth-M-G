package com.example.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import com.example.ProtonApplication
import java.util.Locale

private const val TAG = "ProtonAppLauncher"

data class InstalledAppEntry(
    val appLabel: String,
    val normalizedLabel: String,
    val packageName: String,
    val launchActivityClass: String? = null
)

data class AppLaunchResult(
    val success: Boolean,
    val extractedAppName: String,
    val resolvedPackageName: String?,
    val resolvedAppLabel: String?,
    val message: String,
    val failureReason: String? = null
)

/**
 * Reliable Android application launcher using PackageManager, getLaunchIntentForPackage(),
 * dynamic installed-app indexing, Android Intent flags (FLAG_ACTIVITY_NEW_TASK),
 * foreground-activity awareness for Android 14/15 BAL rules, and detailed Logcat tracing.
 */
class ProtonAppLauncher(private val context: Context) {

    @Volatile
    private var cachedApps: List<InstalledAppEntry> = emptyList()

    @Volatile
    private var cacheTimestampMs: Long = 0L

    fun launchApplication(
        appQuery: String,
        knownPackages: List<String> = emptyList()
    ): AppLaunchResult {
        val cleanedQuery = cleanAppQuery(appQuery)
        safeLogI("[APP_LAUNCH] Extracted app name=\"$cleanedQuery\" (raw=\"$appQuery\")")

        if (cleanedQuery.isBlank()) {
            val reason = "EMPTY_APP_NAME"
            safeLogW("[APP_LAUNCH] Failure reason=$reason")
            return AppLaunchResult(
                success = false,
                extractedAppName = appQuery,
                resolvedPackageName = null,
                resolvedAppLabel = null,
                message = "Please tell me which application you want to open.",
                failureReason = reason
            )
        }

        val pm = context.packageManager

        // Step 1: Check explicit preferred packages if supplied (fast path)
        for (pkg in knownPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                val appLabel = resolveApplicationLabel(pm, pkg) ?: cleanedQuery
                safeLogI("[APP_LAUNCH] Resolved package=$pkg (known candidate) label=\"$appLabel\"")
                return executeLaunchIntent(
                    pm = pm,
                    launchIntent = launchIntent,
                    extractedAppName = cleanedQuery,
                    resolvedPackageName = pkg,
                    resolvedAppLabel = appLabel
                )
            }
        }

        // Step 2: Dynamically search all launchable apps installed on the device
        val installedApps = getInstalledLaunchableApps(pm)
        safeLogD("[APP_LAUNCH] Indexed ${installedApps.size} launchable apps for lookup")

        val matchedEntry = findBestAppMatch(cleanedQuery, installedApps)
        if (matchedEntry == null) {
            val reason = "APP_NOT_INSTALLED"
            safeLogW("[APP_LAUNCH] Resolved package=NONE for query=\"$cleanedQuery\" failure=$reason")
            return AppLaunchResult(
                success = false,
                extractedAppName = cleanedQuery,
                resolvedPackageName = null,
                resolvedAppLabel = null,
                message = "$cleanedQuery is not installed on this device.",
                failureReason = reason
            )
        }

        safeLogI("[APP_LAUNCH] Resolved package=${matchedEntry.packageName} label=\"${matchedEntry.appLabel}\"")

        // Step 3: Build launch Intent via getLaunchIntentForPackage() or explicit launcher component
        val launchIntent = pm.getLaunchIntentForPackage(matchedEntry.packageName)
            ?: matchedEntry.launchActivityClass?.let { activityClass ->
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(matchedEntry.packageName)
                    setClassName(matchedEntry.packageName, activityClass)
                }
            }

        if (launchIntent == null) {
            val reason = "NULL_LAUNCH_INTENT"
            safeLogW("[APP_LAUNCH] Failure reason=$reason for package=${matchedEntry.packageName}")
            return AppLaunchResult(
                success = false,
                extractedAppName = cleanedQuery,
                resolvedPackageName = matchedEntry.packageName,
                resolvedAppLabel = matchedEntry.appLabel,
                message = "${matchedEntry.appLabel} cannot be opened directly because Android returned no launcher entry.",
                failureReason = reason
            )
        }

        return executeLaunchIntent(
            pm = pm,
            launchIntent = launchIntent,
            extractedAppName = cleanedQuery,
            resolvedPackageName = matchedEntry.packageName,
            resolvedAppLabel = matchedEntry.appLabel
        )
    }

    private fun executeLaunchIntent(
        pm: PackageManager,
        launchIntent: Intent,
        extractedAppName: String,
        resolvedPackageName: String,
        resolvedAppLabel: String
    ): AppLaunchResult {
        val foregroundActivity: Activity? = ProtonApplication.currentResumedActivity()
        val launchContext: Context = foregroundActivity ?: context
        val isNonActivityContext = launchContext !is Activity

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // Always include FLAG_ACTIVITY_NEW_TASK when launching from Service / Overlay / Application context
        if (isNonActivityContext) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        safeLogI(
            "[APP_LAUNCH] Intent created package=$resolvedPackageName component=${launchIntent.component} " +
                "flags=0x${Integer.toHexString(launchIntent.flags)} fromActivity=${!isNonActivityContext}"
        )

        val resolvedComponent = launchIntent.resolveActivity(pm)
        if (resolvedComponent == null) {
            val reason = "UNRESOLVABLE_ACTIVITY"
            safeLogW("[APP_LAUNCH] Failure reason=$reason for package=$resolvedPackageName")
            return AppLaunchResult(
                success = false,
                extractedAppName = extractedAppName,
                resolvedPackageName = resolvedPackageName,
                resolvedAppLabel = resolvedAppLabel,
                message = "$resolvedAppLabel is not available to launch on this device.",
                failureReason = reason
            )
        }

        return try {
            launchContext.startActivity(launchIntent)
            safeLogI("[APP_LAUNCH] Launch result=ACCEPTED package=$resolvedPackageName label=\"$resolvedAppLabel\"")
            AppLaunchResult(
                success = true,
                extractedAppName = extractedAppName,
                resolvedPackageName = resolvedPackageName,
                resolvedAppLabel = resolvedAppLabel,
                message = "Opening $resolvedAppLabel."
            )
        } catch (e: SecurityException) {
            val reason = "SECURITY_OR_BAL_BLOCKED: ${e.message}"
            safeLogE("[APP_LAUNCH] Launch result=FAILED package=$resolvedPackageName reason=$reason", e)
            AppLaunchResult(
                success = false,
                extractedAppName = extractedAppName,
                resolvedPackageName = resolvedPackageName,
                resolvedAppLabel = resolvedAppLabel,
                message = "Android blocked launching $resolvedAppLabel from the background. Please bring Proton to the foreground and try again.",
                failureReason = reason
            )
        } catch (e: ActivityNotFoundException) {
            val reason = "ACTIVITY_NOT_FOUND: ${e.message}"
            safeLogE("[APP_LAUNCH] Launch result=FAILED package=$resolvedPackageName reason=$reason", e)
            AppLaunchResult(
                success = false,
                extractedAppName = extractedAppName,
                resolvedPackageName = resolvedPackageName,
                resolvedAppLabel = resolvedAppLabel,
                message = "$resolvedAppLabel is not installed or has no launchable screen.",
                failureReason = reason
            )
        } catch (e: Exception) {
            val reason = "${e.javaClass.simpleName}: ${e.message}"
            safeLogE("[APP_LAUNCH] Launch result=FAILED package=$resolvedPackageName reason=$reason", e)
            AppLaunchResult(
                success = false,
                extractedAppName = extractedAppName,
                resolvedPackageName = resolvedPackageName,
                resolvedAppLabel = resolvedAppLabel,
                message = "Could not open $resolvedAppLabel (${e.localizedMessage ?: "launch error"}).",
                failureReason = reason
            )
        }
    }

    private fun getInstalledLaunchableApps(pm: PackageManager): List<InstalledAppEntry> {
        val now = System.currentTimeMillis()
        if (cachedApps.isNotEmpty() && (now - cacheTimestampMs) < 60_000L) {
            return cachedApps
        }

        val entriesByPkg = LinkedHashMap<String, InstalledAppEntry>()

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }

            for (info in resolved) {
                val activityInfo = info.activityInfo ?: continue
                val pkg = activityInfo.packageName ?: continue
                val activityLabel = info.loadLabel(pm).toString().trim()
                val appInfoLabel = activityInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString().trim() }
                val bestLabel = activityLabel.ifBlank { appInfoLabel.orEmpty() }
                if (bestLabel.isBlank()) continue

                entriesByPkg.putIfAbsent(
                    pkg,
                    InstalledAppEntry(
                        appLabel = bestLabel,
                        normalizedLabel = normalizeForMatch(bestLabel),
                        packageName = pkg,
                        launchActivityClass = activityInfo.name
                    )
                )

                // Also add application-info label variant when launcher label differs (e.g., "Play Store" vs "Google Play Store")
                if (!appInfoLabel.isNullOrBlank() &&
                    !appInfoLabel.equals(bestLabel, ignoreCase = true)
                ) {
                    entriesByPkg["$pkg#app"] = InstalledAppEntry(
                        appLabel = appInfoLabel,
                        normalizedLabel = normalizeForMatch(appInfoLabel),
                        packageName = pkg,
                        launchActivityClass = activityInfo.name
                    )
                }
            }

            // Secondary sweep via installed applications in case an OEM launcher filter hid an app
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }

            for (appInfo in installedApps) {
                val pkg = appInfo.packageName ?: continue
                if (entriesByPkg.containsKey(pkg)) continue
                if (pm.getLaunchIntentForPackage(pkg) != null) {
                    val label = pm.getApplicationLabel(appInfo).toString().trim()
                    if (label.isNotBlank()) {
                        entriesByPkg[pkg] = InstalledAppEntry(
                            appLabel = label,
                            normalizedLabel = normalizeForMatch(label),
                            packageName = pkg,
                            launchActivityClass = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            safeLogW("[APP_LAUNCH] Error indexing installed apps: ${e.message}")
        }

        val list = entriesByPkg.values.toList()
        if (list.isNotEmpty()) {
            cachedApps = list
            cacheTimestampMs = now
        }
        return list
    }

    private fun resolveApplicationLabel(pm: PackageManager, packageName: String): String? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString().trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val COMMON_ALIASES: Map<String, List<String>> = mapOf(
            "play store" to listOf("google play store", "play store", "google play"),
            "google play" to listOf("google play store", "play store"),
            "google play store" to listOf("google play store", "play store"),
            "youtube" to listOf("youtube", "yt"),
            "chrome" to listOf("chrome", "google chrome"),
            "gmail" to listOf("gmail", "mail"),
            "whatsapp" to listOf("whatsapp", "whatsapp messenger", "whatsapp business"),
            "instagram" to listOf("instagram"),
            "maps" to listOf("maps", "google maps"),
            "gallery" to listOf("photos", "gallery", "google photos"),
            "messages" to listOf("messages", "google messages"),
            "files" to listOf("files", "files by google", "my files"),
            "calculator" to listOf("calculator"),
            "calendar" to listOf("calendar", "google calendar"),
            "clock" to listOf("clock")
        )

        fun cleanAppQuery(raw: String): String {
            return raw
                .trim()
                .replace(Regex("[.?!,;:\"'`~]+$"), "")
                .replace(Regex("\\s+(app|application|please|now|for me)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("[.?!,;:\"'`~]+$"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun normalizeForMatch(text: String): String {
            return text
                .lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun findBestAppMatch(
            appQuery: String,
            installedApps: List<InstalledAppEntry>
        ): InstalledAppEntry? {
            if (installedApps.isEmpty()) return null
            val normQuery = normalizeForMatch(cleanAppQuery(appQuery))
            if (normQuery.isBlank()) return null

            val candidateTerms = buildList {
                add(normQuery)
                COMMON_ALIASES[normQuery]?.forEach { alias ->
                    val normAlias = normalizeForMatch(alias)
                    if (normAlias != normQuery) add(normAlias)
                }
            }

            // 1. Exact normalized label match
            for (term in candidateTerms) {
                installedApps.firstOrNull { it.normalizedLabel == term }?.let { return it }
            }

            // 2. Prefix match ("chrome" -> "chrome beta", "google play" -> "google play store")
            for (term in candidateTerms) {
                installedApps.firstOrNull { it.normalizedLabel.startsWith(term) }?.let { return it }
            }

            // 3. Word-boundary / whole-token match ("play store" inside "google play store")
            for (term in candidateTerms) {
                val boundaryRegex = Regex("(^|\\s)${Regex.escape(term)}(\\s|$)")
                installedApps.firstOrNull { boundaryRegex.containsMatchIn(it.normalizedLabel) }?.let { return it }
            }

            // 4. Compact alphanumeric match ("playstore" -> "google play store" / "whats app" -> "whatsapp")
            val compactQuery = normQuery.replace(" ", "")
            if (compactQuery.length >= 3) {
                installedApps.firstOrNull {
                    it.normalizedLabel.replace(" ", "").contains(compactQuery)
                }?.let { return it }
            }

            // 5. Substring match on label or package segment (e.g. "whatsapp" matches com.whatsapp)
            installedApps.firstOrNull { it.normalizedLabel.contains(normQuery) }?.let { return it }
            if (compactQuery.length >= 4) {
                installedApps.firstOrNull { entry ->
                    entry.packageName.lowercase(Locale.ROOT)
                        .split('.')
                        .any { seg -> seg == compactQuery || seg.contains(compactQuery) }
                }?.let { return it }
            }

            return null
        }
    }

    private fun safeLogD(msg: String) {
        try { Log.d(TAG, msg) } catch (_: Throwable) { }
    }

    private fun safeLogI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) { }
    }

    private fun safeLogW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) { }
    }

    private fun safeLogE(msg: String, t: Throwable) {
        try { Log.e(TAG, msg, t) } catch (_: Throwable) { }
    }
}
