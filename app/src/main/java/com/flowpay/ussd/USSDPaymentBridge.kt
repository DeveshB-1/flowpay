package com.flowpay.ussd

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * Bridge between the beautiful UI and the USSD engine.
 *
 * The UI calls methods like pay() and checkBalance().
 * This bridge translates them to USSD sequences,
 * executes them, and returns parsed results.
 *
 * From the UI's perspective, it's just:
 *   val result = bridge.pay("merchant@upi", 500, "Chai", "1234")
 *   if (result.success) showSuccess() else showError()
 */
class USSDPaymentBridge(private val context: Context) {

    private val engine = USSDEngine(context)

    /**
     * Pay to a UPI ID. Real money, real bank, no internet.
     */
    suspend fun payByUpi(
        payeeUpi: String,
        amount: Int,
        remarks: String = "",
        pin: String
    ): USSDResult = executeSequence(
        engine.buildPayByUpiSequence(payeeUpi, amount, remarks, pin)
    )

    /**
     * Pay to a phone number.
     */
    suspend fun payByPhone(
        phone: String,
        amount: Int,
        remarks: String = "",
        pin: String
    ): USSDResult = executeSequence(
        engine.buildPayByPhoneSequence(phone, amount, remarks, pin)
    )

    /**
     * Check account balance. Returns balance in paise.
     */
    suspend fun checkBalance(pin: String): USSDResult = executeSequence(
        engine.buildCheckBalanceSequence(pin)
    )

    /**
     * Get user's UPI ID from *99# profile.
     */
    suspend fun getProfile(): USSDResult = executeSequence(
        engine.buildViewProfileSequence()
    )

    /**
     * Get transaction history.
     */
    suspend fun getTransactions(): USSDResult = executeSequence(
        engine.buildTransactionHistorySequence()
    )

    /**
     * Change UPI PIN.
     */
    suspend fun changePin(oldPin: String, newPin: String): USSDResult = executeSequence(
        engine.buildChangePinSequence(oldPin, newPin)
    )

    /**
     * Check if the Accessibility Service is enabled.
     * Required for multi-step USSD automation.
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return USSDAccessibilityService.instance != null
    }

    /**
     * Check if *99# service is available (SIM present, GSM network).
     */
    fun isUSSDAvailable(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        return tm.simState == android.telephony.TelephonyManager.SIM_STATE_READY
    }

    /**
     * Execute a USSD sequence and wait for the result.
     */
    private suspend fun executeSequence(sequence: USSDSequence): USSDResult {
        return withTimeout(60_000) { // 60s timeout for full sequence
            suspendCancellableCoroutine { continuation ->
                // Set up completion callback
                USSDSessionManager.onComplete = { result ->
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                // Set up cancellation
                continuation.invokeOnCancellation {
                    USSDSessionManager.reset()
                }

                // Start the USSD session
                engine.initiateUSSD(sequence)
            }
        }
    }
}

/**
 * Response parser for common *99# responses.
 */
object USSDResponseParser {

    /**
     * Parse balance from *99# balance check response.
     * Example: "Your available balance is Rs. 15,234.50 in A/C XXXX1234"
     */
    fun parseBalance(response: String): Long? {
        val patterns = listOf(
            Regex("(?:rs\\.?|₹|inr)\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("balance\\s*:?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE),
            Regex("available\\s*:?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE),
        )

        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull() ?: continue
                return (amount * 100).toLong() // Convert to paise
            }
        }
        return null
    }

    /**
     * Parse transaction ID from payment response.
     * Example: "Transaction successful. UPI Ref No: 123456789012"
     */
    fun parseTransactionId(response: String): String? {
        val patterns = listOf(
            Regex("(?:ref|txn|transaction)\\s*(?:no|id|number)?\\s*:?\\s*(\\d{8,})", RegexOption.IGNORE_CASE),
            Regex("UPI\\s*(?:ref)?\\s*:?\\s*(\\d{8,})", RegexOption.IGNORE_CASE),
        )

        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    /**
     * Parse UPI ID from profile response.
     * Example: "Your UPI ID is: 9876543210@upi"
     */
    fun parseUpiId(response: String): String? {
        val match = Regex("([\\w.]+@[\\w]+)").find(response)
        return match?.value
    }

    /**
     * Check if a response indicates success.
     */
    fun isSuccess(response: String): Boolean {
        val successPatterns = listOf(
            "successful", "success", "completed", "done",
            "approved", "processed", "accepted"
        )
        return successPatterns.any { response.contains(it, ignoreCase = true) }
    }

    /**
     * Check if a response indicates failure.
     */
    fun isFailure(response: String): Boolean {
        val failurePatterns = listOf(
            "failed", "failure", "declined", "rejected",
            "insufficient", "invalid", "expired", "blocked",
            "error", "unable", "try again"
        )
        return failurePatterns.any { response.contains(it, ignoreCase = true) }
    }
}
