package com.flowpay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.models.Transaction
import com.flowpay.data.models.TransactionStatus
import com.flowpay.data.models.TransactionType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transaction: Transaction,
    onBack: () -> Unit
) {
    val isReceived = transaction.type == TransactionType.RECEIVED
    val displayName = if (isReceived) transaction.payerName else transaction.payeeName
    val displayUpi = if (isReceived) transaction.payerUpi else transaction.payeeUpi

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // Status icon
            val (statusColor, statusIcon) = when (transaction.status) {
                TransactionStatus.SETTLED -> Color(0xFF34A853) to Icons.Default.CheckCircle
                TransactionStatus.FAILED -> Color(0xFFEA4335) to Icons.Default.Cancel
                else -> Color(0xFFFF9800) to Icons.Default.Schedule
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = statusColor
                )
            }

            Spacer(Modifier.height(16.dp))

            // Amount
            val prefix = if (isReceived) "+" else "-"
            val amountColor = if (isReceived) Color(0xFF34A853) else MaterialTheme.colorScheme.onBackground
            Text(
                "${prefix}₹${formatAmount(transaction.amount)}",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )

            Spacer(Modifier.height(4.dp))

            // Status text
            val statusText = when (transaction.status) {
                TransactionStatus.SETTLED -> "Payment Successful"
                TransactionStatus.FAILED -> "Payment Failed"
                TransactionStatus.CREATED, TransactionStatus.DELIVERED -> "Processing"
                TransactionStatus.SETTLING -> "Settling"
            }
            Text(
                statusText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = statusColor
            )

            Spacer(Modifier.height(24.dp))

            // Transaction Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // To/From
                    DetailRow(
                        label = if (isReceived) "Received from" else "Paid to",
                        value = displayName
                    )
                    DetailRow(label = "UPI ID", value = displayUpi)

                    if (transaction.note.isNotEmpty()) {
                        DetailRow(label = "Note", value = transaction.note)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    DetailRow(label = "Transaction ID", value = transaction.txnId)
                    DetailRow(label = "Bank Reference", value = transaction.bankRef)
                    DetailRow(
                        label = "Date & Time",
                        value = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                            .format(Date(transaction.timestamp))
                    )
                    DetailRow(
                        label = "Payment Mode",
                        value = if (transaction.createdOffline) "Offline UPI" else "UPI"
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { /* Share */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = { /* Report */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Flag, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report")
                }
            }

            Spacer(Modifier.height(16.dp))

            // Repeat payment button
            if (!isReceived && transaction.status == TransactionStatus.SETTLED) {
                Button(
                    onClick = { /* Repeat payment */ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Icon(Icons.Default.Replay, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pay Again", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
