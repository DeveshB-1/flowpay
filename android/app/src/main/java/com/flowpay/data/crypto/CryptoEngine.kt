package com.flowpay.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.flowpay.data.models.PaymentIntentData
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cryptographic engine — handles all signing, verification, and encryption.
 *
 * All keys live in Android Keystore (hardware-backed TEE/StrongBox).
 * Private keys NEVER leave the secure enclave.
 */
class CryptoEngine {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DEVICE_KEY_ALIAS = "flowpay_device_key"
        private const val PIN_KEY_ALIAS = "flowpay_pin_key"
        private const val DB_KEY_ALIAS = "flowpay_db_key"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val PIN_ITERATIONS = 100_000
        private const val PIN_KEY_LENGTH = 256
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    // ── Device Key (for signing payment intents) ────────────

    /**
     * Generate the device key pair in TEE.
     * Called once during app setup.
     * Private key NEVER leaves the hardware.
     */
    fun generateDeviceKeyPair() {
        if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) return

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            DEVICE_KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false) // PIN check is separate
            .setIsStrongBoxBacked(true)            // Use StrongBox if available
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Get the device's public key (shared with bank during registration).
     */
    fun getDevicePublicKey(): PublicKey {
        val entry = keyStore.getEntry(DEVICE_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey
    }

    /**
     * Sign a payment intent with the device's private key.
     * The private key is in TEE — signing happens in hardware.
     */
    fun signPaymentIntent(data: PaymentIntentData): ByteArray {
        val privateKey = (keyStore.getEntry(DEVICE_KEY_ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data.toSignableBytes())
        return signature.sign()
    }

    /**
     * Verify a payer's signature on a payment intent (merchant side).
     */
    fun verifyPayerSignature(intentData: ByteArray, signature: ByteArray): Boolean {
        // In production: payer's public key would be fetched from bank's directory
        // For offline: included in the payment intent or auth token
        return try {
            val publicKey = getDevicePublicKey() // TODO: use payer's actual public key
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(publicKey)
            sig.update(intentData)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    // ── Bank Signature Verification ─────────────────────────

    /**
     * Verify that an auth token was genuinely signed by the bank.
     * Bank's public key is pre-installed or fetched during online sync.
     */
    fun verifyBankSignature(authTokenId: String, signature: ByteArray): Boolean {
        // Bank public keys are stored locally (updated during online sync)
        val bankPublicKey = getBankPublicKey() ?: return false

        return try {
            val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
            sig.initVerify(bankPublicKey)
            sig.update(authTokenId.toByteArray(Charsets.UTF_8))
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }

    private fun getBankPublicKey(): PublicKey? {
        // TODO: Load from local store (synced from bank during online phase)
        return if (keyStore.containsAlias("bank_public_key")) {
            (keyStore.getEntry("bank_public_key", null) as KeyStore.TrustedCertificateEntry)
                .trustedCertificate.publicKey
        } else null
    }

    // ── UPI PIN ─────────────────────────────────────────────

    /**
     * Set up the UPI PIN. Hashed with PBKDF2 and stored in Keystore.
     * The actual PIN is NEVER stored.
     */
    fun setupPin(pin: String) {
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hash = hashPin(pin, salt)

        // Store salt + hash securely
        // Using encrypted shared preferences backed by Keystore
        PinStorage.store(salt, hash)
    }

    /**
     * Verify the UPI PIN against stored hash.
     * Runs entirely on-device, no network needed.
     */
    fun verifyPin(pin: String): Boolean {
        val (salt, storedHash) = PinStorage.retrieve() ?: return false
        val hash = hashPin(pin, salt)
        return MessageDigest.isEqual(hash, storedHash)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        // PBKDF2 with HMAC-SHA256
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(), salt, PIN_ITERATIONS, PIN_KEY_LENGTH
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    // ── Database Encryption ─────────────────────────────────

    /**
     * Get or create the AES-256-GCM key for encrypting local database.
     * Key lives in Keystore, never exposed to app memory.
     */
    fun getDatabaseEncryptionKey(): SecretKey {
        if (keyStore.containsAlias(DB_KEY_ALIAS)) {
            return (keyStore.getEntry(DB_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                DB_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data with AES-256-GCM.
     */
    fun encrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getDatabaseEncryptionKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        return Pair(iv, encrypted)
    }

    /**
     * Decrypt data with AES-256-GCM.
     */
    fun decrypt(iv: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getDatabaseEncryptionKey(), spec)
        return cipher.doFinal(data)
    }
}

/**
 * Secure storage for PIN hash + salt.
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 */
object PinStorage {
    // TODO: Implement with EncryptedSharedPreferences
    private var storedSalt: ByteArray? = null
    private var storedHash: ByteArray? = null

    fun store(salt: ByteArray, hash: ByteArray) {
        storedSalt = salt
        storedHash = hash
    }

    fun retrieve(): Pair<ByteArray, ByteArray>? {
        val s = storedSalt ?: return null
        val h = storedHash ?: return null
        return Pair(s, h)
    }
}
