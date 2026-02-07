package com.flowpay.ui.screens

import androidx.compose.runtime.*
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.Transaction
import com.flowpay.data.models.TransactionStatus
import com.flowpay.data.models.TransactionType
import com.flowpay.payment.OfflinePaymentEngine
import com.flowpay.payment.PaymentResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun PayScreenWithEngine(
    payeeUpi: String,
    prefillAmount: Long,
    engine: OfflinePaymentEngine,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    var result by remember { mutableStateOf<PaymentResult?>(null) }
    var showResult by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PayScreen(
        payeeUpi = payeeUpi,
        prefillAmount = prefillAmount,
        onPaymentComplete = { onDone() },
        onCancel = onCancel,
        onPinSubmitted = { amount, pin, note ->
            scope.launch {
                delay(1200) // processing animation
                val payResult = engine.pay(
                    payeeUpi = payeeUpi,
                    amount = amount * 100,
                    note = note,
                    pin = pin
                )
                // Also add to rich transactions
                if (payResult is PaymentResult.Success) {
                    val payeeName = InMemoryStore.contacts.firstOrNull { it.upiId == payeeUpi }?.name ?: payeeUpi
                    val myUpi = InMemoryStore.userProfile?.upiId ?: "you@upi"
                    InMemoryStore.addRichTransaction(
                        Transaction(
                            txnId = payResult.paymentIntent.txnId,
                            payerUpi = myUpi,
                            payeeUpi = payeeUpi,
                            payerName = "You",
                            payeeName = payeeName,
                            amount = amount * 100,
                            note = note,
                            timestamp = System.currentTimeMillis(),
                            status = TransactionStatus.SETTLED,
                            type = TransactionType.PAID,
                            bankRef = "BNK${System.currentTimeMillis().toString().takeLast(9)}",
                            createdOffline = true
                        )
                    )
                }
                result = payResult
                showResult = true
            }
        },
        result = result,
        showResult = showResult
    )
}
