package com.flowpay.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Authorization token issued by the bank.
 * This is the user's "spending authority" — replaces the need
 * to contact the bank for each transaction.
 *
 * Stored in Android Keystore (hardware-backed).
 */
@Entity(tableName = "auth_tokens")
data class AuthorizationToken(
    @PrimaryKey val id: String,
    val userId: String,
    val upiId: String,            // user@bank
    val accountId: String,         // Masked account number
    val maxAmount: Long,           // In paise (bank-authorized ceiling)
    val spentAmount: Long,         // Running total of offline spends
    val issuedAt: Long,            // Epoch millis
    val validUntil: Long,          // Epoch millis (typically 24h)
    val bankPublicKey: ByteArray,  // Bank's Ed25519 public key
    val bankSignature: ByteArray,  // Bank's signature over this token
    val status: TokenStatus = TokenStatus.ACTIVE
) {
    val remaining: Long get() = maxAmount - spentAmount

    fun isExpired(): Boolean = Instant.now().toEpochMilli() > validUntil

    fun isValid(): Boolean = status == TokenStatus.ACTIVE && !isExpired()
}

enum class TokenStatus {
    ACTIVE, EXPIRED, REVOKED
}

/**
 * A signed payment intent — the core of offline payments.
 * This IS the payment. It's as good as money.
 *
 * Created offline, settled when online.
 */
@Entity(tableName = "payment_intents")
data class PaymentIntent(
    @PrimaryKey val txnId: String,
    val payerUpi: String,          // sender@bank
    val payeeUpi: String,          // merchant@bank
    val amount: Long,              // In paise
    val note: String,              // "Chai ☕" etc.
    val timestamp: Long,           // When payment was made
    val authTokenId: String,       // Which auth token authorized this
    val sequenceNumber: Long,      // Monotonic counter (anti-replay)
    val payerSignature: ByteArray, // Ed25519 signature by payer device
    val bankAuthProof: ByteArray,  // Bank's auth token signature
    val status: TransactionStatus,
    val createdOffline: Boolean,
    val settledAt: Long?           // Null until settled with bank
) {
    /**
     * Convert to signable data (deterministic byte representation).
     * This is what gets signed by the payer's device key.
     */
    fun toSignableData(): ByteArray {
        val data = "$txnId|$payerUpi|$payeeUpi|$amount|$timestamp|$authTokenId|$sequenceNumber"
        return data.toByteArray(Charsets.UTF_8)
    }
}

/**
 * Data class for signing (before we have the signature).
 */
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
    CREATED,    // Created offline, not yet delivered or settled
    DELIVERED,  // Delivered to merchant via NFC/BLE
    SETTLING,   // Settlement in progress
    SETTLED,    // Bank has debited/credited
    FAILED      // Settlement failed
}

/**
 * Settlement queue — offline transactions waiting to be settled.
 */
@Entity(tableName = "settlement_queue")
data class SettlementQueueEntry(
    @PrimaryKey val txnId: String,
    val paymentIntent: PaymentIntent,
    val attempts: Int,
    val nextAttemptAt: Long,        // Epoch millis
    val lastError: String? = null
)

/**
 * Local ledger entry — tracks balance changes.
 */
@Entity(tableName = "ledger")
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txnId: String,
    val type: LedgerType,
    val amount: Long,
    val balanceAfter: Long,
    val timestamp: Long = Instant.now().toEpochMilli()
)

enum class LedgerType {
    DEBIT,        // Outgoing payment
    CREDIT,       // Incoming payment
    AUTH_REFRESH,  // Balance updated from bank sync
    SETTLEMENT    // Confirmed by bank
}

/**
 * Contact / recent payee — cached for offline use.
 */
@Entity(tableName = "contacts")
data class UpiContact(
    @PrimaryKey val upiId: String,
    val displayName: String,
    val lastPaidAt: Long,
    val lastAmount: Long
)
