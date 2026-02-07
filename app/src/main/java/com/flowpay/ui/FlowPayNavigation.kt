package com.flowpay.ui

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.*
import com.flowpay.payment.OfflinePaymentEngine
import com.flowpay.payment.PaymentResult
import com.flowpay.ui.screens.*

@Composable
fun FlowPayNavigation() {
    val navController = rememberNavController()
    val engine = remember { OfflinePaymentEngine() }

    // Initialize demo balance
    LaunchedEffect(Unit) {
        if (InMemoryStore.getActiveToken() == null) {
            InMemoryStore.init("devesh@flowpay", 1_500_000L) // ₹15,000
        }
    }

    val balance by remember { derivedStateOf { engine.getAvailableBalance() } }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Force recomposition on payment
    val currentBalance = remember(refreshTrigger) { engine.getAvailableBalance() }
    val transactions = remember(refreshTrigger) { InMemoryStore.transactions.toList() }
    val pendingCount = remember(refreshTrigger) { engine.getPendingCount() }

    NavHost(navController = navController, startDestination = "home") {

        composable("home") {
            HomeScreen(
                balance = currentBalance,
                isOnline = false,
                pendingSettlements = pendingCount,
                recentTransactions = transactions,
                recentContacts = listOf(
                    QuickContact("Chai Wala", "chai.wala@upi"),
                    QuickContact("Grocery", "groceries@upi"),
                    QuickContact("Devesh", "devesh@bank"),
                    QuickContact("Mom", "mom@bank"),
                ),
                onScanQR = { navController.navigate("scan") },
                onPayContact = { upi -> navController.navigate("pay/$upi?amount=0") },
                onPayNew = { navController.navigate("pay_new") },
                onViewHistory = { }
            )
        }

        composable("scan") {
            QRScannerScreen(
                onQRScanned = { upiUri ->
                    val payeeUpi = parseUpiId(upiUri) ?: "unknown@upi"
                    val amount = parseUpiAmount(upiUri)
                    navController.navigate("pay/$payeeUpi?amount=${amount ?: 0}") {
                        popUpTo("scan") { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("pay_new") {
            PayNewScreen(
                onProceed = { upi ->
                    navController.navigate("pay/$upi?amount=0") {
                        popUpTo("pay_new") { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(
            "pay/{payeeUpi}?amount={amount}",
            arguments = listOf(
                navArgument("payeeUpi") { type = NavType.StringType },
                navArgument("amount") { type = NavType.LongType; defaultValue = 0L }
            )
        ) { backStackEntry ->
            val payeeUpi = backStackEntry.arguments?.getString("payeeUpi") ?: ""
            val prefillAmount = backStackEntry.arguments?.getLong("amount") ?: 0L

            PayScreenWithEngine(
                payeeUpi = payeeUpi,
                prefillAmount = prefillAmount,
                engine = engine,
                onDone = {
                    refreshTrigger++
                    navController.popBackStack("home", false)
                },
                onCancel = { navController.popBackStack() }
            )
        }
    }
}

private fun parseUpiId(uri: String): String? {
    val regex = Regex("[?&]pa=([^&]+)")
    return regex.find(uri)?.groupValues?.get(1)
}

private fun parseUpiAmount(uri: String): Long? {
    val regex = Regex("[?&]am=([^&]+)")
    val amountStr = regex.find(uri)?.groupValues?.get(1) ?: return null
    return try { (amountStr.toDouble() * 100).toLong() } catch (e: Exception) { null }
}
