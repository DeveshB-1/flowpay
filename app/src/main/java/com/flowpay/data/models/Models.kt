package com.flowpay.data.models

import java.time.Instant

/**
 * Authorization token issued by the bank.
 */
data class AuthorizationToken(
    val id: String,
    val userId: String,
    val upiId: String,
    val accountId: String,
    val maxAmount: Long,
    val spentAmount: Long,
    val issuedAt: Long,
    val validUntil: Long,
    val bankPublicKey: ByteArray = ByteArray(0),
    val bankSignature: ByteArray = ByteArray(0),
    val status: TokenStatus = TokenStatus.ACTIVE
) {
    val remaining: Long get() = maxAmount - spentAmount
    fun isExpired(): Boolean = Instant.now().toEpochMilli() > validUntil
    fun isValid(): Boolean = status == TokenStatus.ACTIVE && !isExpired()
}

enum class TokenStatus { ACTIVE, EXPIRED, REVOKED }

/**
 * A signed payment intent — the core of offline payments.
 */
data class PaymentIntent(
    val txnId: String,
    val payerUpi: String,
    val payeeUpi: String,
    val amount: Long,
    val note: String,
    val timestamp: Long,
    val authTokenId: String,
    val sequenceNumber: Long,
    val payerSignature: ByteArray = ByteArray(0),
    val bankAuthProof: ByteArray = ByteArray(0),
    val status: TransactionStatus,
    val createdOffline: Boolean,
    val settledAt: Long?
) {
    fun toSignableData(): ByteArray {
        val data = "$txnId|$payerUpi|$payeeUpi|$amount|$timestamp|$authTokenId|$sequenceNumber"
        return data.toByteArray(Charsets.UTF_8)
    }
}

data class PaymentIntentData(
    val txnId: String,
    val payerUpi: String,
    val payeeUpi: String,
    val amount: Long,
    val note: String,
    val timestamp: Long,
    val authTokenId: String,
    val sequenceNumber: Long
) {
    fun toSignableBytes(): ByteArray {
        val data = "$txnId|$payerUpi|$payeeUpi|$amount|$timestamp|$authTokenId|$sequenceNumber"
        return data.toByteArray(Charsets.UTF_8)
    }
}

enum class TransactionStatus {
    CREATED, DELIVERED, SETTLING, SETTLED, FAILED
}
