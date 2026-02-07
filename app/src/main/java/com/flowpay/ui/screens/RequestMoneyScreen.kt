package com.flowpay.ui.screens

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.MoneyRequest
import com.flowpay.data.models.RequestStatus
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestMoneyScreen(
    onBack: () -> Unit,
    onRequestSent: () -> Unit
) {
    var upiId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showSuccess by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Money") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (showSuccess) {
            // Success state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34A853)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, "Sent", tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text("Request Sent!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "₹$amount requested from $upiId",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onRequestSent,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                Icon(
                    Icons.Default.CallReceived,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it.lowercase().trim() },
                    label = { Text("Request from (UPI ID)") },
                    placeholder = { Text("name@bank") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Person, null) }
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 7) amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    leadingIcon = { Text("₹", fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp)) }
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.StickyNote2, null) }
                )

                Spacer(Modifier.weight(1f))

                Button(
                    onClick = {
                        val myUpi = InMemoryStore.userProfile?.upiId ?: "you@upi"
                        val myName = InMemoryStore.userProfile?.name ?: "You"
                        InMemoryStore.moneyRequests.add(
                            0,
                            MoneyRequest(
                                id = UUID.randomUUID().toString().take(8),
                                fromUpi = myUpi,
                                fromName = myName,
                                toUpi = upiId,
                                toName = upiId.substringBefore("@"),
                                amount = (amount.toLongOrNull() ?: 0) * 100,
                                note = note,
                                timestamp = System.currentTimeMillis(),
                                status = RequestStatus.PENDING
                            )
                        )
                        showSuccess = true
                    },
                    enabled = upiId.contains("@") && amount.isNotEmpty() && (amount.toLongOrNull() ?: 0) > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text(
                        "Request Money",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
