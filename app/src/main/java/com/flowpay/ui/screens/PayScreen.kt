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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.payment.PaymentResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(
    payeeUpi: String,
    payeeName: String = "",
    prefillAmount: Long = 0,
    onPaymentComplete: (PaymentResult) -> Unit,
    onCancel: () -> Unit,
    onPinSubmitted: ((Long, String) -> Unit)? = null,
    result: PaymentResult? = null,
    showResult: Boolean = false
) {
    var step by remember { mutableStateOf(PayStep.AMOUNT) }
    var amount by remember { mutableStateOf(if (prefillAmount > 0) (prefillAmount / 100).toString() else "") }
    var note by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    // React to result
    LaunchedEffect(showResult) {
        if (showResult && result != null) {
            step = PayStep.RESULT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                onPinChange = {
                    pin = it
                    if (it.length >= 4) {
                        step = PayStep.PROCESSING
                        onPinSubmitted?.invoke(amount.toLong(), it)
                    }
                }
            )
            PayStep.PROCESSING -> ProcessingScreen(amount = amount, payeeUpi = payeeUpi)
            PayStep.RESULT -> ResultScreen(
                result = result!!,
                amount = amount,
                payeeUpi = payeeUpi,
                onDone = { onPaymentComplete(result) }
            )
        }
    }
}

@Composable
private fun AmountEntry(
    payeeUpi: String, payeeName: String, amount: String, note: String,
    onAmountChange: (String) -> Unit, onNoteChange: (String) -> Unit, onProceed: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (if (payeeName.isNotEmpty()) payeeName else payeeUpi).first().uppercase(),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(payeeUpi, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            Text("₹", fontSize = 36.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() }) onAmountChange(it) },
                textStyle = LocalTextStyle.current.copy(fontSize = 48.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                modifier = Modifier.width(200.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = note, onValueChange = onNoteChange,
            placeholder = { Text("Add a note") }, singleLine = true,
            shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth(0.7f)
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onProceed,
            enabled = amount.isNotEmpty() && (amount.toLongOrNull() ?: 0) > 0,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) { Text("Pay ₹$amount", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PinEntry(amount: String, payeeUpi: String, pin: String, onPinChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text("₹$amount", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(payeeUpi, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        Text("Enter UPI PIN", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier.size(16.dp).clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onPinChange(it) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.width(200.dp).focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text("Secured by FlowPay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProcessingScreen(amount: String, payeeUpi: String) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(48.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(24.dp))
        Text("Processing ₹$amount", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text(payeeUpi, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultScreen(result: PaymentResult, amount: String, payeeUpi: String, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally
    ) {
        when (result) {
            is PaymentResult.Success -> {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF4CAF50)),
                    Alignment.Center
                ) { Icon(Icons.Default.Check, "Success", tint = Color.White, modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.height(24.dp))
                Text("₹$amount", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Paid to $payeeUpi", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Txn: ${result.paymentIntent.txnId.take(8)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is PaymentResult.Failed -> {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(Color(0xFFF44336)),
                    Alignment.Center
                ) { Icon(Icons.Default.Close, "Failed", tint = Color.White, modifier = Modifier.size(48.dp)) }
                Spacer(Modifier.height(24.dp))
                Text("Payment Failed", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(result.reason, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onDone, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
            Text("Done", fontSize = 16.sp)
        }
    }
}

private enum class PayStep { AMOUNT, PIN, PROCESSING, RESULT }
