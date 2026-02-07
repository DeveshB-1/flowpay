package com.flowpay.data.db

import com.flowpay.data.models.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory data store for demo/prototype.
 * Replace with Room DB for production.
 */
object InMemoryStore {
    val transactions = CopyOnWriteArrayList<PaymentIntent>()
    val tokens = ConcurrentHashMap<String, AuthorizationToken>()
    val sequenceCounter = AtomicLong(0)

    fun getActiveToken(): AuthorizationToken? {
        return tokens.values.firstOrNull { it.isValid() }
    }

    fun addTransaction(intent: PaymentIntent) {
        transactions.add(0, intent)
    }

    fun getNextSequence(): Long = sequenceCounter.incrementAndGet()

    fun init(upiId: String, balance: Long) {
        val token = AuthorizationToken(
            id = "token-${System.currentTimeMillis()}",
            userId = "user-1",
            upiId = upiId,
            accountId = "XXXX1234",
            maxAmount = balance,
            spentAmount = 0,
            issuedAt = System.currentTimeMillis(),
            validUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
            status = TokenStatus.ACTIVE
        )
        tokens[token.id] = token
    }

    fun deductFromToken(tokenId: String, amount: Long) {
        tokens[tokenId]?.let { old ->
            tokens[tokenId] = old.copy(spentAmount = old.spentAmount + amount)
        }
    }
}
