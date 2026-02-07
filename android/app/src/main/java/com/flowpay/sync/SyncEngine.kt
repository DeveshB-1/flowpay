package com.flowpay.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.flowpay.data.db.FlowPayDatabase
import com.flowpay.data.models.*
import com.flowpay.payment.AuthTokenStore
import kotlinx.coroutines.*
import java.time.Instant

/**
 * Background sync engine — settles offline transactions when connectivity returns.
 *
 * This runs silently. The user sees "Payment done" immediately.
 * When internet comes back, this engine:
 *   1. Drains the settlement queue
 *   2. Refreshes auth tokens
 *   3. Syncs balance with bank
 *   4. Updates transaction statuses
 */
class SyncEngine(
    private val context: Context,
    private val db: FlowPayDatabase,
    private val tokenStore: AuthTokenStore,
    private val settlementApi: SettlementApi
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    /**
     * Start listening for network connectivity changes.
     * When internet becomes available, automatically settle pending txns.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Internet is back! Settle everything.
                scope.launch {
                    settleAllPending()
                    refreshAuthToken()
                    syncBalance()
                }
            }
        })

        // Also try immediately on start
        if (isOnline()) {
            scope.launch {
                settleAllPending()
                refreshAuthToken()
                syncBalance()
            }
        }
    }

    /**
     * Settle all pending offline transactions.
     * Each transaction is submitted independently.
     * Failed ones are retried with exponential backoff.
     */
    private suspend fun settleAllPending() {
        val pending = db.settlementQueueDao().getAllPending()
        if (pending.isEmpty()) return

        for (entry in pending) {
            try {
                // Submit to settlement service
                val result = settlementApi.submitPaymentIntent(entry.paymentIntent)

                when (result) {
                    is SettlementResult.Success -> {
                        // Update transaction status
                        db.transactionDao().updateStatus(
                            txnId = entry.txnId,
                            status = TransactionStatus.SETTLED,
                            settledAt = Instant.now().toEpochMilli()
                        )
                        // Remove from queue
                        db.settlementQueueDao().remove(entry.txnId)

                        // Update ledger
                        db.ledgerDao().recordSettlement(
                            txnId = entry.txnId,
                            bankReference = result.bankReference
                        )
                    }

                    is SettlementResult.Failed -> {
                        // Retry with backoff
                        val newAttempts = entry.attempts + 1
                        val backoffMs = minOf(
                            (1000L * Math.pow(2.0, newAttempts.toDouble())).toLong(),
                            3600_000L // Max 1 hour
                        )

                        db.settlementQueueDao().updateRetry(
                            txnId = entry.txnId,
                            attempts = newAttempts,
                            nextAttemptAt = Instant.now().toEpochMilli() + backoffMs,
                            error = result.reason
                        )

                        if (newAttempts >= 10) {
                            // Too many failures — mark as failed
                            db.transactionDao().updateStatus(
                                txnId = entry.txnId,
                                status = TransactionStatus.FAILED,
                                settledAt = null
                            )
                            // Notify user
                            notifySettlementFailed(entry)
                        }
                    }
                }
            } catch (e: Exception) {
                // Network error during settlement — will retry next time
                continue
            }
        }
    }

    /**
     * Refresh the authorization token from the bank.
     * Gets a new token with updated balance.
     */
    private suspend fun refreshAuthToken() {
        try {
            val currentToken = tokenStore.getActiveToken()
            val newToken = settlementApi.refreshAuthToken(
                currentTokenId = currentToken?.id
            )

            if (newToken != null) {
                tokenStore.storeToken(newToken)
            }
        } catch (e: Exception) {
            // Failed to refresh — existing token still works until expiry
        }
    }

    /**
     * Sync local balance with bank's actual balance.
     * This reconciles any discrepancies.
     */
    private suspend fun syncBalance() {
        try {
            val bankBalance = settlementApi.getBalance()
            val localToken = tokenStore.getActiveToken() ?: return

            // If bank balance differs from what we think, update
            if (bankBalance != localToken.remaining) {
                db.ledgerDao().recordSync(
                    oldBalance = localToken.remaining,
                    newBalance = bankBalance
                )
            }
        } catch (e: Exception) {
            // Will sync next time
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun notifySettlementFailed(entry: SettlementQueueEntry) {
        // TODO: Send push notification to user about failed settlement
    }

    fun stop() {
        isRunning = false
        scope.cancel()
    }
}

// ── Settlement API interface ────────────────────

interface SettlementApi {
    /**
     * Submit a signed payment intent for settlement.
     * Backend will forward to NPCI → Banks.
     */
    suspend fun submitPaymentIntent(intent: PaymentIntent): SettlementResult

    /**
     * Get a fresh authorization token from the bank.
     */
    suspend fun refreshAuthToken(currentTokenId: String?): AuthorizationToken?

    /**
     * Get current bank balance.
     */
    suspend fun getBalance(): Long
}

sealed class SettlementResult {
    data class Success(
        val bankReference: String,
        val settledAt: Long
    ) : SettlementResult()

    data class Failed(
        val reason: String,
        val retryable: Boolean
    ) : SettlementResult()
}
