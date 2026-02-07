package com.flowpay.payment

import com.flowpay.data.crypto.CryptoEngine
import com.flowpay.data.db.FlowPayDatabase
import com.flowpay.data.models.*
import java.util.UUID
import java.time.Instant

/**
 * Core offline payment engine.
 * Handles the complete payment flow without internet.
 *
 * Payment completes LOCALLY and INSTANTLY.
 * Settlement happens in background when online.
 */
class OfflinePaymentEngine(
    private val db: FlowPayDatabase,
    private val crypto: CryptoEngine,
    private val tokenStore: AuthTokenStore
) {

    /**
     * Execute a payment. Works completely offline.
     *
     * @param payeeUpi  Merchant/recipient UPI ID (from QR scan or input)
     * @param amount    Amount in paise (₹500 = 50000)
     * @param note      Optional payment note
     * @param pin       User's UPI PIN (verified locally)
     * @return PaymentResult — success with signed intent, or failure reason
     */
    suspend fun pay(
        payeeUpi: String,
        amount: Long,
        note: String = "",
        pin: String
    ): PaymentResult {

        // Step 1: Verify UPI PIN locally (against TEE-stored hash)
        if (!crypto.verifyPin(pin)) {
            return PaymentResult.Failed(
                reason = "Incorrect UPI PIN",
                code = ErrorCode.INVALID_PIN
            )
        }

        // Step 2: Get active authorization token
        val authToken = tokenStore.getActiveToken()
            ?: return PaymentResult.Failed(
                reason = "No active authorization. Please go online to refresh.",
                code = ErrorCode.NO_AUTH_TOKEN
            )

        // Step 3: Check if token is still valid
        if (authToken.isExpired()) {
            return PaymentResult.Failed(
                reason = "Authorization expired. Please go online to refresh.",
                code = ErrorCode.TOKEN_EXPIRED
            )
        }

        // Step 4: Check spending limit
        if (amount > authToken.remaining) {
            return PaymentResult.Failed(
                reason = "Insufficient balance",
                code = ErrorCode.INSUFFICIENT_FUNDS
            )
        }

        // Step 5: Create payment intent
        val sequenceNumber = db.transactionDao().getNextSequenceNumber()
        val txnId = UUID.randomUUID().toString()

        val paymentData = PaymentIntentData(
            txnId = txnId,
            payerUpi = authToken.upiId,
            payeeUpi = payeeUpi,
            amount = amount,
            note = note,
            timestamp = Instant.now().toEpochMilli(),
            authTokenId = authToken.id,
            sequenceNumber = sequenceNumber
        )

        // Step 6: Sign the payment intent with device key (in TEE)
        val payerSignature = crypto.signPaymentIntent(paymentData)

        val paymentIntent = PaymentIntent(
            txnId = txnId,
            payerUpi = authToken.upiId,
            payeeUpi = payeeUpi,
            amount = amount,
            note = note,
            timestamp = paymentData.timestamp,
            authTokenId = authToken.id,
            sequenceNumber = sequenceNumber,
            payerSignature = payerSignature,
            bankAuthProof = authToken.bankSignature,
            status = TransactionStatus.CREATED,
            createdOffline = true,
            settledAt = null
        )

        // Step 7: Deduct from local authorization
        tokenStore.deductAmount(authToken.id, amount)

        // Step 8: Store in local DB (encrypted)
        db.transactionDao().insert(paymentIntent)

        // Step 9: Queue for settlement when online
        db.settlementQueueDao().enqueue(
            SettlementQueueEntry(
                txnId = txnId,
                paymentIntent = paymentIntent,
                attempts = 0,
                nextAttemptAt = 0 // Settle ASAP when online
            )
        )

        // Step 10: Update local ledger
        db.ledgerDao().recordDebit(
            amount = amount,
            txnId = txnId,
            newBalance = authToken.remaining - amount
        )

        return PaymentResult.Success(
            paymentIntent = paymentIntent,
            newBalance = authToken.remaining - amount
        )
    }

    /**
     * Verify an incoming payment (merchant side).
     * Validates all cryptographic signatures offline.
     */
    fun verifyIncomingPayment(intent: PaymentIntent): VerificationResult {
        // Verify bank's authorization token signature
        val bankKeyValid = crypto.verifyBankSignature(
            authTokenId = intent.authTokenId,
            signature = intent.bankAuthProof
        )
        if (!bankKeyValid) {
            return VerificationResult.Invalid("Bank authorization signature invalid")
        }

        // Verify payer's signature on the payment intent
        val payerSigValid = crypto.verifyPayerSignature(
            intentData = intent.toSignableData(),
            signature = intent.payerSignature
        )
        if (!payerSigValid) {
            return VerificationResult.Invalid("Payer signature invalid")
        }

        // Check timestamp is recent (within 24h)
        val age = Instant.now().toEpochMilli() - intent.timestamp
        if (age > 24 * 60 * 60 * 1000) {
            return VerificationResult.Invalid("Payment intent expired")
        }

        // Check amount is positive
        if (intent.amount <= 0) {
            return VerificationResult.Invalid("Invalid amount")
        }

        return VerificationResult.Valid(
            txnId = intent.txnId,
            payerUpi = intent.payerUpi,
            amount = intent.amount,
            note = intent.note
        )
    }

    /**
     * Get remaining spending authority (local, instant).
     */
    suspend fun getAvailableBalance(): Long {
        val token = tokenStore.getActiveToken() ?: return 0
        return if (token.isExpired()) 0 else token.remaining
    }

    /**
     * Get pending (unsettled) transaction count.
     */
    suspend fun getPendingCount(): Int {
        return db.settlementQueueDao().getPendingCount()
    }
}

// ── Result Types ────────────────────────────────

sealed class PaymentResult {
    data class Success(
        val paymentIntent: PaymentIntent,
        val newBalance: Long
    ) : PaymentResult()

    data class Failed(
        val reason: String,
        val code: ErrorCode
    ) : PaymentResult()
}

sealed class VerificationResult {
    data class Valid(
        val txnId: String,
        val payerUpi: String,
        val amount: Long,
        val note: String
    ) : VerificationResult()

    data class Invalid(val reason: String) : VerificationResult()
}

enum class ErrorCode {
    INVALID_PIN,
    NO_AUTH_TOKEN,
    TOKEN_EXPIRED,
    INSUFFICIENT_FUNDS,
    SIGNING_FAILED,
    INTERNAL_ERROR
}
