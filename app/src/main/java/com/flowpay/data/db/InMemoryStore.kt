package com.flowpay.data.db

import com.flowpay.data.models.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory data store with rich demo data.
 * Makes the app feel alive without a real backend.
 */
object InMemoryStore {
    val transactions = CopyOnWriteArrayList<PaymentIntent>()
    val tokens = ConcurrentHashMap<String, AuthorizationToken>()
    val sequenceCounter = AtomicLong(0)

    // Rich data collections
    val richTransactions = CopyOnWriteArrayList<Transaction>()
    val contacts = CopyOnWriteArrayList<Contact>()
    val bankAccounts = CopyOnWriteArrayList<BankAccount>()
    val moneyRequests = CopyOnWriteArrayList<MoneyRequest>()
    val offers = CopyOnWriteArrayList<Offer>()
    val scratchCards = CopyOnWriteArrayList<ScratchCard>()

    var userProfile: UserProfile? = null

    fun getActiveToken(): AuthorizationToken? {
        return tokens.values.firstOrNull { it.isValid() }
    }

    fun addTransaction(intent: PaymentIntent) {
        transactions.add(0, intent)
    }

    fun addRichTransaction(txn: Transaction) {
        richTransactions.add(0, txn)
    }

    fun getNextSequence(): Long = sequenceCounter.incrementAndGet()

    fun init(upiId: String, balance: Long) {
        val token = AuthorizationToken(
            id = "token-${System.currentTimeMillis()}",
            userId = "user-1",
            upiId = upiId,
            accountId = "XXXX1234",
            maxAmount = balance,
            spentAmount = 0,
            issuedAt = System.currentTimeMillis(),
            validUntil = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
            status = TokenStatus.ACTIVE
        )
        tokens[token.id] = token

        // Set user profile
        val phone = upiId.substringBefore("@")
        val bankCode = upiId.substringAfter("@")
        userProfile = UserProfile(
            name = "Devesh Kumar",
            phone = "+91 $phone",
            upiId = upiId,
            email = "devesh@example.com",
            isSetupComplete = true
        )

        // Seed demo data
        seedContacts()
        seedTransactions(upiId)
        seedBankAccounts(balance, bankCode)
        seedMoneyRequests(upiId)
        seedOffers()
        seedScratchCards()
    }

    fun deductFromToken(tokenId: String, amount: Long) {
        tokens[tokenId]?.let { old ->
            tokens[tokenId] = old.copy(spentAmount = old.spentAmount + amount)
        }
    }

    fun getPrimaryAccount(): BankAccount? = bankAccounts.firstOrNull { it.isPrimary }
        ?: bankAccounts.firstOrNull()

    fun getRecentContacts(): List<Contact> = contacts.filter { it.isRecent }.take(8)

    fun getFilteredTransactions(filter: String): List<Transaction> {
        return when (filter) {
            "paid" -> richTransactions.filter { it.type == TransactionType.PAID }
            "received" -> richTransactions.filter { it.type == TransactionType.RECEIVED }
            "pending" -> richTransactions.filter { it.status != TransactionStatus.SETTLED && it.status != TransactionStatus.FAILED }
            else -> richTransactions.toList()
        }
    }

    // ── Demo Data Seeders ───────────────────────────

    private fun seedContacts() {
        contacts.clear()
        contacts.addAll(
            listOf(
                Contact("Rahul Sharma", "+91 98765 43210", "rahul.sharma@oksbi", isRecent = true, avatarColor = 0xFF4CAF50),
                Contact("Priya Patel", "+91 87654 32109", "priya.patel@okicici", isRecent = true, avatarColor = 0xFFE91E63),
                Contact("Amit Kumar", "+91 76543 21098", "amit.kumar@okhdfc", isRecent = true, avatarColor = 0xFF2196F3),
                Contact("Sneha Gupta", "+91 65432 10987", "sneha.g@paytm", isRecent = true, avatarColor = 0xFF9C27B0),
                Contact("Vikram Singh", "+91 54321 09876", "vikram@ybl", isRecent = true, avatarColor = 0xFFFF9800),
                Contact("Ananya Das", "+91 43210 98765", "ananya.das@okaxis", isRecent = true, avatarColor = 0xFF00BCD4),
                Contact("Karan Mehta", "+91 32109 87654", "karan.m@oksbi", isRecent = false, avatarColor = 0xFF795548),
                Contact("Neha Joshi", "+91 21098 76543", "neha.joshi@okhdfc", isRecent = false, avatarColor = 0xFFFF5722),
                Contact("Rohan Agarwal", "+91 10987 65432", "rohan.a@ybl", isRecent = false, avatarColor = 0xFF607D8B),
                Contact("Chai Wala", "+91 99887 76655", "chai.wala@upi", isRecent = true, avatarColor = 0xFF8D6E63),
                Contact("Grocery Store", "+91 88776 65544", "groceries@upi", isRecent = true, avatarColor = 0xFF4CAF50),
                Contact("Mom", "+91 99001 12233", "mom@oksbi", isRecent = true, isFavorite = true, avatarColor = 0xFFE91E63),
                Contact("Deepak Verma", "+91 77665 54433", "deepak.v@okicici", isRecent = false, avatarColor = 0xFF3F51B5),
                Contact("Pooja Reddy", "+91 66554 43322", "pooja.r@okhdfc", isRecent = false, avatarColor = 0xFFF44336),
                Contact("Sanjay Mishra", "+91 55443 32211", "sanjay.m@paytm", isRecent = false, avatarColor = 0xFF009688),
            )
        )
    }

    private fun seedTransactions(myUpi: String) {
        richTransactions.clear()
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 24 * hour

        richTransactions.addAll(
            listOf(
                Transaction(
                    txnId = "TXN20240207001", payerUpi = myUpi, payeeUpi = "chai.wala@upi",
                    payerName = "You", payeeName = "Chai Wala",
                    amount = 3000, note = "Morning tea", timestamp = now - 2 * hour,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829134", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240207002", payerUpi = "rahul.sharma@oksbi", payeeUpi = myUpi,
                    payerName = "Rahul Sharma", payeeName = "You",
                    amount = 50000, note = "Lunch split", timestamp = now - 5 * hour,
                    status = TransactionStatus.SETTLED, type = TransactionType.RECEIVED,
                    bankRef = "BNK407829135", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240206003", payerUpi = myUpi, payeeUpi = "groceries@upi",
                    payerName = "You", payeeName = "Grocery Store",
                    amount = 127500, note = "Weekly groceries", timestamp = now - 1 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829136", createdOffline = true
                ),
                Transaction(
                    txnId = "TXN20240206004", payerUpi = "priya.patel@okicici", payeeUpi = myUpi,
                    payerName = "Priya Patel", payeeName = "You",
                    amount = 200000, note = "Movie tickets", timestamp = now - 1 * day - 3 * hour,
                    status = TransactionStatus.SETTLED, type = TransactionType.RECEIVED,
                    bankRef = "BNK407829137", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240205005", payerUpi = myUpi, payeeUpi = "amit.kumar@okhdfc",
                    payerName = "You", payeeName = "Amit Kumar",
                    amount = 75000, note = "Dinner share", timestamp = now - 2 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829138", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240205006", payerUpi = myUpi, payeeUpi = "sneha.g@paytm",
                    payerName = "You", payeeName = "Sneha Gupta",
                    amount = 150000, note = "Birthday gift", timestamp = now - 2 * day - 6 * hour,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829139", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240204007", payerUpi = "vikram@ybl", payeeUpi = myUpi,
                    payerName = "Vikram Singh", payeeName = "You",
                    amount = 100000, note = "Rent share", timestamp = now - 3 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.RECEIVED,
                    bankRef = "BNK407829140", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240204008", payerUpi = myUpi, payeeUpi = "ananya.das@okaxis",
                    payerName = "You", payeeName = "Ananya Das",
                    amount = 35000, note = "Coffee", timestamp = now - 3 * day - 2 * hour,
                    status = TransactionStatus.CREATED, type = TransactionType.PAID,
                    bankRef = "BNK407829141", createdOffline = true
                ),
                Transaction(
                    txnId = "TXN20240203009", payerUpi = "mom@oksbi", payeeUpi = myUpi,
                    payerName = "Mom", payeeName = "You",
                    amount = 500000, note = "Festival gift 🎁", timestamp = now - 4 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.RECEIVED,
                    bankRef = "BNK407829142", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240203010", payerUpi = myUpi, payeeUpi = "electricity@billdesk",
                    payerName = "You", payeeName = "Electricity Bill",
                    amount = 245000, note = "Jan bill", timestamp = now - 5 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829143", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240202011", payerUpi = myUpi, payeeUpi = "netflix@razorpay",
                    payerName = "You", payeeName = "Netflix",
                    amount = 64900, note = "Monthly subscription", timestamp = now - 6 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.PAID,
                    bankRef = "BNK407829144", createdOffline = false
                ),
                Transaction(
                    txnId = "TXN20240201012", payerUpi = "karan.m@oksbi", payeeUpi = myUpi,
                    payerName = "Karan Mehta", payeeName = "You",
                    amount = 250000, note = "Trip expenses", timestamp = now - 7 * day,
                    status = TransactionStatus.SETTLED, type = TransactionType.RECEIVED,
                    bankRef = "BNK407829145", createdOffline = false
                ),
            )
        )
    }

    private fun seedBankAccounts(primaryBalance: Long, bankCode: String) {
        bankAccounts.clear()
        bankAccounts.addAll(
            listOf(
                BankAccount(
                    id = "acc-1",
                    bankName = bankCodeToName(bankCode),
                    bankShortCode = bankCode.uppercase(),
                    accountNumber = "XXXX XXXX 4532",
                    ifsc = "${bankCode.uppercase()}0001234",
                    balance = primaryBalance,
                    isPrimary = true,
                    icon = "🏦",
                    color = 0xFF1A73E8
                ),
                BankAccount(
                    id = "acc-2",
                    bankName = "HDFC Bank",
                    bankShortCode = "HDFC",
                    accountNumber = "XXXX XXXX 8821",
                    ifsc = "HDFC0002345",
                    balance = 1_500_000,
                    isPrimary = false,
                    icon = "🏛️",
                    color = 0xFF004B87
                ),
            )
        )
    }

    private fun bankCodeToName(code: String): String = when (code.lowercase()) {
        "sbi", "oksbi" -> "State Bank of India"
        "hdfc", "okhdfc" -> "HDFC Bank"
        "icici", "okicici" -> "ICICI Bank"
        "axis", "okaxis" -> "Axis Bank"
        "kotak", "okkotak" -> "Kotak Mahindra Bank"
        "pnb", "okpnb" -> "Punjab National Bank"
        else -> "State Bank of India"
    }

    private fun seedMoneyRequests(myUpi: String) {
        moneyRequests.clear()
        val now = System.currentTimeMillis()
        moneyRequests.addAll(
            listOf(
                MoneyRequest(
                    id = "REQ001", fromUpi = myUpi, fromName = "You",
                    toUpi = "rahul.sharma@oksbi", toName = "Rahul Sharma",
                    amount = 30000, note = "Cab fare", timestamp = now - 3600_000,
                    status = RequestStatus.PENDING
                ),
                MoneyRequest(
                    id = "REQ002", fromUpi = "sneha.g@paytm", fromName = "Sneha Gupta",
                    toUpi = myUpi, toName = "You",
                    amount = 15000, note = "Snacks", timestamp = now - 7200_000,
                    status = RequestStatus.PENDING
                ),
            )
        )
    }

    private fun seedOffers() {
        offers.clear()
        val now = System.currentTimeMillis()
        offers.addAll(
            listOf(
                Offer("OFF001", "Pay ₹500+ & get cashback", "Get up to ₹75 cashback on payments above ₹500", 7500, now + 7 * 86400_000, 0xFF4CAF50, "💰"),
                Offer("OFF002", "Recharge & save", "₹20 cashback on mobile recharge above ₹199", 2000, now + 5 * 86400_000, 0xFF2196F3, "📱"),
                Offer("OFF003", "Bill payment reward", "₹50 cashback on first bill payment", 5000, now + 10 * 86400_000, 0xFFFF9800, "🧾"),
                Offer("OFF004", "Refer & earn", "Invite friends & earn ₹100 per referral", 10000, now + 30 * 86400_000, 0xFF9C27B0, "🎉"),
            )
        )
    }

    private fun seedScratchCards() {
        scratchCards.clear()
        val now = System.currentTimeMillis()
        scratchCards.addAll(
            listOf(
                ScratchCard("SC001", isScratched = true, rewardAmount = 1500, timestamp = now - 86400_000),
                ScratchCard("SC002", isScratched = false, rewardAmount = 5000, timestamp = now - 3600_000),
                ScratchCard("SC003", isScratched = false, rewardAmount = 2500, timestamp = now),
            )
        )
    }
}
