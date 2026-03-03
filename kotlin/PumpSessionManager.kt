package com.ypsopump.sdk

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.util.UUID

/**
 * Gestiona la sesión con la bomba YpsoPump.
 * Persiste claves, contadores y estado en SharedPreferences.
 */
class PumpSessionManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "ypsopump_session"
        private const val BASE_MAC_PREFIX = "EC:2A:F0"
        private val AUTH_SALT = byteArrayOf(
            0x4F, 0xC2.toByte(), 0x45, 0x4D,
            0x9B.toByte(), 0x81.toByte(), 0x59, 0xA4.toByte(),
            0x93.toByte(), 0xBB.toByte()
        )

        // Claves de SharedPreferences
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PRIVATE_KEY = "private_key"       // hex X25519 private key
        private const val KEY_SERIAL = "serial"
        private const val KEY_MAC = "mac"
        private const val KEY_BT_ADDRESS = "bt_address"         // hex 6 bytes
        private const val KEY_BLE_ADDRESS = "ble_address"       // AA:BB:CC:DD:EE:FF
        private const val KEY_SHARED_KEY = "shared_key"         // hex 32 bytes
        private const val KEY_EXPIRES_AT = "shared_key_expires_at"  // epoch millis
        private const val KEY_SERVER_NONCE = "server_nonce"     // hex 24 bytes
        private const val KEY_CHALLENGE = "challenge"           // hex 32 bytes
        private const val KEY_PUMP_PUBLIC_KEY = "pump_public_key" // hex 32 bytes
        private const val KEY_READ_COUNTER = "read_counter"
        private const val KEY_WRITE_COUNTER = "write_counter"
        private const val KEY_REBOOT_COUNTER = "reboot_counter"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Device identity (generado una vez) ---

    fun getOrCreateDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    // --- Derivaciones desde serial ---

    /**
     * Convierte serial de la bomba (7 dígitos) a MAC address.
     * Ejemplo: 10123456 → EC:2A:F0:01:E2:40
     */
    fun serialToMac(serial: String): String {
        val num = serial.toLong().let { if (it > 10_000_000) it - 10_000_000 else it }
        val hexStr = String.format("%06X", num)
        return "$BASE_MAC_PREFIX:${hexStr.substring(0, 2)}:${hexStr.substring(2, 4)}:${hexStr.substring(4, 6)}"
    }

    /**
     * Convierte serial a BT address (6 bytes hex).
     * Ejemplo: 10123456 → ec2af001e240
     */
    fun serialToBtAddress(serial: String): String {
        val mac = serialToMac(serial)
        return mac.replace(":", "").lowercase()
    }

    /**
     * Calcula el password BLE: MD5(mac_bytes + salt).
     * Devuelve 16 bytes como hex string.
     */
    fun computePassword(mac: String): ByteArray {
        val macBytes = mac.replace(":", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val buf = macBytes + AUTH_SALT
        return MessageDigest.getInstance("MD5").digest(buf)
    }

    // --- Persistencia de sesión ---

    fun savePumpDiscovery(serial: String, bleAddress: String) {
        val mac = serialToMac(serial)
        val btAddress = serialToBtAddress(serial)
        prefs.edit()
            .putString(KEY_SERIAL, serial)
            .putString(KEY_MAC, mac)
            .putString(KEY_BT_ADDRESS, btAddress)
            .putString(KEY_BLE_ADDRESS, bleAddress)
            .apply()
    }

    fun saveServerNonce(serverNonce: String) {
        prefs.edit().putString(KEY_SERVER_NONCE, serverNonce).apply()
    }

    fun savePumpKeyExchangeData(challenge: String, pumpPublicKey: String) {
        prefs.edit()
            .putString(KEY_CHALLENGE, challenge)
            .putString(KEY_PUMP_PUBLIC_KEY, pumpPublicKey)
            .apply()
    }

    /**
     * Guarda la sesión completa tras el pairing exitoso.
     * Inicializa contadores a 0.
     */
    fun saveCompletedSession(
        sharedKey: String,
        challenge: String,
        pumpPublicKey: String,
        serverNonce: String,
        privateKeyHex: String
    ) {
        val expiresAt = System.currentTimeMillis() + 28L * 24 * 3600 * 1000
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, privateKeyHex)
            .putString(KEY_SHARED_KEY, sharedKey)
            .putString(KEY_CHALLENGE, challenge)
            .putString(KEY_PUMP_PUBLIC_KEY, pumpPublicKey)
            .putString(KEY_SERVER_NONCE, serverNonce)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putLong(KEY_READ_COUNTER, 0)
            .putLong(KEY_WRITE_COUNTER, 0)
            .putLong(KEY_REBOOT_COUNTER, 0)
            .apply()
    }

    // --- Lectura de estado ---

    val serial: String? get() = prefs.getString(KEY_SERIAL, null)
    val mac: String? get() = prefs.getString(KEY_MAC, null)
    val btAddress: String? get() = prefs.getString(KEY_BT_ADDRESS, null)
    val bleAddress: String? get() = prefs.getString(KEY_BLE_ADDRESS, null)
    val sharedKey: String? get() = prefs.getString(KEY_SHARED_KEY, null)
    val serverNonce: String? get() = prefs.getString(KEY_SERVER_NONCE, null)
    val challenge: String? get() = prefs.getString(KEY_CHALLENGE, null)
    val pumpPublicKey: String? get() = prefs.getString(KEY_PUMP_PUBLIC_KEY, null)
    val privateKeyHex: String? get() = prefs.getString(KEY_PRIVATE_KEY, null)

    val expiresAt: Long get() = prefs.getLong(KEY_EXPIRES_AT, 0)

    val isSessionValid: Boolean
        get() {
            val key = sharedKey ?: return false
            return key.isNotEmpty() && System.currentTimeMillis() < expiresAt
        }

    // --- Contadores ---

    var readCounter: Long
        get() = prefs.getLong(KEY_READ_COUNTER, 0)
        set(value) = prefs.edit().putLong(KEY_READ_COUNTER, value).apply()

    var writeCounter: Long
        get() = prefs.getLong(KEY_WRITE_COUNTER, 0)
        set(value) = prefs.edit().putLong(KEY_WRITE_COUNTER, value).apply()

    var rebootCounter: Long
        get() = prefs.getLong(KEY_REBOOT_COUNTER, 0)
        set(value) = prefs.edit().putLong(KEY_REBOOT_COUNTER, value).apply()

    fun resetCounters() {
        prefs.edit()
            .putLong(KEY_READ_COUNTER, 0)
            .putLong(KEY_WRITE_COUNTER, 0)
            .putLong(KEY_REBOOT_COUNTER, 0)
            .apply()
    }

    fun incrementWriteCounter(): Long {
        val next = writeCounter + 1
        writeCounter = next
        return next
    }

    // --- Clear ---

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
