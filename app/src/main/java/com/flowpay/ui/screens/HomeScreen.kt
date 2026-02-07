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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.Contact
import com.flowpay.data.models.Offer
import com.flowpay.data.models.Transaction
import com.flowpay.data.models.TransactionStatus
import com.flowpay.data.models.TransactionType
import kotlinx.coroutines.delay

data class QuickContact(
    val name: String,
    val upiId: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onScanQR: () -> Unit,
    onPayPhone: () -> Unit,
    onBankTransfer: () -> Unit,
    onSelfTransfer: () -> Unit,
    onPayContact: (String) -> Unit,
    onCheckBalance: () -> Unit,
    onViewOffers: () -> Unit,
    onSearchClick: () -> Unit,
    onViewAllTransactions: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    val balance = InMemoryStore.getActiveToken()?.remaining ?: 0L
    val recentContacts = InMemoryStore.getRecentContacts()
    val recentTransactions = InMemoryStore.richTransactions.take(5)
    val offers = InMemoryStore.offers.toList()
    val userProfile = InMemoryStore.userProfile
    var isRefreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
        }
    ) {
        // Reset refresh after delay
        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(1500)
                isRefreshing = false
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ── Search Bar ──────────────────────────
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onSearchClick
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Pay by name, phone, or UPI ID",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile?.name?.first() ?: "D").toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // ── Balance Card ────────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    onClick = onCheckBalance,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF1A73E8), Color(0xFF4285F4))
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Bank Balance",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "₹${formatAmount(balance)}",
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalance,
                                        "Balance",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF81C784))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Tap to check balance • ${InMemoryStore.getPrimaryAccount()?.bankName ?: "Bank"}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // ── Pay Actions Row ─────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PayActionItem(Icons.Default.QrCodeScanner, "Scan QR", onScanQR)
                    PayActionItem(Icons.Default.PhoneAndroid, "Pay phone", onPayPhone)
                    PayActionItem(Icons.Default.AccountBalance, "Bank\ntransfer", onBankTransfer)
                    PayActionItem(Icons.Default.SwapHoriz, "Self\ntransfer", onSelfTransfer)
                }
            }

            // ── People Section ──────────────────────
            if (recentContacts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "People",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(recentContacts) { contact ->
                            ContactCircle(
                                name = contact.name,
                                avatarColor = Color(contact.avatarColor),
                                onClick = { onPayContact(contact.upiId) }
                            )
                        }
                    }
                }
            }

            // ── Businesses & Bills Section ──────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Businesses & Bills",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(12.dp))
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BusinessItem("📱", "Mobile\nRecharge", {})
                            BusinessItem("💡", "Electricity", {})
                            BusinessItem("📺", "DTH", {})
                            BusinessItem("🏠", "Rent", {})
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BusinessItem("💧", "Water", {})
                            BusinessItem("🛡️", "Insurance", {})
                            BusinessItem("🚂", "Train\nTickets", {})
                            BusinessItem("📋", "More", {})
                        }
                    }
                }
            }

            // ── Promotions & Offers ─────────────────
            if (offers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Rewards & Offers",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        TextButton(onClick = onViewOffers) {
                            Text("See all")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(offers) { offer ->
                            OfferCard(offer)
                        }
                    }
                }
            }

            // ── Recent Transactions ─────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent transactions",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onViewAllTransactions) {
                        Text("See all")
                    }
                }
            }

            if (recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Receipt,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No transactions yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentTransactions) { txn ->
                    HomeTransactionItem(txn) { onTransactionClick(txn) }
                }
            }
        }
    }
}

@Composable
private fun PayActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun ContactCircle(name: String, avatarColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.first().uppercase(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name.split(" ").first(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun BusinessItem(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .width(80.dp)
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun OfferCard(offer: Offer) {
    Card(
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(offer.icon, fontSize = 28.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "₹${formatAmount(offer.cashbackAmount)}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(offer.color)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                offer.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                offer.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeTransactionItem(txn: Transaction, onClick: () -> Unit) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            val displayName = if (txn.type == TransactionType.PAID) txn.payeeName else txn.payerName
            val initial = displayName.firstOrNull()?.uppercase() ?: "?"
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initial,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
            }

            Column(horizontalAlignment = Alignment.End) {
                val amountColor = if (txn.type == TransactionType.RECEIVED)
                    Color(0xFF34A853) else MaterialTheme.colorScheme.onSurface
                val prefix = if (txn.type == TransactionType.RECEIVED) "+" else "-"
                Text(
                    "${prefix}₹${formatAmount(txn.amount)}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = amountColor
                )
            }
        }
    }
}

fun formatAmount(paise: Long): String {
    val rupees = paise / 100
    val paiseRemainder = paise % 100
    return if (paiseRemainder > 0) {
        "$rupees.${paiseRemainder.toString().padStart(2, '0')}"
    } else {
        rupees.toString()
    }
}
