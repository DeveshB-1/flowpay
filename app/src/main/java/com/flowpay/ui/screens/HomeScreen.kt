package com.flowpay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.models.PaymentIntent
import com.flowpay.data.models.TransactionStatus

/**
 * Home screen — the main screen users see.
 * Shows balance, quick actions, recent transactions.
 * Looks like GPay home screen.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    balance: Long,                            // In paise
    isOnline: Boolean,
    pendingSettlements: Int,
    recentTransactions: List<PaymentIntent>,
    recentContacts: List<QuickContact>,
    onScanQR: () -> Unit,
    onPayContact: (String) -> Unit,
    onPayNew: () -> Unit,
    onViewHistory: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "FlowPay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        if (!isOnline) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(containerColor = Color(0xFFFF9800)) {
                                Text("Offline", fontSize = 10.sp)
                            }
                        }
                    }
                },
                actions = {
                    // Pending settlements indicator
                    if (pendingSettlements > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text("$pendingSettlements") }
                            }
                        ) {
                            Icon(Icons.Default.Sync, "Pending settlements")
                        }
                    }
                    IconButton(onClick = { /* Profile */ }) {
                        Icon(Icons.Default.Person, "Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            // Scan QR FAB
            ExtendedFloatingActionButton(
                onClick = onScanQR,
                icon = { Icon(Icons.Default.QrCodeScanner, "Scan") },
                text = { Text("Scan & Pay") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Balance Card ────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Available Balance",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "₹${formatAmount(balance)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        if (!isOnline) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "💡 Balance will refresh when you're back online",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            // ── Quick Actions ───────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(icon = Icons.Default.QrCodeScanner, label = "Scan QR", onClick = onScanQR)
                    QuickAction(icon = Icons.Default.Send, label = "Pay UPI ID", onClick = onPayNew)
                    QuickAction(icon = Icons.Default.ContactPhone, label = "Pay Contact", onClick = { })
                    QuickAction(icon = Icons.Default.History, label = "History", onClick = onViewHistory)
                }
            }

            // ── Recent Contacts ─────────────────────────
            if (recentContacts.isNotEmpty()) {
                item {
                    Text(
                        "People",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentContacts) { contact ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onPayContact(contact.upiId) }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        contact.name.first().uppercase(),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    contact.name,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // ── Recent Transactions ─────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onViewHistory) {
                        Text("See all")
                    }
                }
            }

            items(recentTransactions.take(10)) { txn ->
                TransactionItem(txn)
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun TransactionItem(txn: PaymentIntent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    txn.payeeUpi.first().uppercase(),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(txn.payeeUpi, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    val (statusText, statusColor) = when (txn.status) {
                        TransactionStatus.SETTLED -> "Completed" to Color(0xFF4CAF50)
                        TransactionStatus.CREATED, TransactionStatus.DELIVERED -> "Processing" to Color(0xFFFF9800)
                        TransactionStatus.SETTLING -> "Settling" to Color(0xFF2196F3)
                        TransactionStatus.FAILED -> "Failed" to Color(0xFFF44336)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        statusText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "₹${formatAmount(txn.amount)}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

private fun formatAmount(paise: Long): String {
    val rupees = paise / 100
    val paiseRemainder = paise % 100
    return if (paiseRemainder > 0) {
        "$rupees.${paiseRemainder.toString().padStart(2, '0')}"
    } else {
        rupees.toString()
    }
}

data class QuickContact(
    val name: String,
    val upiId: String
)
