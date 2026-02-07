package com.flowpay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.flowpay.data.db.InMemoryStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bank account setup flow — mimics real UPI onboarding.
 *
 * Real flow (requires PSP license):
 *   1. Verify phone via silent SMS to NPCI
 *   2. NPCI returns linked bank accounts
 *   3. User selects bank
 *   4. Bank sends OTP → auto-read
 *   5. User sets UPI PIN
 *   6. Account linked, UPI ID created
 *
 * Demo flow (simulated):
 *   1. Enter phone number
 *   2. "Detecting" bank accounts (simulated SMS)
 *   3. Pick a bank from list
 *   4. OTP verification (auto-read simulated)
 *   5. Set UPI PIN
 *   6. Done — balance loaded
 */

data class BankInfo(
    val name: String,
    val shortCode: String,
    val icon: String,       // Emoji for demo
    val color: Color,
    val accountNumber: String,  // Masked
    val ifsc: String,
    val balance: Long       // In paise (demo)
)

// Demo bank list
val DEMO_BANKS = listOf(
    BankInfo("State Bank of India", "SBI", "🏦", Color(0xFF1A237E), "XXXX XXXX 4532", "SBIN0001234", 2_500_000),
    BankInfo("HDFC Bank", "HDFC", "🏛️", Color(0xFF004B87), "XXXX XXXX 8821", "HDFC0002345", 1_500_000),
    BankInfo("ICICI Bank", "ICICI", "🏢", Color(0xFFB71C1C), "XXXX XXXX 1199", "ICIC0003456", 3_200_000),
    BankInfo("Axis Bank", "AXIS", "🏗️", Color(0xFF6A1B9A), "XXXX XXXX 5567", "UTIB0004567", 800_000),
    BankInfo("Kotak Mahindra", "KOTAK", "🔴", Color(0xFFE53935), "XXXX XXXX 3344", "KKBK0005678", 1_100_000),
    BankInfo("Punjab National Bank", "PNB", "🟤", Color(0xFF4E342E), "XXXX XXXX 7788", "PUNB0006789", 650_000),
    BankInfo("Bank of Baroda", "BOB", "🟠", Color(0xFFE65100), "XXXX XXXX 2211", "BARB0007890", 950_000),
)

enum class SetupStep {
    PHONE, DETECTING, SELECT_BANK, OTP_VERIFY, SET_PIN, DONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onSetupComplete: (String, Long) -> Unit  // (upiId, balance)
) {
    var step by remember { mutableStateOf(SetupStep.PHONE) }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf<BankInfo?>(null) }
    var otp by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Up FlowPay") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            // Progress indicator
            LinearProgressIndicator(
                progress = { when (step) {
                    SetupStep.PHONE -> 0.1f
                    SetupStep.DETECTING -> 0.3f
                    SetupStep.SELECT_BANK -> 0.5f
                    SetupStep.OTP_VERIFY -> 0.7f
                    SetupStep.SET_PIN -> 0.9f
                    SetupStep.DONE -> 1.0f
                }},
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            )

            Spacer(Modifier.height(24.dp))

            when (step) {
                SetupStep.PHONE -> PhoneStep(
                    phone = phoneNumber,
                    onPhoneChange = { phoneNumber = it },
                    onProceed = {
                        step = SetupStep.DETECTING
                        scope.launch {
                            delay(2500) // Simulate SMS verification
                            step = SetupStep.SELECT_BANK
                        }
                    }
                )

                SetupStep.DETECTING -> DetectingStep(phoneNumber)

                SetupStep.SELECT_BANK -> SelectBankStep(
                    onBankSelected = {
                        selectedBank = it
                        step = SetupStep.OTP_VERIFY
                        // Simulate OTP auto-read after 3 seconds
                        scope.launch {
                            delay(3000)
                            otp = "847291" // Simulated auto-read
                            delay(800)
                            step = SetupStep.SET_PIN
                        }
                    }
                )

                SetupStep.OTP_VERIFY -> OtpVerifyStep(
                    bank = selectedBank!!,
                    otp = otp,
                    onOtpChange = { otp = it },
                    onProceed = { step = SetupStep.SET_PIN }
                )

                SetupStep.SET_PIN -> SetPinStep(
                    newPin = newPin,
                    confirmPin = confirmPin,
                    onNewPinChange = { newPin = it },
                    onConfirmPinChange = { confirmPin = it },
                    onProceed = {
                        step = SetupStep.DONE
                        val bank = selectedBank!!
                        val upiId = "${phoneNumber}@${bank.shortCode.lowercase()}"
                        // Initialize the store with this bank account
                        InMemoryStore.init(upiId, bank.balance)
                        scope.launch {
                            delay(1500)
                            onSetupComplete(upiId, bank.balance)
                        }
                    }
                )

                SetupStep.DONE -> DoneStep(
                    bank = selectedBank!!,
                    upiId = "${phoneNumber}@${selectedBank!!.shortCode.lowercase()}"
                )
            }
        }
    }
}

// ── Phone Number ────────────────────────────────

@Composable
private fun PhoneStep(phone: String, onPhoneChange: (String) -> Unit, onProceed: () -> Unit) {
    Column {
        Text("Enter your mobile number", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "We'll verify this number via SMS to find your linked bank accounts",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { if (it.length <= 10 && it.all { c -> c.isDigit() }) onPhoneChange(it) },
            label = { Text("Phone Number") },
            prefix = { Text("+91 ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "📱 A silent SMS will be sent for verification. Standard SMS charges may apply.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onProceed,
            enabled = phone.length == 10,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) { Text("Verify Number", fontSize = 16.sp) }
    }
}

// ── Detecting Banks ─────────────────────────────

@Composable
private fun DetectingStep(phone: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 3.dp)
        Spacer(Modifier.height(24.dp))
        Text("Verifying +91 $phone", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Sending SMS verification...\nDetecting linked bank accounts...",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Select Bank ─────────────────────────────────

@Composable
private fun SelectBankStep(onBankSelected: (BankInfo) -> Unit) {
    Column {
        Text("Select your bank account", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "We found these accounts linked to your number",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DEMO_BANKS) { bank ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBankSelected(bank) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(bank.color.copy(alpha = 0.15f)),
                            Alignment.Center
                        ) { Text(bank.icon, fontSize = 24.sp) }

                        Spacer(Modifier.width(16.dp))

                        Column(Modifier.weight(1f)) {
                            Text(bank.name, fontWeight = FontWeight.Medium)
                            Text(bank.accountNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── OTP Verification ────────────────────────────

@Composable
private fun OtpVerifyStep(
    bank: BankInfo, otp: String,
    onOtpChange: (String) -> Unit, onProceed: () -> Unit
) {
    Column {
        Text("Verifying your account", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${bank.name} is sending an OTP to verify your account",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bank.color.copy(alpha = 0.08f))
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(bank.icon, fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(bank.name, fontWeight = FontWeight.Medium)
                    Text(bank.accountNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (otp.isEmpty()) {
            // Waiting for OTP
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(16.dp))
                Text("Waiting for OTP...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "OTP will be auto-read from SMS",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // OTP received
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        "Verified",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "OTP auto-read",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Show OTP digits
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    otp.forEach { digit ->
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            Alignment.Center
                        ) {
                            Text(
                                digit.toString(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Verifying with ${bank.name}...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Manual OTP entry option
        if (otp.isEmpty()) {
            var manualOtp by remember { mutableStateOf("") }
            OutlinedTextField(
                value = manualOtp,
                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) manualOtp = it },
                label = { Text("Enter OTP manually") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    onOtpChange(manualOtp)
                    onProceed()
                },
                enabled = manualOtp.length == 6,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Verify", fontSize = 16.sp) }

            Spacer(Modifier.height(8.dp))
            Text(
                "Didn't receive OTP? Resend in 30s",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Set UPI PIN ─────────────────────────────────

@Composable
private fun SetPinStep(
    newPin: String, confirmPin: String,
    onNewPinChange: (String) -> Unit, onConfirmPinChange: (String) -> Unit, onProceed: () -> Unit
) {
    var isConfirming by remember { mutableStateOf(false) }

    Column {
        Text(
            if (!isConfirming) "Set your UPI PIN" else "Confirm your UPI PIN",
            fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (!isConfirming) "Choose a 4 or 6 digit PIN for payments"
            else "Enter the same PIN again to confirm",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(48.dp))

        // PIN dots
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val currentPin = if (!isConfirming) newPin else confirmPin
            repeat(6) { i ->
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(20.dp).clip(CircleShape)
                        .background(
                            if (i < currentPin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
                Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = if (!isConfirming) newPin else confirmPin,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    if (!isConfirming) onNewPinChange(it) else onConfirmPinChange(it)
                }
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            label = { Text(if (!isConfirming) "New UPI PIN" else "Confirm UPI PIN") }
        )

        if (isConfirming && confirmPin.length >= 4 && confirmPin != newPin) {
            Spacer(Modifier.height(8.dp))
            Text("PINs don't match", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                if (!isConfirming) {
                    isConfirming = true
                } else {
                    onProceed()
                }
            },
            enabled = if (!isConfirming) newPin.length >= 4 else (confirmPin.length >= 4 && confirmPin == newPin),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp)
        ) { Text(if (!isConfirming) "Continue" else "Set PIN", fontSize = 16.sp) }
    }
}

// ── Done ────────────────────────────────────────

@Composable
private fun DoneStep(bank: BankInfo, upiId: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF4CAF50)),
            Alignment.Center
        ) { Icon(Icons.Default.Check, "Done", tint = Color.White, modifier = Modifier.size(48.dp)) }

        Spacer(Modifier.height(24.dp))
        Text("You're all set!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Your UPI ID", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(upiId, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(bank.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(bank.accountNumber, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(32.dp))
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(8.dp))
        Text("Setting up your account...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
