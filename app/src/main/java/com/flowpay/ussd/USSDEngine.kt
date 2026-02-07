package com.flowpay.ussd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * USSD Engine — Executes *99# commands for real UPI payments.
 *
 * Maps user actions to USSD sequences:
 *   Pay via UPI ID:  *99# → 1 → 2 → {upi_id} → {amount} → {note} → {pin}
 *   Pay via Phone:   *99# → 1 → 1 → {phone}  → {amount} → {note} → {pin}
 *   Check Balance:   *99# → 3 → {pin}
 *   View Profile:    *99# → 4 → 1
 *   Transactions:    *99# → 6
 */
class USSDEngine(private val context: Context) {

    companion object {
        const val USSD_CODE = "*99"
        const val TIMEOUT_MS = 30_000L
    }

    /**
     * Pay to a UPI ID.
     * USSD Sequence: *99# → 1 → 2 → {payeeUpi} → {amount} → {remarks} → {pin}
     */
    fun buildPayByUpiSequence(payeeUpi: String, amount: Int, remarks: String, pin: String): USSDSequence {
        return USSDSequence(
            name = "Pay ₹$amount to $payeeUpi",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("1"),              // Send Money
                USSDStep.Reply("2"),              // Via UPI ID
                USSDStep.Reply(payeeUpi),         // Enter UPI ID
                USSDStep.Reply(amount.toString()), // Enter Amount
                USSDStep.Reply(remarks.ifEmpty { "Payment" }), // Remarks
                USSDStep.Reply(pin),              // UPI PIN
            ),
            expectedResult = "successful|completed|done"
        )
    }

    /**
     * Pay to a phone number.
     * USSD Sequence: *99# → 1 → 1 → {phone} → {amount} → {remarks} → {pin}
     */
    fun buildPayByPhoneSequence(phone: String, amount: Int, remarks: String, pin: String): USSDSequence {
        return USSDSequence(
            name = "Pay ₹$amount to $phone",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("1"),              // Send Money
                USSDStep.Reply("1"),              // Via Mobile Number
                USSDStep.Reply(phone),            // Enter Phone
                USSDStep.Reply(amount.toString()), // Enter Amount
                USSDStep.Reply(remarks.ifEmpty { "Payment" }), // Remarks
                USSDStep.Reply(pin),              // UPI PIN
            ),
            expectedResult = "successful|completed|done"
        )
    }

    /**
     * Check account balance.
     * USSD Sequence: *99# → 3 → {pin}
     */
    fun buildCheckBalanceSequence(pin: String): USSDSequence {
        return USSDSequence(
            name = "Check Balance",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("3"),              // Check Balance
                USSDStep.Reply(pin),              // UPI PIN
            ),
            expectedResult = "balance|available"
        )
    }

    /**
     * View profile / UPI ID.
     * USSD Sequence: *99# → 4 → 1
     */
    fun buildViewProfileSequence(): USSDSequence {
        return USSDSequence(
            name = "View Profile",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("4"),              // My Profile
                USSDStep.Reply("1"),              // My UPI ID
            ),
            expectedResult = "@"  // UPI IDs contain @
        )
    }

    /**
     * View recent transactions.
     * USSD Sequence: *99# → 6
     */
    fun buildTransactionHistorySequence(): USSDSequence {
        return USSDSequence(
            name = "Transaction History",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("6"),              // Transactions
            ),
            expectedResult = "transaction|txn"
        )
    }

    /**
     * Set/Change UPI PIN.
     * USSD Sequence: *99# → 7 → 2 → {old_pin} → {new_pin} → {confirm_new_pin}
     */
    fun buildChangePinSequence(oldPin: String, newPin: String): USSDSequence {
        return USSDSequence(
            name = "Change UPI PIN",
            steps = listOf(
                USSDStep.Dial("*99${Uri.encode("#")}"),
                USSDStep.Reply("7"),              // UPI PIN
                USSDStep.Reply("2"),              // Change PIN
                USSDStep.Reply(oldPin),           // Old PIN
                USSDStep.Reply(newPin),           // New PIN
                USSDStep.Reply(newPin),           // Confirm New PIN
            ),
            expectedResult = "success|changed|updated"
        )
    }

    /**
     * Initiate a USSD session using Android's dialer.
     * The Accessibility Service will handle the multi-step interaction.
     */
    fun initiateUSSD(sequence: USSDSequence) {
        // Store the sequence for the Accessibility Service to execute
        USSDSessionManager.currentSequence = sequence
        USSDSessionManager.currentStep = 0
        USSDSessionManager.isActive = true
        USSDSessionManager.responses.clear()

        // Dial *99# — this opens the system USSD dialog
        val ussdUri = Uri.parse("tel:${sequence.steps.first().let { (it as USSDStep.Dial).code }}")
        val intent = Intent(Intent.ACTION_CALL, ussdUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Alternative: Use TelephonyManager for single-shot USSD (API 26+).
     * Limited — only works for simple USSD, not multi-step sessions.
     * But useful for initial *99# dial to check if service is available.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun sendSingleUSSD(code: String): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        return withTimeout(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                try {
                    telephonyManager.sendUssdRequest(
                        code,
                        object : TelephonyManager.UssdResponseCallback() {
                            override fun onReceiveUssdResponse(
                                tm: TelephonyManager, request: String, response: CharSequence
                            ) {
                                continuation.resume(response.toString())
                            }

                            override fun onReceiveUssdResponseFailed(
                                tm: TelephonyManager, request: String, failureCode: Int
                            ) {
                                continuation.resumeWithException(
                                    USSDException("USSD failed with code: $failureCode")
                                )
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (e: SecurityException) {
                    continuation.resumeWithException(
                        USSDException("Permission denied. Grant CALL_PHONE permission.")
                    )
                }
            }
        }
    }
}

// ── Data Models ─────────────────────────────────

/**
 * A complete USSD interaction sequence.
 */
data class USSDSequence(
    val name: String,
    val steps: List<USSDStep>,
    val expectedResult: String  // Regex pattern to match success in final response
)

/**
 * A single step in a USSD sequence.
 */
sealed class USSDStep {
    /** Dial a USSD code (first step) */
    data class Dial(val code: String) : USSDStep()

    /** Reply to a USSD prompt */
    data class Reply(val text: String) : USSDStep()
}

/**
 * Result of a USSD session.
 */
data class USSDResult(
    val success: Boolean,
    val responses: List<String>,   // All USSD responses received
    val finalResponse: String,     // Last response (usually the result)
    val transactionId: String?,    // Parsed from success response
    val balance: Long?,            // Parsed from balance check response
    val error: String?
)

class USSDException(message: String) : Exception(message)

/**
 * Singleton that manages the current USSD session state.
 * Shared between USSDEngine and USSDAccessibilityService.
 */
object USSDSessionManager {
    @Volatile var currentSequence: USSDSequence? = null
    @Volatile var currentStep: Int = 0
    @Volatile var isActive: Boolean = false
    val responses = mutableListOf<String>()

    @Volatile var onComplete: ((USSDResult) -> Unit)? = null
    @Volatile var onStepResponse: ((Int, String) -> Unit)? = null

    fun reset() {
        currentSequence = null
        currentStep = 0
        isActive = false
        responses.clear()
    }

    fun getNextReply(): String? {
        val seq = currentSequence ?: return null
        currentStep++

        return if (currentStep < seq.steps.size) {
            val step = seq.steps[currentStep]
            when (step) {
                is USSDStep.Reply -> step.text
                is USSDStep.Dial -> null // shouldn't happen mid-session
            }
        } else {
            null // Session complete
        }
    }

    fun addResponse(response: String) {
        responses.add(response)
        onStepResponse?.invoke(currentStep, response)
    }

    fun complete() {
        val seq = currentSequence
        val finalResponse = responses.lastOrNull() ?: ""

        val success = seq?.expectedResult?.let {
            Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(finalResponse)
        } ?: false

        val txnId = Regex("(?:txn|transaction|ref)\\s*(?:id|no)?\\s*:?\\s*(\\w+)",
            RegexOption.IGNORE_CASE).find(finalResponse)?.groupValues?.get(1)

        val balance = Regex("(?:balance|available)\\s*:?\\s*₹?\\s*([\\d,]+\\.?\\d*)",
            RegexOption.IGNORE_CASE).find(finalResponse)?.groupValues?.get(1)
            ?.replace(",", "")?.toDoubleOrNull()?.let { (it * 100).toLong() }

        val result = USSDResult(
            success = success,
            responses = responses.toList(),
            finalResponse = finalResponse,
            transactionId = txnId,
            balance = balance,
            error = if (!success) finalResponse else null
        )

        onComplete?.invoke(result)
        reset()
    }
}
