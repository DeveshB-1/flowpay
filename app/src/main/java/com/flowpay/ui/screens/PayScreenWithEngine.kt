package com.flowpay.ui.screens

import androidx.compose.runtime.*
import com.flowpay.payment.OfflinePaymentEngine
import com.flowpay.payment.PaymentResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wrapper that connects PayScreen UI to the real OfflinePaymentEngine.
 */
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
        onPaymentComplete = { payResult ->
            onDone()
        },
        onCancel = onCancel,
        onPinSubmitted = { amount, pin ->
            scope.launch {
                // Small delay for UX (processing animation)
                delay(800)
                val payResult = engine.pay(
                    payeeUpi = payeeUpi,
                    amount = amount * 100, // Convert rupees to paise
                    note = "",
                    pin = pin
                )
                result = payResult
                showResult = true
            }
        },
        result = result,
        showResult = showResult
    )
}
