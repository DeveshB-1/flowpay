package com.flowpay.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.flowpay.data.models.PaymentIntent
import com.flowpay.payment.OfflinePaymentEngine
import java.io.ByteArrayOutputStream

/**
 * NFC Host Card Emulation service for phone-to-phone payments.
 *
 * This is the payer's side — when you tap your phone to the merchant's,
 * this service sends the signed PaymentIntent over NFC.
 *
 * Flow:
 * 1. Merchant phone runs NFC reader mode, waiting for payment
 * 2. Payer phone (this service) emulates a card
 * 3. On tap: sends the signed PaymentIntent as APDU response
 * 4. Merchant verifies signatures offline
 * 5. Both phones show ✅
 */
class NFCPaymentService : HostApduService() {

    companion object {
        // AID (Application Identifier) for FlowPay
        // Registered custom AID for our payment app
        val FLOWPAY_AID = byteArrayOf(
            0xF0.toByte(), 0x46, 0x4C, 0x4F, 0x57,  // F0 FLOW
            0x50, 0x41, 0x59                           // PAY
        )

        // APDU commands
        private const val SELECT_INS: Byte = 0xA4.toByte()
        private const val GET_PAYMENT_INS: Byte = 0xCA.toByte()
        private const val STATUS_OK = 0x9000
        private const val STATUS_FAILED = 0x6F00

        // Pending payment to send over NFC
        @Volatile
        var pendingPayment: PaymentIntent? = null
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (commandApdu.size < 4) return buildResponse(STATUS_FAILED)

        val ins = commandApdu[1]

        return when (ins) {
            SELECT_INS -> handleSelect(commandApdu)
            GET_PAYMENT_INS -> handleGetPayment()
            else -> buildResponse(STATUS_FAILED)
        }
    }

    /**
     * Handle SELECT command — merchant selects our payment AID.
     */
    private fun handleSelect(apdu: ByteArray): ByteArray {
        // Verify the AID matches
        val aidLength = apdu[4].toInt()
        val aid = apdu.copyOfRange(5, 5 + aidLength)

        return if (aid.contentEquals(FLOWPAY_AID)) {
            // Selected! Send back our app identifier
            val response = "FLOWPAY_V1".toByteArray()
            buildResponse(STATUS_OK, response)
        } else {
            buildResponse(STATUS_FAILED)
        }
    }

    /**
     * Handle GET PAYMENT — merchant requests the payment intent.
     */
    private fun handleGetPayment(): ByteArray {
        val payment = pendingPayment
            ?: return buildResponse(STATUS_FAILED)

        // Serialize payment intent to CBOR (compact binary)
        val paymentBytes = serializePaymentIntent(payment)

        // Clear pending payment (one-time use)
        pendingPayment = null

        return buildResponse(STATUS_OK, paymentBytes)
    }

    override fun onDeactivated(reason: Int) {
        // NFC link lost
    }

    /**
     * Build an APDU response with status word.
     */
    private fun buildResponse(status: Int, data: ByteArray? = null): ByteArray {
        val stream = ByteArrayOutputStream()
        data?.let { stream.write(it) }
        stream.write(status shr 8)
        stream.write(status and 0xFF)
        return stream.toByteArray()
    }

    /**
     * Serialize a PaymentIntent to compact binary format.
     * Using a simple TLV (Tag-Length-Value) encoding.
     *
     * In production: use CBOR for better interoperability.
     */
    private fun serializePaymentIntent(intent: PaymentIntent): ByteArray {
        val stream = ByteArrayOutputStream()

        // Magic header
        stream.write("FP01".toByteArray()) // FlowPay v1

        // TLV fields
        writeTLV(stream, 0x01, intent.txnId.toByteArray())
        writeTLV(stream, 0x02, intent.payerUpi.toByteArray())
        writeTLV(stream, 0x03, intent.payeeUpi.toByteArray())
        writeTLV(stream, 0x04, longToBytes(intent.amount))
        writeTLV(stream, 0x05, intent.note.toByteArray())
        writeTLV(stream, 0x06, longToBytes(intent.timestamp))
        writeTLV(stream, 0x07, intent.authTokenId.toByteArray())
        writeTLV(stream, 0x08, longToBytes(intent.sequenceNumber))
        writeTLV(stream, 0x09, intent.payerSignature)
        writeTLV(stream, 0x0A, intent.bankAuthProof)

        return stream.toByteArray()
    }

    private fun writeTLV(stream: ByteArrayOutputStream, tag: Int, value: ByteArray) {
        stream.write(tag)
        // Length as 2 bytes (big-endian)
        stream.write(value.size shr 8)
        stream.write(value.size and 0xFF)
        stream.write(value)
    }

    private fun longToBytes(value: Long): ByteArray {
        return ByteArray(8) { i -> (value shr (56 - i * 8)).toByte() }
    }
}

/**
 * NFC Reader for the MERCHANT side.
 * Reads the payment intent from the payer's phone.
 */
class NFCPaymentReader {

    /**
     * Parse a PaymentIntent from NFC APDU response bytes.
     */
    fun parsePaymentIntent(data: ByteArray): PaymentIntent? {
        if (data.size < 4) return null

        // Check magic header
        val header = String(data.copyOfRange(0, 4))
        if (header != "FP01") return null

        var offset = 4
        val fields = mutableMapOf<Int, ByteArray>()

        while (offset < data.size) {
            if (offset + 3 > data.size) break
            val tag = data[offset].toInt() and 0xFF
            val length = ((data[offset + 1].toInt() and 0xFF) shl 8) or
                         (data[offset + 2].toInt() and 0xFF)
            offset += 3

            if (offset + length > data.size) break
            fields[tag] = data.copyOfRange(offset, offset + length)
            offset += length
        }

        return try {
            PaymentIntent(
                txnId = String(fields[0x01]!!),
                payerUpi = String(fields[0x02]!!),
                payeeUpi = String(fields[0x03]!!),
                amount = bytesToLong(fields[0x04]!!),
                note = String(fields[0x05] ?: ByteArray(0)),
                timestamp = bytesToLong(fields[0x06]!!),
                authTokenId = String(fields[0x07]!!),
                sequenceNumber = bytesToLong(fields[0x08]!!),
                payerSignature = fields[0x09]!!,
                bankAuthProof = fields[0x0A]!!,
                status = com.flowpay.data.models.TransactionStatus.DELIVERED,
                createdOffline = true,
                settledAt = null
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var result = 0L
        for (b in bytes) {
            result = (result shl 8) or (b.toLong() and 0xFF)
        }
        return result
    }
}
