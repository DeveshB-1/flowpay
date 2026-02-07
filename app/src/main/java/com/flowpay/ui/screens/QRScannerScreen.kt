package com.flowpay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * QR Scanner screen.
 * In production: uses CameraX + ML Kit barcode scanning.
 * For demo: shows a mock scanner with a manual input option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRScannerScreen(
    onQRScanned: (String) -> Unit,
    onCancel: () -> Unit
) {
    var showManualInput by remember { mutableStateOf(false) }
    var manualUpi by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview would go here (CameraX)
        // For now: dark background with scanner frame

        // Scanner frame
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Scan any UPI QR code",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))

            // QR frame
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Point your camera at a UPI QR code",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Manual UPI ID entry option
            TextButton(onClick = { showManualInput = true }) {
                Text("Enter UPI ID manually", color = Color.White.copy(alpha = 0.8f))
            }
        }

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, "Cancel", tint = Color.White)
            }
        }

        // Demo: tap the scanner area to simulate a scan
        if (!showManualInput) {
            Button(
                onClick = {
                    // Simulate scanning a UPI QR
                    onQRScanned("upi://pay?pa=demo.merchant@upi&pn=Demo%20Shop&am=100")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
            ) {
                Text("Demo: Simulate QR Scan", color = Color.White)
            }
        }

        // Manual UPI input dialog
        if (showManualInput) {
            AlertDialog(
                onDismissRequest = { showManualInput = false },
                title = { Text("Enter UPI ID") },
                text = {
                    OutlinedTextField(
                        value = manualUpi,
                        onValueChange = { manualUpi = it },
                        placeholder = { Text("name@bank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (manualUpi.contains("@")) {
                                onQRScanned("upi://pay?pa=$manualUpi")
                                showManualInput = false
                            }
                        },
                        enabled = manualUpi.contains("@")
                    ) {
                        Text("Pay")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualInput = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

/**
 * Screen for entering a UPI ID manually.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayNewScreen(
    onProceed: (String) -> Unit,
    onCancel: () -> Unit
) {
    var upiId by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Pay to UPI ID") },
            navigationIcon = {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, "Cancel")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Enter UPI ID",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = upiId,
                onValueChange = { upiId = it.lowercase().trim() },
                placeholder = { Text("name@bank") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Example: shopname@paytm, 9876543210@upi",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onProceed(upiId) },
                enabled = upiId.contains("@") && upiId.length > 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Proceed", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
