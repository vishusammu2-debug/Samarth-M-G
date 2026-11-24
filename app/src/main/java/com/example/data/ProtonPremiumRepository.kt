package com.example.data

import com.example.domain.PremiumMembershipState
import com.example.domain.PremiumPlanSpec
import com.example.domain.PremiumStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.URI
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

sealed interface EndpointValidationResult {
    data class Valid(val normalizedBaseUrl: String) : EndpointValidationResult
    data class Invalid(val reason: String) : EndpointValidationResult
}

sealed interface BeneficiaryValidationResult {
    data class Valid(val maskedUpi: String) : BeneficiaryValidationResult
    data class Invalid(val reason: String) : BeneficiaryValidationResult
}

sealed interface OrderCreationResult {
    data class Success(
        val orderId: String,
        val providerName: String,
        val checkoutUrl: String
    ) : OrderCreationResult

    data class Failure(val reason: String) : OrderCreationResult
}

sealed interface PaymentVerificationResult {
    data class Verified(
        val orderId: String,
        val paymentId: String,
        val providerName: String,
        val purchasedAtMillis: Long,
        val expiresAtMillis: Long
    ) : PaymentVerificationResult

    data class Unverified(val reason: String, val isPending: Boolean = false) : PaymentVerificationResult
}

class ProtonPremiumRepository(
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    suspend fun createAuthorizedOrder(
        membership: PremiumMembershipState
    ): OrderCreationResult = withContext(Dispatchers.IO) {
        val beneficiaryCheck = validateBeneficiaryConfig(
            upiHandle = membership.beneficiaryUpiHandle,
            accountLabel = membership.beneficiaryAccountLabel,
            permissionGranted = membership.beneficiaryPermissionGranted
        )
        if (beneficiaryCheck is BeneficiaryValidationResult.Invalid) {
            return@withContext OrderCreationResult.Failure(beneficiaryCheck.reason)
        }

        val endpointCheck = validateVerificationEndpoint(membership.verificationEndpoint)
        if (endpointCheck is EndpointValidationResult.Invalid) {
            return@withContext OrderCreationResult.Failure(endpointCheck.reason)
        }
        val baseUrl = (endpointCheck as EndpointValidationResult.Valid).normalizedBaseUrl

        val payloadJson = buildJsonObject(
            "planName" to PremiumPlanSpec.PLAN_NAME,
            "amountInr" to PremiumPlanSpec.PRICE_INR,
            "currency" to PremiumPlanSpec.CURRENCY,
            "durationMonths" to PremiumPlanSpec.DURATION_MONTHS,
            "autoRenew" to PremiumPlanSpec.AUTO_RENEWAL,
            "beneficiaryUpiHandle" to membership.beneficiaryUpiHandle.trim(),
            "beneficiaryAccountLabel" to membership.beneficiaryAccountLabel.trim(),
            "accountHolderPermissionConfirmed" to true
        )

        try {
            val request = Request.Builder()
                .url("$baseUrl/orders")
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext OrderCreationResult.Failure(
                        "Payment provider order creation failed (HTTP ${response.code}). Verify your HTTPS payment backend."
                    )
                }
                parseAndValidateOrderResponse(rawBody)
            }
        } catch (e: Exception) {
            OrderCreationResult.Failure(
                "Could not reach payment verification server: ${e.localizedMessage ?: "network error"}"
            )
        }
    }

    suspend fun verifyOrderWithBackend(
        verificationEndpoint: String,
        orderId: String,
        providerPaymentId: String? = null
    ): PaymentVerificationResult = withContext(Dispatchers.IO) {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isBlank()) {
            return@withContext PaymentVerificationResult.Unverified(
                "No pending provider order ID exists. Start a ₹5 checkout through the authorized provider first."
            )
        }

        val endpointCheck = validateVerificationEndpoint(verificationEndpoint)
        if (endpointCheck is EndpointValidationResult.Invalid) {
            return@withContext PaymentVerificationResult.Unverified(endpointCheck.reason)
        }
        val baseUrl = (endpointCheck as EndpointValidationResult.Valid).normalizedBaseUrl

        val fields = mutableListOf<Pair<String, Any?>>(
            "orderId" to cleanOrderId,
            "expectedAmountInr" to PremiumPlanSpec.PRICE_INR,
            "expectedCurrency" to PremiumPlanSpec.CURRENCY,
            "expectedDurationMonths" to PremiumPlanSpec.DURATION_MONTHS,
            "expectedAutoRenew" to PremiumPlanSpec.AUTO_RENEWAL
        )
        if (!providerPaymentId.isNullOrBlank()) {
            fields += "paymentId" to providerPaymentId.trim()
        }
        val payloadJson = buildJsonObject(*fields.toTypedArray())

        try {
            val request = Request.Builder()
                .url("$baseUrl/verify")
                .post(payloadJson.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext PaymentVerificationResult.Unverified(
                        "Server verification failed (HTTP ${response.code}). Premium remains locked."
                    )
                }
                parseAndValidateVerificationPayload(
                    jsonBody = rawBody,
                    expectedOrderId = cleanOrderId,
                    nowMillis = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            PaymentVerificationResult.Unverified(
                "Verification request failed: ${e.localizedMessage ?: "network error"}"
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val UPI_HANDLE_REGEX = Regex("^[a-zA-Z0-9._-]{2,64}@[a-zA-Z0-9.-]{2,64}$")

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        fun computeSixMonthExpiryMillis(startMillis: Long): Long {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.timeInMillis = startMillis
            calendar.add(Calendar.MONTH, PremiumPlanSpec.DURATION_MONTHS)
            return calendar.timeInMillis
        }

        fun maskUpiHandle(rawUpi: String): String {
            val clean = rawUpi.trim()
            val atIdx = clean.indexOf('@')
            if (atIdx <= 0) return ""
            val local = clean.substring(0, atIdx)
            val domain = clean.substring(atIdx)
            val prefix = if (local.length >= 2) local.take(2) else local.take(1)
            return "$prefix****$domain"
        }

        fun validateBeneficiaryConfig(
            upiHandle: String,
            accountLabel: String,
            permissionGranted: Boolean
        ): BeneficiaryValidationResult {
            if (!permissionGranted) {
                return BeneficiaryValidationResult.Invalid(
                    "Account holder permission is required before configuring or using the payment destination."
                )
            }
            val cleanUpi = upiHandle.trim()
            if (cleanUpi.isBlank()) {
                return BeneficiaryValidationResult.Invalid(
                    "Enter the intended Navi UPI ID (@naviaxis) and confirm account holder permission before continuing."
                )
            }
            if (!UPI_HANDLE_REGEX.matches(cleanUpi)) {
                return BeneficiaryValidationResult.Invalid(
                    "Invalid UPI ID format. Expected a valid UPI handle such as name@navi."
                )
            }
            if (accountLabel.trim().length < 2) {
                return BeneficiaryValidationResult.Invalid(
                    "Enter the beneficiary account holder label before authorizing."
                )
            }
            return BeneficiaryValidationResult.Valid(maskUpiHandle(cleanUpi))
        }

        fun validateVerificationEndpoint(rawUrl: String): EndpointValidationResult {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isBlank()) {
                return EndpointValidationResult.Invalid(
                    "Authorized HTTPS payment verification server URL is not configured."
                )
            }
            val uri = try {
                URI(trimmed)
            } catch (_: Exception) {
                return EndpointValidationResult.Invalid("Malformed verification server URL.")
            }

            if (!uri.scheme.equals("https", ignoreCase = true)) {
                return EndpointValidationResult.Invalid(
                    "Payment verification endpoint must use HTTPS."
                )
            }
            if (!uri.userInfo.isNullOrBlank()) {
                return EndpointValidationResult.Invalid(
                    "Embedded credentials in URLs are prohibited."
                )
            }

            val host = uri.host?.lowercase(Locale.ROOT)?.trim()
            if (host.isNullOrBlank()) {
                return EndpointValidationResult.Invalid("Verification URL must include a valid domain host.")
            }

            if (isDisallowedHost(host)) {
                return EndpointValidationResult.Invalid(
                    "Localhost, loopback, or private network verification URLs are prohibited."
                )
            }

            return EndpointValidationResult.Valid(trimmed)
        }

        private fun isDisallowedHost(host: String): Boolean {
            if (host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal")
            ) {
                return true
            }
            if (host == "127.0.0.1" || host == "0.0.0.0" || host == "::1" || host == "[::1]") {
                return true
            }
            if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("169.254.") ||
                host.startsWith("192.168.")
            ) {
                return true
            }
            val parts = host.split('.')
            if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
                val first = parts[0].toInt()
                val second = parts[1].toInt()
                if (first == 172 && second in 16..31) return true
                if (first == 100 && second in 64..127) return true
                if (first == 0) return true
            }
            if (host.startsWith("[") || host.contains(":")) {
                val bare = host.removePrefix("[").removeSuffix("]").lowercase(Locale.ROOT)
                if (bare == "::1" || bare.startsWith("fe80:") || bare.startsWith("fc") || bare.startsWith("fd")) {
                    return true
                }
            }
            val isIpLiteral = host.all { it.isDigit() || it == '.' || it == ':' || it == '[' || it == ']' }
            if (isIpLiteral) {
                return try {
                    val addr = InetAddress.getByName(host)
                    addr.isAnyLocalAddress || addr.isLoopbackAddress ||
                        addr.isLinkLocalAddress || addr.isSiteLocalAddress || addr.isMulticastAddress
                } catch (_: Exception) {
                    true
                }
            }
            return false
        }

        fun parseAndValidateOrderResponse(jsonBody: String): OrderCreationResult {
            return try {
                val obj = SimpleJsonParser(jsonBody).parseRootObject()
                val orderId = obj.string("orderId").trim()
                val provider = obj.string("provider").trim()
                val checkoutUrl = obj.string("checkoutUrl").trim()
                val amountInr = obj.int("amountInr", -1)
                val currency = obj.string("currency").trim().uppercase(Locale.ROOT)
                val durationMonths = obj.int("durationMonths", -1)
                val autoRenew = obj.bool("autoRenew", true)

                if (orderId.isBlank() || provider.isBlank()) {
                    return OrderCreationResult.Failure("Server did not return a valid orderId or provider.")
                }
                if (amountInr != PremiumPlanSpec.PRICE_INR || currency != PremiumPlanSpec.CURRENCY) {
                    return OrderCreationResult.Failure(
                        "Order pricing mismatch: expected ₹${PremiumPlanSpec.PRICE_INR} ${PremiumPlanSpec.CURRENCY}."
                    )
                }
                if (durationMonths != PremiumPlanSpec.DURATION_MONTHS || autoRenew) {
                    return OrderCreationResult.Failure(
                        "Order terms mismatch: must be 6 months one-time with autoRenew=false."
                    )
                }
                val checkoutValidation = validateVerificationEndpoint(checkoutUrl)
                if (checkoutValidation is EndpointValidationResult.Invalid) {
                    return OrderCreationResult.Failure("Invalid provider checkout URL: ${checkoutValidation.reason}")
                }
                OrderCreationResult.Success(
                    orderId = orderId,
                    providerName = provider,
                    checkoutUrl = (checkoutValidation as EndpointValidationResult.Valid).normalizedBaseUrl
                )
            } catch (_: Exception) {
                OrderCreationResult.Failure("Invalid JSON order response from payment server.")
            }
        }

        fun parseAndValidateVerificationPayload(
            jsonBody: String,
            expectedOrderId: String,
            nowMillis: Long
        ): PaymentVerificationResult {
            return try {
                val obj = SimpleJsonParser(jsonBody).parseRootObject()
                val verified = obj.bool("verified", false)
                val pending = obj.bool("pending", false)
                val statusText = obj.string("status").trim().uppercase(Locale.ROOT)
                if (!verified) {
                    val isStillPending = pending || statusText == "PENDING" || statusText == "CREATED"
                    val msg = obj.string("message").ifBlank {
                        if (isStillPending) {
                            "Payment is still pending confirmation from the provider."
                        } else {
                            "Payment not verified by payment provider."
                        }
                    }
                    return PaymentVerificationResult.Unverified(msg, isPending = isStillPending)
                }

                val orderId = obj.string("orderId").trim()
                val paymentId = obj.string("paymentId").trim()
                val provider = obj.string("provider").trim()
                val amountInr = obj.int("amountInr", -1)
                val currency = obj.string("currency").trim().uppercase(Locale.ROOT)
                val durationMonths = obj.int("durationMonths", -1)
                val autoRenew = obj.bool("autoRenew", true)
                val purchasedAt = obj.long("purchasedAtEpochMs", 0L)
                val serverExpiry = obj.long("expiresAtEpochMs", 0L)

                if (orderId.isBlank() || orderId != expectedOrderId.trim()) {
                    return PaymentVerificationResult.Unverified("Verified orderId does not match active order.")
                }
                if (paymentId.isBlank() || provider.isBlank()) {
                    return PaymentVerificationResult.Unverified("Provider paymentId or provider name missing.")
                }
                if (amountInr != PremiumPlanSpec.PRICE_INR || currency != PremiumPlanSpec.CURRENCY) {
                    return PaymentVerificationResult.Unverified(
                        "Payment amount mismatch: expected ₹${PremiumPlanSpec.PRICE_INR} ${PremiumPlanSpec.CURRENCY}."
                    )
                }
                if (durationMonths != PremiumPlanSpec.DURATION_MONTHS || autoRenew) {
                    return PaymentVerificationResult.Unverified(
                        "Plan duration/renewal mismatch: requires 6-month one-time pass with no auto-renewal."
                    )
                }
                if (purchasedAt <= 0L) {
                    return PaymentVerificationResult.Unverified("Missing purchase timestamp from server.")
                }

                val computedSixMonthExpiry = computeSixMonthExpiryMillis(purchasedAt)
                val resolvedExpiry = if (serverExpiry > purchasedAt) serverExpiry else computedSixMonthExpiry
                if (resolvedExpiry <= nowMillis) {
                    return PaymentVerificationResult.Unverified("Verified membership timestamp is already expired.")
                }

                PaymentVerificationResult.Verified(
                    orderId = orderId,
                    paymentId = paymentId,
                    providerName = provider,
                    purchasedAtMillis = purchasedAt,
                    expiresAtMillis = resolvedExpiry
                )
            } catch (_: Exception) {
                PaymentVerificationResult.Unverified("Invalid JSON response from verification server.")
            }
        }

        fun enforceExpiryState(
            membership: PremiumMembershipState,
            nowMillis: Long = System.currentTimeMillis()
        ): PremiumMembershipState {
            return if (membership.status == PremiumStatus.ACTIVE && membership.isMembershipExpired(nowMillis)) {
                membership.copy(
                    status = PremiumStatus.EXPIRED,
                    statusNote = "Your 6-month PROTON PREMIUM pass has expired. Renew with a one-time ₹5 payment."
                )
            } else {
                membership
            }
        }

        private fun buildJsonObject(vararg entries: Pair<String, Any?>): String {
            return entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                val encodedKey = "\"${escapeJson(key)}\""
                val encodedVal = when (value) {
                    null -> "null"
                    is Boolean, is Int, is Long -> value.toString()
                    else -> "\"${escapeJson(value.toString())}\""
                }
                "$encodedKey:$encodedVal"
            }
        }

        private fun escapeJson(raw: String): String {
            val sb = StringBuilder(raw.length)
            for (ch in raw) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            return sb.toString()
        }
    }
}

private fun Map<String, Any?>.string(key: String): String =
    (this[key] as? String).orEmpty()

private fun Map<String, Any?>.int(key: String, default: Int): Int {
    val num = this[key] as? Number ?: return default
    return num.toInt()
}

private fun Map<String, Any?>.long(key: String, default: Long): Long {
    val num = this[key] as? Number ?: return default
    return num.toLong()
}

private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean {
    return (this[key] as? Boolean) ?: default
}

private class SimpleJsonParser(private val src: String) {
    private var idx = 0

    fun parseRootObject(): Map<String, Any?> {
        skipWs()
        val obj = parseObject()
        skipWs()
        return obj
    }

    private fun parseValue(): Any? {
        skipWs()
        if (idx >= src.length) throw IllegalArgumentException("Unexpected EOF")
        return when (src[idx]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected char at $idx")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val map = LinkedHashMap<String, Any?>()
        skipWs()
        if (peek() == '}') {
            idx++
            return map
        }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWs()
            when (peek()) {
                ',' -> idx++
                '}' -> {
                    idx++
                    return map
                }
                else -> throw IllegalArgumentException("Expected , or } at $idx")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val list = ArrayList<Any?>()
        skipWs()
        if (peek() == ']') {
            idx++
            return list
        }
        while (true) {
            list.add(parseValue())
            skipWs()
            when (peek()) {
                ',' -> idx++
                ']' -> {
                    idx++
                    return list
                }
                else -> throw IllegalArgumentException("Expected , or ] at $idx")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (idx < src.length) {
            val c = src[idx++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (idx >= src.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = src[idx++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (idx + 4 > src.length) throw IllegalArgumentException("Bad unicode escape")
                            val hex = src.substring(idx, idx + 4)
                            sb.append(hex.toInt(16).toChar())
                            idx += 4
                        }
                        else -> throw IllegalArgumentException("Invalid escape: $esc")
                    }
                }
                else -> sb.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated string")
    }

    private fun parseNumber(): Number {
        val start = idx
        if (src[idx] == '-') idx++
        while (idx < src.length && (src[idx].isDigit() || src[idx] in listOf('.', 'e', 'E', '+', '-'))) {
            if ((src[idx] == '+' || src[idx] == '-') && src[idx - 1] !in listOf('e', 'E')) break
            idx++
        }
        val token = src.substring(start, idx)
        return if (token.contains('.') || token.contains('e', true)) {
            token.toDouble()
        } else {
            token.toLong()
        }
    }

    private fun parseLiteral(expected: String, result: Any?): Any? {
        if (!src.startsWith(expected, idx)) {
            throw IllegalArgumentException("Expected $expected at $idx")
        }
        idx += expected.length
        return result
    }

    private fun expect(ch: Char) {
        if (idx >= src.length || src[idx] != ch) {
            throw IllegalArgumentException("Expected $ch at $idx")
        }
        idx++
    }

    private fun peek(): Char = if (idx < src.length) src[idx] else '\u0000'

    private fun skipWs() {
        while (idx < src.length && src[idx].isWhitespace()) idx++
    }
}
