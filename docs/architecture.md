# FlowPay — Architecture

## Principle
Payment completes offline, instantly, directly from bank account.
Settlement is the bank catching up — not the payment happening.

## Offline Payment Flow

### Phase 1: Authorization Sync (while online, background)
```
App ←→ Bank Server
  1. Fetch current balance
  2. Bank signs an Authorization Token:
     {
       user_id, upi_id, account_number,
       max_amount: <balance>,
       valid_until: <24h from now>,
       bank_signature: Ed25519(...)
     }
  3. Token stored in Android Keystore (hardware-backed)
  4. Token refreshes automatically when online
```

### Phase 2: Offline Payment (no internet required)
```
1. User scans QR / enters UPI ID + amount
2. App checks: auth_token.remaining >= amount? → Yes
3. User enters UPI PIN
4. PIN verified locally (hashed + salted, stored in TEE)
5. App creates Payment Intent:
   {
     txn_id: UUID,
     payer: user@bank,
     payee: merchant@bank,
     amount: 500,
     timestamp: now,
     auth_token_ref: <token_id>,
     payer_signature: Ed25519(private_key, txn_data),
     bank_auth_proof: <from auth_token>
   }
6. App deducts from local auth_token.remaining
7. Local balance: 10000 → 9500

IF NFC/BLE available (merchant nearby):
   8. Payment intent sent to merchant device
   9. Merchant app verifies:
      - Bank signature on auth token is valid
      - Payer signature is valid
      - Amount <= token remaining
      - Token not expired
   10. Merchant shows ✅ ₹500 received

IF no NFC (remote payment / QR only):
   8. Payment intent stored in local queue
   9. Txn ID shown to both parties
   10. User shows ✅ sent, merchant verifies when online

ALWAYS:
   11. Payment intent queued for settlement
```

### Phase 3: Settlement (when online, automatic)
```
App → Backend → NPCI UPI Switch → Banks
  1. Submit payment intent with all signatures
  2. NPCI verifies bank's auth token
  3. Payer bank debits account
  4. Payee bank credits account
  5. Both parties get push notification (confirmation)
  6. Settlement complete

IF settlement fails (insufficient funds somehow):
  - Extremely rare because auth_token tracks spending
  - Bank bears the risk (they issued the authorization)
  - Same dispute process as chargeback
```

## Security Model

### On-Device
- UPI PIN: Never stored in plaintext. PBKDF2 hash in Android Keystore
- Auth Tokens: Stored in StrongBox / TEE (hardware secure element)
- Payment Intents: Signed with Ed25519 key pair (key in Keystore)
- Local DB: AES-256-GCM encrypted (key in Keystore)
- Anti-replay: Each txn has unique UUID + monotonic counter

### Cryptographic Chain
```
Bank Root Key
  └→ signs Auth Token (for user's spending limit)
       └→ User's Device Key (in TEE)
            └→ signs Payment Intent (each transaction)
                 └→ Merchant verifies (has bank's public key)
```

### Attack Vectors & Mitigations
| Attack | Mitigation |
|--------|------------|
| Replay old txn | UUID + monotonic counter + timestamp |
| Forge auth token | Bank's Ed25519 signature, verified offline |
| Tampered amount | Signed by payer + covered by auth token |
| Stolen phone | UPI PIN (not biometric-only) + TEE lockout |
| Double spend | Local ledger tracks remaining auth balance |
| Spend more than auth | Client enforces + merchant verifies token |
| Expired token | 24h validity, must re-sync while online |

## Data Models

### AuthorizationToken
```
id: UUID
user_id: string
upi_id: string (user@bank)
account_id: string (masked)
max_amount: int (paise)
spent_amount: int (paise)
remaining: int (paise) [computed]
issued_at: timestamp
valid_until: timestamp
bank_public_key: bytes
bank_signature: bytes (Ed25519)
status: ACTIVE | EXPIRED | REVOKED
```

### PaymentIntent
```
txn_id: UUID
payer_upi: string
payee_upi: string
amount: int (paise)
note: string (optional)
timestamp: timestamp
auth_token_id: UUID
sequence_number: int (monotonic)
payer_signature: bytes (Ed25519)
bank_auth_proof: bytes
status: CREATED | DELIVERED | SETTLED | FAILED
created_offline: boolean
settled_at: timestamp (null until settled)
```

### LocalLedger
```
Tracks all transactions and remaining spending authority.
Encrypted with AES-256-GCM, key in Android Keystore.
Syncs with bank's ledger when online.
```

## Communication Protocols

### NFC (primary, phone-to-phone)
- Android HCE (Host Card Emulation)
- Payload: CBOR-encoded PaymentIntent (compact binary)
- Max ~4KB per APDU, payment intent fits in 1 exchange
- Sub-second transfer

### BLE (fallback, longer range)
- GATT service for payment exchange
- Used when NFC not available
- ~1 second transfer

### QR Code (universal fallback)
- Standard UPI QR format for merchant identity
- For offline receipt: QR contains signed txn_id
- Merchant scans to verify later

## Settlement Service (Backend)

### APIs
- POST /api/settle — Submit offline payment intent
- POST /api/token/refresh — Get new auth token
- GET  /api/balance — Fetch current balance
- GET  /api/transactions — Transaction history
- WS   /api/sync — Realtime sync websocket

### Integration
- NPCI UPI 2.0 APIs (PSP integration)
- Bank partner APIs (for auth token issuance)
- Push notifications (FCM)
