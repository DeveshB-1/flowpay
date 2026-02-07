package com.flowpay.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.db.InMemoryStore
import com.flowpay.payment.PaymentResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayScreen(
    payeeUpi: String,
    payeeName: String = "",
    prefillAmount: Long = 0,
    onPaymentComplete: (PaymentResult) -> Unit,
    onCancel: () -> Unit,
    onPinSubmitted: ((Long, String, String) -> Unit)? = null,
    result: PaymentResult? = null,
    showResult: Boolean = false
) {
    var step by remember { mutableStateOf(PayStep.AMOUNT) }
    var amount by remember { mutableStateOf(if (prefillAmount > 0) (prefillAmount / 100).toString() else "") }
    var note by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(showResult) {
        if (showResult && result != null) {
            step = PayStep.RESULT
        }
    }

    // Resolve payee name from contacts
    val resolvedName = remember(payeeUpi) {
        if (payeeName.isNotEmpty()) payeeName
        else InMemoryStore.contacts.firstOrNull { it.upiId == payeeUpi }?.name ?: ""
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
                payeeName = resolvedName,
                amount = amount,
                note = note,
                onAmountChange = { amount = it },
                onNoteChange = { note = it },
                onProceed = { step = PayStep.PIN }
            )
            PayStep.PIN -> PinEntry(
                amount = amount,
                payeeUpi = payeeUpi,
                payeeName = resolvedName,
                pin = pin,
                onPinChange = {
                    pin = it
                    if (it.length >= 4) {
                        step = PayStep.PROCESSING
                        onPinSubmitted?.invoke(amount.toLong(), it, note)
                    }
                }
            )
            PayStep.PROCESSING -> ProcessingScreen(amount = amount, payeeUpi = payeeUpi, payeeName = resolvedName)
            PayStep.RESULT -> ResultScreen(
                result = result!!,
                amount = amount,
                payeeUpi = payeeUpi,
                payeeName = resolvedName,
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
    val displayName = payeeName.ifEmpty { payeeUpi }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Payee avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                displayName.first().uppercase(),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(Modifier.height(12.dp))

        if (payeeName.isNotEmpty()) {
            Text(
                payeeName,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            payeeUpi,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        // Amount display
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "₹",
                fontSize = 36.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 7) onAmountChange(it) },
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
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        Spacer(Modifier.height(16.dp))

        // Note field
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("Add a note") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.7f),
            leadingIcon = { Icon(Icons.Default.StickyNote2, null, modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        Spacer(Modifier.weight(1f))

        // Bank info
        InMemoryStore.getPrimaryAccount()?.let { account ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Default.AccountBalance,
                    null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${account.bankName} • ${account.accountNumber}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onProceed,
            enabled = amount.isNotEmpty() && (amount.toLongOrNull() ?: 0) > 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                if (amount.isNotEmpty()) "Pay ₹$amount" else "Enter amount",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PinEntry(
    amount: String, payeeUpi: String, payeeName: String,
    pin: String, onPinChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "₹$amount",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (payeeName.isNotEmpty()) "to $payeeName" else "to $payeeUpi",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(48.dp))

        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter UPI PIN",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(24.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { i ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Hidden input
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onPinChange(it) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .width(200.dp)
                .focusRequester(focusRequester),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Shield,
                null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Secured by FlowPay",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProcessingScreen(amount: String, payeeUpi: String, payeeName: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "processing")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        Modifier.fillMaxSize(),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Sync,
                "Processing",
                modifier = Modifier
                    .size(40.dp)
                    .rotate(rotation),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Processing payment...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "₹$amount to ${payeeName.ifEmpty { payeeUpi }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Please don't close the app",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ResultScreen(
    result: PaymentResult, amount: String, payeeUpi: String, payeeName: String,
    onDone: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        Arrangement.Center,
        Alignment.CenterHorizontally
    ) {
        when (result) {
            is PaymentResult.Success -> {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34A853)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Check, "Success", tint = Color.White, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("₹$amount", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Paid to ${payeeName.ifEmpty { payeeUpi }}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Transaction ID: ${result.paymentIntent.txnId.take(12)}...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(32.dp))

                // Share button
                OutlinedButton(
                    onClick = { /* Share intent */ },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share Receipt")
                }
            }
            is PaymentResult.Failed -> {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEA4335)),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Close, "Failed", tint = Color.White, modifier = Modifier.size(52.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Payment Failed", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    result.reason,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDone,
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private enum class PayStep { AMOUNT, PIN, PROCESSING, RESULT }
