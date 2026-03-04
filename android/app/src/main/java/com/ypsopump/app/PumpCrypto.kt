package com.ypsopump.app

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

object PumpCrypto {

    private val sodium = SodiumAndroid()
    private val lazySodium = LazySodiumAndroid(sodium)

    private val AUTH_SALT = byteArrayOf(
        0x4F, 0xC2.toByte(), 0x45, 0x4D,
        0x9B.toByte(), 0x81.toByte(), 0x59, 0xA4.toByte(),
        0x93.toByte(), 0xBB.toByte()
    )

    // --- CRC16 ---

    private const val CRC_POLY = 0x04C11DB7L
    private val CRC_TABLE = LongArray(256).also { table ->
        for (idx in 0 until 256) {
            var v = idx.toLong() shl 24
            for (bit in 0 until 8) {
                v = if (v and 0x80000000L != 0L) {
                    ((v shl 1) and 0xFFFFFFFFL) xor CRC_POLY
                } else {
                    (v shl 1) and 0xFFFFFFFFL
                }
            }
            table[idx] = v
        }
    }

    private fun bitstuff(data: ByteArray): ByteArray {
        if (data.isEmpty()) return byteArrayOf()
        val blockCount = (data.size + 3) / 4
        val stuffed = ByteArray(blockCount * 4)
        for (block in 0 until blockCount) {
            val base = block * 4
            for (idx in 0 until 4) {
                val src = base + idx
                stuffed[base + 3 - idx] = if (src < data.size) data[src] else 0
            }
        }
        return stuffed
    }

    fun crc16(payload: ByteArray): ByteArray {
        var crc = 0xFFFFFFFFL
        for (byte in bitstuff(payload)) {
            val tableIdx = (((crc shr 24) xor (byte.toLong() and 0xFF)) and 0xFF).toInt()
            crc = (((crc shl 8) and 0xFFFFFFFFL) xor CRC_TABLE[tableIdx])
        }
        val result = (crc and 0xFFFFL).toInt()
        return byteArrayOf((result and 0xFF).toByte(), ((result shr 8) and 0xFF).toByte())
    }

    fun crc16Valid(payload: ByteArray): Boolean {
        if (payload.size < 2) return false
        val data = payload.copyOfRange(0, payload.size - 2)
        val expected = payload.copyOfRange(payload.size - 2, payload.size)
        return crc16(data).contentEquals(expected)
    }

    // --- Password ---

    fun computePassword(mac: String): ByteArray {
        val macBytes = mac.replace(":", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.getInstance("MD5").digest(macBytes + AUTH_SALT)
    }

    // --- Serial to addresses ---

    fun serialToMac(serial: String): String {
        var num = serial.toLong()
        if (num > 10_000_000) num -= 10_000_000
        val hexStr = String.format("%06X", num)
        return "EC:2A:F0:${hexStr.substring(0, 2)}:${hexStr.substring(2, 4)}:${hexStr.substring(4, 6)}"
    }

    fun serialToBtAddress(serial: String): ByteArray {
        val num = (serial.toLong() % 10_000_000).toInt()
        val le = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(num).array()
        return byteArrayOf(0xEC.toByte(), 0x2A, 0xF0.toByte(), le[2], le[1], le[0])
    }

    // --- X25519 Key Pair ---

    fun generateX25519KeyPair(): Pair<ByteArray, ByteArray> {
        val privateKey = ByteArray(32)
        val publicKey = ByteArray(32)
        sodium.crypto_box_keypair(publicKey, privateKey)
        // Convert from Ed25519 box keypair to raw X25519:
        // Actually crypto_box_keypair already generates X25519 keys in libsodium
        return Pair(privateKey, publicKey)
    }

    fun x25519PublicFromPrivate(privateKey: ByteArray): ByteArray {
        val publicKey = ByteArray(32)
        sodium.crypto_scalarmult_base(publicKey, privateKey)
        return publicKey
    }

    // --- HChaCha20 ---

    private fun rotl32(v: Long, n: Int): Long {
        return (((v shl n) or (v ushr (32 - n))) and 0xFFFFFFFFL)
    }

    private fun quarterRound(state: LongArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] = (state[a] + state[b]) and 0xFFFFFFFFL
        state[d] = rotl32(state[d] xor state[a], 16)
        state[c] = (state[c] + state[d]) and 0xFFFFFFFFL
        state[b] = rotl32(state[b] xor state[c], 12)
        state[a] = (state[a] + state[b]) and 0xFFFFFFFFL
        state[d] = rotl32(state[d] xor state[a], 8)
        state[c] = (state[c] + state[d]) and 0xFFFFFFFFL
        state[b] = rotl32(state[b] xor state[c], 7)
    }

    private fun leU32(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) or
                ((data[offset + 1].toLong() and 0xFF) shl 8) or
                ((data[offset + 2].toLong() and 0xFF) shl 16) or
                ((data[offset + 3].toLong() and 0xFF) shl 24))
    }

    private fun u32ToLe(value: Long): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    fun hchacha20(key: ByteArray, nonce: ByteArray): ByteArray {
        val const = "expand 32-byte k".toByteArray(Charsets.US_ASCII)
        val state = LongArray(16)
        for (i in 0 until 4) state[i] = leU32(const, i * 4)
        for (i in 0 until 8) state[4 + i] = leU32(key, i * 4)
        for (i in 0 until 4) state[12 + i] = leU32(nonce, i * 4)

        repeat(10) {
            quarterRound(state, 0, 4, 8, 12)
            quarterRound(state, 1, 5, 9, 13)
            quarterRound(state, 2, 6, 10, 14)
            quarterRound(state, 3, 7, 11, 15)
            quarterRound(state, 0, 5, 10, 15)
            quarterRound(state, 1, 6, 11, 12)
            quarterRound(state, 2, 7, 8, 13)
            quarterRound(state, 3, 4, 9, 14)
        }

        val output = ByteArray(32)
        val outputWords = longArrayOf(
            state[0], state[1], state[2], state[3],
            state[12], state[13], state[14], state[15]
        )
        for (i in 0 until 8) {
            System.arraycopy(u32ToLe(outputWords[i]), 0, output, i * 4, 4)
        }
        return output
    }

    // --- Shared Key ---

    fun computeSharedKey(privateKey: ByteArray, pumpPublicKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        sodium.crypto_scalarmult(sharedSecret, privateKey, pumpPublicKey)
        return hchacha20(sharedSecret, ByteArray(16))
    }

    // --- XChaCha20-Poly1305 Encrypt/Decrypt ---

    fun encrypt(
        payload: ByteArray,
        sharedKey: ByteArray,
        rebootCounter: Long,
        writeCounter: Long
    ): ByteArray {
        val nonce = ByteArray(24)
        SecureRandom().nextBytes(nonce)

        // Build buffer: payload + reboot_counter(4 LE) + write_counter(8 LE)
        val buffer = ByteArray(payload.size + 12)
        System.arraycopy(payload, 0, buffer, 0, payload.size)
        val bb = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(rebootCounter.toInt())
        bb.putLong(writeCounter)
        System.arraycopy(bb.array(), 0, buffer, payload.size, 12)

        // Encrypt: ciphertext includes 16-byte Poly1305 tag
        val ciphertext = ByteArray(buffer.size + 16)
        val ciphertextLen = longArrayOf(0)
        sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
            ciphertext, ciphertextLen,
            buffer, buffer.size.toLong(),
            null, 0,
            null,
            nonce,
            sharedKey
        )

        // Output: ciphertext + nonce
        val result = ByteArray(ciphertextLen[0].toInt() + 24)
        System.arraycopy(ciphertext, 0, result, 0, ciphertextLen[0].toInt())
        System.arraycopy(nonce, 0, result, ciphertextLen[0].toInt(), 24)
        return result
    }

    fun decrypt(encryptedPayload: ByteArray, sharedKey: ByteArray): DecryptResult {
        if (encryptedPayload.size < 40) {
            throw IllegalArgumentException("Encrypted payload too short")
        }
        val nonce = encryptedPayload.copyOfRange(encryptedPayload.size - 24, encryptedPayload.size)
        val ciphertext = encryptedPayload.copyOfRange(0, encryptedPayload.size - 24)

        val plaintext = ByteArray(ciphertext.size - 16)
        val plaintextLen = longArrayOf(0)
        val rc = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(
            plaintext, plaintextLen,
            null,
            ciphertext, ciphertext.size.toLong(),
            null, 0,
            nonce,
            sharedKey
        )
        if (rc != 0) throw SecurityException("Decryption failed (invalid key or tampered data)")

        val len = plaintextLen[0].toInt()
        val data = plaintext.copyOfRange(0, len - 12)
        val bb = ByteBuffer.wrap(plaintext, len - 12, 12).order(ByteOrder.LITTLE_ENDIAN)
        val rebootCounter = bb.int.toLong() and 0xFFFFFFFFL
        val readCounter = bb.long

        return DecryptResult(data, rebootCounter, readCounter)
    }

    data class DecryptResult(
        val data: ByteArray,
        val rebootCounter: Long,
        val readCounter: Long
    )

    // --- Bolus payloads ---

    fun buildStartBolusPayload(totalUnits: Double, durationMinutes: Int = 0, immediateUnits: Double = 0.0): ByteArray {
        val totalScaled = Math.round(totalUnits * 100).toInt()
        val immediateScaled = Math.round(immediateUnits * 100).toInt()
        val bolusType = if (durationMinutes == 0) 1 else 2

        val buf = ByteBuffer.allocate(13).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(totalScaled)
        buf.putInt(durationMinutes)
        buf.putInt(immediateScaled)
        buf.put(bolusType.toByte())
        return buf.array()
    }

    fun buildStopBolusPayload(kind: String): ByteArray {
        val typeVal = when (kind) {
            "fast" -> 1
            "extended", "combined" -> 2
            else -> throw IllegalArgumentException("Bolus type must be: fast, extended, combined")
        }
        val buf = ByteArray(13)
        buf[12] = typeVal.toByte()
        return buf
    }

    // --- BLE Frame chunking ---

    fun chunkPayload(data: ByteArray): List<ByteArray> {
        if (data.isEmpty()) return listOf(byteArrayOf(0x10))
        val total = maxOf(1, (data.size + 18) / 19)
        val frames = mutableListOf<ByteArray>()
        for (idx in 0 until total) {
            val start = idx * 19
            val end = minOf(start + 19, data.size)
            val chunk = data.copyOfRange(start, end)
            val header = (((idx + 1) shl 4) and 0xF0) or (total and 0x0F)
            frames.add(byteArrayOf(header.toByte()) + chunk)
        }
        return frames
    }
}
