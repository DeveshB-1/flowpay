package com.flowpay.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.flowpay.data.db.InMemoryStore
import com.flowpay.data.models.Transaction
import com.flowpay.payment.OfflinePaymentEngine
import com.flowpay.ui.screens.*

// Bottom nav items
sealed class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : BottomNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object PayReceive : BottomNavItem("pay_receive", "Pay", Icons.Filled.QrCode, Icons.Outlined.QrCode)
    data object History : BottomNavItem("history", "History", Icons.Filled.Receipt, Icons.Outlined.Receipt)
    data object Profile : BottomNavItem("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

val bottomNavItems = listOf(
    BottomNavItem.Home,
    BottomNavItem.PayReceive,
    BottomNavItem.History,
    BottomNavItem.Profile
)

// Routes that should show the bottom bar
private val bottomBarRoutes = setOf("home", "pay_receive", "history", "profile")

@Composable
fun FlowPayNavigation() {
    val navController = rememberNavController()
    val engine = remember { OfflinePaymentEngine() }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val hasAccount = remember(refreshTrigger) { InMemoryStore.getActiveToken() != null }
    val startDest = if (hasAccount) "main" else "splash"

    // Store for passing transaction between screens
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    NavHost(navController = navController, startDestination = startDest) {

        // ── Splash ──────────────────────────────
        composable("splash") {
            SplashScreen(
                onSplashComplete = {
                    val dest = if (InMemoryStore.getActiveToken() != null) "main" else "setup"
                    navController.navigate(dest) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // ── Setup ───────────────────────────────
        composable("setup") {
            SetupScreen(
                onSetupComplete = { _, _ ->
                    refreshTrigger++
                    navController.navigate("main") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }

        // ── Main (with bottom nav) ──────────────
        composable("main") {
            MainScreen(
                engine = engine,
                navController = navController,
                selectedTransaction = selectedTransaction,
                onSelectTransaction = { selectedTransaction = it },
                onRefresh = { refreshTrigger++ }
            )
        }

        // ── Full-screen routes (no bottom nav) ──

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
                    navController.navigate("main") {
                        popUpTo("main") { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("pay_contacts") {
            PayContactsScreen(
                onContactSelected = { upiId ->
                    navController.navigate("pay/$upiId?amount=0") {
                        popUpTo("pay_contacts") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("request_money") {
            RequestMoneyScreen(
                onBack = { navController.popBackStack() },
                onRequestSent = { navController.popBackStack() }
            )
        }

        composable("check_balance") {
            CheckBalanceScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("bank_accounts") {
            BankAccountsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("rewards") {
            RewardsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("transaction_detail") {
            selectedTransaction?.let { txn ->
                TransactionDetailScreen(
                    transaction = txn,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Main screen with bottom navigation bar.
 */
@Composable
private fun MainScreen(
    engine: OfflinePaymentEngine,
    navController: androidx.navigation.NavHostController,
    selectedTransaction: Transaction?,
    onSelectTransaction: (Transaction) -> Unit,
    onRefresh: () -> Unit
) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                tonalElevation = 2.dp
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = {
                            Icon(
                                if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        selected = selected,
                        onClick = {
                            innerNavController.navigate(item.route) {
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onScanQR = { navController.navigate("scan") },
                    onPayPhone = { navController.navigate("pay_contacts") },
                    onBankTransfer = { navController.navigate("pay_new") },
                    onSelfTransfer = {
                        val myUpi = InMemoryStore.userProfile?.upiId ?: "you@upi"
                        navController.navigate("pay/$myUpi?amount=0")
                    },
                    onPayContact = { upiId -> navController.navigate("pay/$upiId?amount=0") },
                    onCheckBalance = { navController.navigate("check_balance") },
                    onViewOffers = { navController.navigate("rewards") },
                    onSearchClick = { navController.navigate("pay_contacts") },
                    onViewAllTransactions = { innerNavController.navigate("history") },
                    onTransactionClick = { txn ->
                        onSelectTransaction(txn)
                        navController.navigate("transaction_detail")
                    }
                )
            }

            composable("pay_receive") {
                PayReceiveTab(
                    onScanQR = { navController.navigate("scan") },
                    onPayUpiId = { navController.navigate("pay_new") },
                    onPayContacts = { navController.navigate("pay_contacts") },
                    onRequestMoney = { navController.navigate("request_money") },
                    onCheckBalance = { navController.navigate("check_balance") },
                    onSelfTransfer = {
                        val myUpi = InMemoryStore.userProfile?.upiId ?: "you@upi"
                        navController.navigate("pay/$myUpi?amount=0")
                    }
                )
            }

            composable("history") {
                HistoryScreen(
                    onTransactionClick = { txn ->
                        onSelectTransaction(txn)
                        navController.navigate("transaction_detail")
                    }
                )
            }

            composable("profile") {
                ProfileScreen(
                    onManageBankAccounts = { navController.navigate("bank_accounts") },
                    onLogout = {
                        // Reset everything
                        InMemoryStore.tokens.clear()
                        InMemoryStore.transactions.clear()
                        InMemoryStore.richTransactions.clear()
                        InMemoryStore.contacts.clear()
                        InMemoryStore.bankAccounts.clear()
                        InMemoryStore.userProfile = null
                        navController.navigate("setup") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Pay & Receive tab — hub for payment actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayReceiveTab(
    onScanQR: () -> Unit,
    onPayUpiId: () -> Unit,
    onPayContacts: () -> Unit,
    onRequestMoney: () -> Unit,
    onCheckBalance: () -> Unit,
    onSelfTransfer: () -> Unit
) {
    val profile = InMemoryStore.userProfile

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        item {
            TopAppBar(
                title = { Text("Pay & Receive", fontWeight = FontWeight.SemiBold) }
            )
        }

        // My QR Code card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                onClick = { /* Show full QR */ }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // QR icon placeholder
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.QrCode2,
                            null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "Your QR Code",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Text(
                            profile?.upiId ?: "your@upi",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            "Show to receive payments",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Pay actions
        item {
            Text(
                "Pay",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            PayReceiveActionItem(
                icon = Icons.Default.QrCodeScanner,
                title = "Scan & Pay",
                subtitle = "Scan any UPI QR code to pay",
                onClick = onScanQR
            )
        }
        item {
            PayReceiveActionItem(
                icon = Icons.Default.Person,
                title = "Pay UPI ID",
                subtitle = "Enter UPI ID to send money",
                onClick = onPayUpiId
            )
        }
        item {
            PayReceiveActionItem(
                icon = Icons.Default.Contacts,
                title = "Pay Contacts",
                subtitle = "Send money to your contacts",
                onClick = onPayContacts
            )
        }
        item {
            PayReceiveActionItem(
                icon = Icons.Default.SwapHoriz,
                title = "Self Transfer",
                subtitle = "Transfer between your own accounts",
                onClick = onSelfTransfer
            )
        }

        // Receive actions
        item {
            Text(
                "Receive",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            PayReceiveActionItem(
                icon = Icons.Default.CallReceived,
                title = "Request Money",
                subtitle = "Send a payment request",
                onClick = onRequestMoney
            )
        }
        item {
            PayReceiveActionItem(
                icon = Icons.Default.AccountBalance,
                title = "Check Balance",
                subtitle = "View your bank balance",
                onClick = onCheckBalance
            )
        }
    }
}

@Composable
private fun PayReceiveActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── UPI URI Parsing ─────────────────────────────

private fun parseUpiId(uri: String): String? {
    val regex = Regex("[?&]pa=([^&]+)")
    return regex.find(uri)?.groupValues?.get(1)
}

private fun parseUpiAmount(uri: String): Long? {
    val regex = Regex("[?&]am=([^&]+)")
    val amountStr = regex.find(uri)?.groupValues?.get(1) ?: return null
    return try { (amountStr.toDouble() * 100).toLong() } catch (e: Exception) { null }
}
