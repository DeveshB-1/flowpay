package com.flowpay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.Transaction
import com.flowpay.data.models.TransactionStatus
import com.flowpay.data.models.TransactionType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onTransactionClick: (Transaction) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("all") }
    val filters = listOf("all" to "All", "paid" to "Paid", "received" to "Received", "pending" to "Pending")
    val transactions = remember(selectedFilter) { InMemoryStore.getFilteredTransactions(selectedFilter) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        TopAppBar(
            title = {
                Text(
                    "Transaction History",
                    fontWeight = FontWeight.SemiBold
                )
            }
        )

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (key, label) ->
                FilterChip(
                    selected = selectedFilter == key,
                    onClick = { selectedFilter = key },
                    label = { Text(label, fontSize = 13.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        if (transactions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No transactions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Your transactions will appear here",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Transaction list grouped by date
            val grouped = transactions.groupBy { txn ->
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(txn.timestamp))
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                grouped.forEach { (date, txns) ->
                    item {
                        Text(
                            text = getRelativeDate(date),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(txns) { txn ->
                        HistoryTransactionItem(txn) { onTransactionClick(txn) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTransactionItem(txn: Transaction, onClick: () -> Unit) {
    val displayName = if (txn.type == TransactionType.PAID) txn.payeeName else txn.payerName
    val initial = displayName.firstOrNull()?.uppercase() ?: "?"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (statusText, statusColor) = when (txn.status) {
                        TransactionStatus.SETTLED -> "Completed" to Color(0xFF34A853)
                        TransactionStatus.CREATED, TransactionStatus.DELIVERED -> "Processing" to Color(0xFFFF9800)
                        TransactionStatus.SETTLING -> "Settling" to Color(0xFF1A73E8)
                        TransactionStatus.FAILED -> "Failed" to Color(0xFFEA4335)
                    }
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (txn.note.isNotEmpty()) {
                        Text(
                            " • ${txn.note}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // Time
                Text(
                    formatTime(txn.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val amountColor = if (txn.type == TransactionType.RECEIVED)
                    Color(0xFF34A853) else MaterialTheme.colorScheme.onSurface
                val prefix = if (txn.type == TransactionType.RECEIVED) "+" else "-"
                Text(
                    "${prefix}₹${formatAmount(txn.amount)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = amountColor
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun getRelativeDate(dateStr: String): String {
    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val date = sdf.parse(dateStr) ?: return dateStr
    val today = Calendar.getInstance()
    val txnDay = Calendar.getInstance().apply { time = date }

    return when {
        today.get(Calendar.DAY_OF_YEAR) == txnDay.get(Calendar.DAY_OF_YEAR) &&
            today.get(Calendar.YEAR) == txnDay.get(Calendar.YEAR) -> "Today"
        today.get(Calendar.DAY_OF_YEAR) - txnDay.get(Calendar.DAY_OF_YEAR) == 1 &&
            today.get(Calendar.YEAR) == txnDay.get(Calendar.YEAR) -> "Yesterday"
        else -> dateStr
    }
}
