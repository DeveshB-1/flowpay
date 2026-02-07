package com.flowpay.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.payment.ErrorCode
import com.flowpay.payment.PaymentResult

/**
 * Payment screen — looks and feels exactly like GPay/PhonePe.
 * User has NO idea they're paying offline.
 *
 * Flow:
 *   1. Show payee info (from QR scan)
 *   2. Enter amount
 *   3. Enter UPI PIN
 *   4. ✅ Done (or ❌ Failed)
 */

@Composable
fun PayScreen(
    payeeUpi: String,
    payeeName: String = "",
    prefillAmount: Long = 0,
    onPaymentComplete: (PaymentResult) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(PayStep.AMOUNT) }
    var amount by remember { mutableStateOf(if (prefillAmount > 0) (prefillAmount / 100).toString() else "") }
    var note by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<PaymentResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Bar ──────────────────────────────────────
        TopAppBar(
            title = { Text("Pay") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "Cancel")
                }
            }
        )

        when (step) {
            PayStep.AMOUNT -> AmountEntry(
                payeeUpi = payeeUpi,
                payeeName = payeeName,
                amount = amount,
                note = note,
                onAmountChange = { amount = it },
                onNoteChange = { note = it },
                onProceed = { step = PayStep.PIN }
            )

            PayStep.PIN -> PinEntry(
                amount = amount,
                payeeUpi = payeeUpi,
                pin = pin,
                onPinChange = { pin = it },
                onSubmit = {
                    step = PayStep.PROCESSING
                    // Payment will be triggered by the ViewModel
                }
            )

            PayStep.PROCESSING -> ProcessingScreen(
                amount = amount,
                payeeUpi = payeeUpi
            )

            PayStep.RESULT -> ResultScreen(
                result = result!!,
                amount = amount,
                payeeUpi = payeeUpi,
                onDone = { onPaymentComplete(result!!) }
            )
        }
    }
}

// ── Amount Entry ────────────────────────────────

@Composable
private fun AmountEntry(
    payeeUpi: String,
    payeeName: String,
    amount: String,
    note: String,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Payee avatar
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = payeeName.firstOrNull()?.uppercase() ?: payeeUpi.first().uppercase(),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (payeeName.isNotEmpty()) payeeName else payeeUpi,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        if (payeeName.isNotEmpty()) {
            Text(
                text = payeeUpi,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Amount input
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "₹",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() }) onAmountChange(it) },
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier.width(200.dp),
                placeholder = {
                    Text(
                        "0",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note input
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("Add a note") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Pay button
        Button(
            onClick = onProceed,
            enabled = amount.isNotEmpty() && amount.toLongOrNull() != null && amount.toLong() > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Pay ₹$amount", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── PIN Entry ───────────────────────────────────

@Composable
private fun PinEntry(
    amount: String,
    payeeUpi: String,
    pin: String,
    onPinChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        Text("₹$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(payeeUpi, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(48.dp))

        Text("Enter UPI PIN", fontSize = 16.sp, fontWeight = FontWeight.Medium)

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        // Hidden PIN input
        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    onPinChange(it)
                    if (it.length == 6) onSubmit()
                }
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .size(1.dp) // Hidden — we show the dots above
                .padding(0.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        // Security footer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "Secured by FlowPay",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Processing ──────────────────────────────────

@Composable
private fun ProcessingScreen(amount: String, payeeUpi: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Processing ₹$amount", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(payeeUpi, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Result ──────────────────────────────────────

@Composable
private fun ResultScreen(
    result: PaymentResult,
    amount: String,
    payeeUpi: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (result) {
            is PaymentResult.Success -> {
                // Big green checkmark
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Success",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("₹$amount", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Paid to $payeeUpi",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Txn ID: ${result.paymentIntent.txnId.take(8)}...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is PaymentResult.Failed -> {
                // Red X
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF44336)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Failed",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Payment Failed", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    result.reason,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Done", fontSize = 16.sp)
        }
    }
}

// ── Payment Steps ───────────────────────────────

private enum class PayStep {
    AMOUNT, PIN, PROCESSING, RESULT
}
