# FlowPay — USSD-Based Offline UPI Architecture

## Core Idea
Beautiful GPay-like UI that automates *99# USSD commands behind the scenes.
Real bank accounts. Real payments. No internet. No PSP license.

## How *99# (NUUP) Works

NPCI's National Unified USSD Platform — works on any GSM phone.

### Menu Structure
```
*99# → Main Menu
  1. Send Money
     → 1. Mobile Number
     → 2. UPI ID  
     → 3. IFSC + Account Number
  2. Request Money
  3. Check Balance
  4. My Profile
     → 1. My UPI ID
     → 2. Link Bank Account
     → 3. Change Default Bank
  5. Pending Requests  
  6. Transactions
  7. UPI PIN
     → 1. Set UPI PIN
     → 2. Change UPI PIN
  0. Back
```

### Payment via UPI ID (*99#)
```
Step 1: Dial *99#          → "Welcome to UPI. Select option"
Step 2: Send "1"           → "Send Money. Select: 1.Mobile 2.UPI ID 3.IFSC"
Step 3: Send "2"           → "Enter UPI ID"
Step 4: Send "shop@upi"   → "Enter Amount"
Step 5: Send "500"         → "Enter remarks (optional)"  
Step 6: Send "Chai"        → "Confirm: ₹500 to shop@upi? Enter UPI PIN"
Step 7: Send "1234"        → "Transaction successful. Txn ID: UPI123456789"
```

## Android Implementation

### USSD API (Android 8+/API 26+)
```kotlin
// TelephonyManager.sendUssdRequest() — official API
telephonyManager.sendUssdRequest(
    "*99#",
    object : TelephonyManager.UssdResponseCallback() {
        override fun onReceiveUssdResponse(
            telephonyManager: TelephonyManager,
            request: String,
            response: CharSequence
        ) {
            // Parse USSD response
            // Send next command in sequence
        }
        
        override fun onReceiveUssdResponseFailed(
            telephonyManager: TelephonyManager,
            request: String,
            failureCode: Int
        ) {
            // Handle USSD failure
        }
    },
    handler
)
```

### Problem: Multi-step USSD Sessions
`sendUssdRequest()` only handles single-shot USSD.
*99# needs a multi-step session (send 1, then 2, then UPI ID, etc.)

### Solution: Accessibility Service + USSD Dialer
```
App → dial *99# via Intent
  → Accessibility Service reads USSD popup text
  → Accessibility Service auto-responds
  → Reads next popup
  → Auto-responds again
  → Until transaction complete
  → Returns result to our UI
```

### Alternative: AT Commands via Root (not recommended)
```
echo 'AT+CUSD=1,"*99#",15' > /dev/smd0
```

### Best Approach: Standard USSD Session Handler
```
1. App triggers: tel:*99#  
2. System USSD dialog appears briefly
3. Accessibility Service:
   - Reads dialog text
   - Clicks "Reply" / enters response
   - Reads next dialog
   - Repeats until done
4. Returns parsed result to our beautiful UI
```

## App Architecture

```
┌─────────────────────────────┐
│   Beautiful GPay-like UI     │  ← User sees this
│   (Jetpack Compose)          │
├─────────────────────────────┤
│   USSD Command Builder       │  ← Builds USSD sequences
│   parses menu, builds cmds   │
├─────────────────────────────┤
│   USSD Session Manager       │  ← Manages USSD dialog
│   Accessibility Service      │     auto-responds
├─────────────────────────────┤
│   Android USSD / Telephony   │  ← System layer
│   GSM Network                │  ← No internet needed
├─────────────────────────────┤
│   *99# → NPCI → Bank         │  ← Real UPI
└─────────────────────────────┘
```

## USSD Command Sequences

### Pay via UPI ID
```
Command Sequence: *99# → 1 → 2 → {upi_id} → {amount} → {remarks} → {pin}
Expected responses at each step to validate we're on track.
```

### Pay via Phone Number
```
Command Sequence: *99# → 1 → 1 → {phone} → {amount} → {remarks} → {pin}
```

### Check Balance
```
Command Sequence: *99# → 3 → {pin}
Response: "Available Balance: ₹15,234.50"
```

### View UPI ID
```
Command Sequence: *99# → 4 → 1
Response: "Your UPI ID: 9876543210@upi"
```

### Transaction History
```
Command Sequence: *99# → 6
Response: List of recent transactions
```

## Permissions Required
```xml
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
```

## Limitations
- ₹5,000 per transaction limit (NPCI rule for USSD)
- ~₹0.50 service charge per transaction
- USSD session timeout: 15-30 seconds
- Depends on GSM network (not WiFi)
- Accessibility Service needs user to enable manually
- Some OEMs kill Accessibility Services aggressively
- USSD popups briefly visible (can't fully hide)

## Advantages
- REAL bank debit/credit — not a wallet
- Works with ZERO internet
- Works on any GSM network (2G/3G/4G/5G)
- No PSP license needed
- Supports 13 languages
- Works with almost all Indian banks
- Already NPCI certified and RBI approved
