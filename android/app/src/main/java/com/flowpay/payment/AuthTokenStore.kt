package com.flowpay.payment

import com.flowpay.data.db.FlowPayDatabase
import com.flowpay.data.models.AuthorizationToken
import com.flowpay.data.models.TokenStatus

/**
 * Manages bank-issued authorization tokens.
 *
 * Tokens represent the bank's guarantee:
 * "This user can spend up to ₹X from their account."
 *
 * Token lifecycle:
 *   1. Bank issues token (during online sync)
 *   2. Each offline payment deducts from token.remaining
 *   3. Token expires after validity period (typically 24h)
 *   4. New token issued on next online sync
 */
class AuthTokenStore(private val db: FlowPayDatabase) {

    /**
     * Get the current active (non-expired) authorization token.
     * Returns null if no valid token exists — user must go online.
     */
    suspend fun getActiveToken(): AuthorizationToken? {
        val tokens = db.authTokenDao().getActiveTokens()
        return tokens.firstOrNull { it.isValid() }
    }

    /**
     * Store a new authorization token from the bank.
     * Expires any previous tokens.
     */
    suspend fun storeToken(token: AuthorizationToken) {
        // Expire all existing tokens
        db.authTokenDao().expireAll()

        // Store the new one
        db.authTokenDao().insert(token)
    }

    /**
     * Deduct amount from a token's remaining balance.
     * Called after each offline payment.
     */
    suspend fun deductAmount(tokenId: String, amount: Long) {
        db.authTokenDao().addSpentAmount(tokenId, amount)
    }

    /**
     * Get the remaining spending authority.
     */
    suspend fun getRemainingBalance(): Long {
        val token = getActiveToken() ?: return 0
        return token.remaining
    }

    /**
     * Check if the user has any valid spending authority.
     */
    suspend fun hasValidToken(): Boolean {
        return getActiveToken() != null
    }

    /**
     * Get time until current token expires (millis).
     * Returns 0 if no valid token.
     */
    suspend fun getTokenTimeRemaining(): Long {
        val token = getActiveToken() ?: return 0
        val remaining = token.validUntil - System.currentTimeMillis()
        return maxOf(0, remaining)
    }
}
