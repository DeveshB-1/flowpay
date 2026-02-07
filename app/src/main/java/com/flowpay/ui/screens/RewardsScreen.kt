package com.flowpay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.Offer
import com.flowpay.data.models.ScratchCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    onBack: () -> Unit
) {
    val offers = InMemoryStore.offers.toList()
    val scratchCards = remember { InMemoryStore.scratchCards.toMutableStateList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards & Offers", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Scratch Cards
            item {
                Text(
                    "Scratch Cards",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(scratchCards.size) { index ->
                        val card = scratchCards[index]
                        ScratchCardItem(
                            card = card,
                            onScratch = {
                                scratchCards[index] = card.copy(isScratched = true)
                                InMemoryStore.scratchCards[index] = card.copy(isScratched = true)
                            }
                        )
                    }
                }
            }

            // Total rewards
            item {
                val totalRewards = scratchCards.filter { it.isScratched }.sumOf { it.rewardAmount }
                if (totalRewards > 0) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF34A853), Color(0xFF0D652D))
                                    ),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Total Rewards Earned", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                                    Text(
                                        "₹${formatAmount(totalRewards)}",
                                        color = Color.White,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Offers
            item {
                Text(
                    "Available Offers",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            items(offers) { offer ->
                OfferListItem(offer)
            }
        }
    }
}

@Composable
private fun ScratchCardItem(card: ScratchCard, onScratch: () -> Unit) {
    val bgColor by animateColorAsState(
        if (card.isScratched) Color(0xFF34A853) else Color(0xFFFBBC04),
        label = "scratchColor"
    )

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(180.dp)
            .clickable { if (!card.isScratched) onScratch() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(bgColor, bgColor.copy(alpha = 0.8f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (card.isScratched) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎉", fontSize = 36.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "₹${formatAmount(card.rewardAmount)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Won!",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap to Scratch",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        "Win rewards!",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OfferListItem(offer: Offer) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(offer.color).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(offer.icon, fontSize = 28.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    offer.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    offer.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "₹${formatAmount(offer.cashbackAmount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(offer.color)
                )
                Text(
                    "cashback",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
