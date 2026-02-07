package com.flowpay.payment

import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.*
import java.util.UUID
import java.time.Instant

/**
 * Core offline payment engine.
 * Handles the complete payment flow without internet.
 */
class OfflinePaymentEngine {

    /**
     * Execute a payment. Works completely offline.
     */
    fun pay(
        payeeUpi: String,
        amount: Long,
        note: String = "",
        pin: String
    ): PaymentResult {

        // Step 1: Verify PIN (demo: "1234")
        if (pin != "1234" && pin != "123456") {
            return PaymentResult.Failed(
                reason = "Incorrect UPI PIN",
                code = ErrorCode.INVALID_PIN
            )
        }

        // Step 2: Get active authorization token
        val authToken = InMemoryStore.getActiveToken()
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
        val sequenceNumber = InMemoryStore.getNextSequence()
        val txnId = UUID.randomUUID().toString()

        val paymentIntent = PaymentIntent(
            txnId = txnId,
            payerUpi = authToken.upiId,
            payeeUpi = payeeUpi,
            amount = amount,
            note = note,
            timestamp = Instant.now().toEpochMilli(),
            authTokenId = authToken.id,
            sequenceNumber = sequenceNumber,
            payerSignature = ByteArray(0), // In production: sign with TEE key
            bankAuthProof = authToken.bankSignature,
            status = TransactionStatus.CREATED,
            createdOffline = true,
            settledAt = null
        )

        // Step 6: Deduct from local authorization
        InMemoryStore.deductFromToken(authToken.id, amount)

        // Step 7: Store transaction
        InMemoryStore.addTransaction(paymentIntent)

        return PaymentResult.Success(
            paymentIntent = paymentIntent,
            newBalance = authToken.remaining - amount
        )
    }

    fun getAvailableBalance(): Long {
        val token = InMemoryStore.getActiveToken() ?: return 0
        return if (token.isExpired()) 0 else token.remaining
    }

    fun getPendingCount(): Int {
        return InMemoryStore.transactions.count {
            it.status == TransactionStatus.CREATED || it.status == TransactionStatus.DELIVERED
        }
    }
}

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

enum class ErrorCode {
    INVALID_PIN, NO_AUTH_TOKEN, TOKEN_EXPIRED, INSUFFICIENT_FUNDS
}
