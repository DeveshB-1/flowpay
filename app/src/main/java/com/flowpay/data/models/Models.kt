package com.flowpay.data.models

import java.time.Instant

// ── Authorization Token ─────────────────────────
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

// ── Payment Intent ──────────────────────────────
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

// ── Transaction (richer model for history) ──────
data class Transaction(
    val txnId: String,
    val payerUpi: String,
    val payeeUpi: String,
    val payerName: String,
    val payeeName: String,
    val amount: Long, // in paise
    val note: String,
    val timestamp: Long,
    val status: TransactionStatus,
    val type: TransactionType,
    val bankRef: String,
    val createdOffline: Boolean = false
)

enum class TransactionType {
    PAID, RECEIVED, PENDING_REQUEST
}

// ── Contact ─────────────────────────────────────
data class Contact(
    val name: String,
    val phone: String,
    val upiId: String,
    val isRecent: Boolean = false,
    val isFavorite: Boolean = false,
    val avatarColor: Long = 0xFF1A73E8
)

// ── Bank Account ────────────────────────────────
data class BankAccount(
    val id: String,
    val bankName: String,
    val bankShortCode: String,
    val accountNumber: String, // masked
    val ifsc: String,
    val balance: Long, // in paise
    val isPrimary: Boolean = false,
    val icon: String = "🏦",
    val color: Long = 0xFF1A73E8
)

// ── User Profile ────────────────────────────────
data class UserProfile(
    val name: String,
    val phone: String,
    val upiId: String,
    val email: String = "",
    val isSetupComplete: Boolean = false
)

// ── Money Request ───────────────────────────────
data class MoneyRequest(
    val id: String,
    val fromUpi: String,
    val fromName: String,
    val toUpi: String,
    val toName: String,
    val amount: Long,
    val note: String,
    val timestamp: Long,
    val status: RequestStatus
)

enum class RequestStatus {
    PENDING, ACCEPTED, DECLINED, EXPIRED
}

// ── Offer / Reward ──────────────────────────────
data class Offer(
    val id: String,
    val title: String,
    val description: String,
    val cashbackAmount: Long,
    val expiresAt: Long,
    val color: Long,
    val icon: String
)

data class ScratchCard(
    val id: String,
    val isScratched: Boolean = false,
    val rewardAmount: Long = 0,
    val timestamp: Long
)
