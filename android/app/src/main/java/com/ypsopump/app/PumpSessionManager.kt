package com.ypsopump.app

import android.content.Context
import android.content.SharedPreferences

class PumpSessionManager(context: Context) {

    companion object {
        private const val PREFS = "ypsopump_session"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // --- Getters ---
    var deviceId: String
        get() = prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also { deviceId = it }
        set(v) = prefs.edit().putString("device_id", v).apply()

    var serial: String?
        get() = prefs.getString("serial", null)
        set(v) = prefs.edit().putString("serial", v).apply()

    var mac: String?
        get() = prefs.getString("mac", null)
        set(v) = prefs.edit().putString("mac", v).apply()

    var btAddress: String?
        get() = prefs.getString("bt_address", null)
        set(v) = prefs.edit().putString("bt_address", v).apply()

    var bleAddress: String?
        get() = prefs.getString("ble_address", null)
        set(v) = prefs.edit().putString("ble_address", v).apply()

    var privateKeyHex: String?
        get() = prefs.getString("private_key", null)
        set(v) = prefs.edit().putString("private_key", v).apply()

    var sharedKeyHex: String?
        get() = prefs.getString("shared_key", null)
        set(v) = prefs.edit().putString("shared_key", v).apply()

    var serverNonceHex: String?
        get() = prefs.getString("server_nonce", null)
        set(v) = prefs.edit().putString("server_nonce", v).apply()

    var challengeHex: String?
        get() = prefs.getString("challenge", null)
        set(v) = prefs.edit().putString("challenge", v).apply()

    var pumpPublicKeyHex: String?
        get() = prefs.getString("pump_public_key", null)
        set(v) = prefs.edit().putString("pump_public_key", v).apply()

    var expiresAt: Long
        get() = prefs.getLong("expires_at", 0)
        set(v) = prefs.edit().putLong("expires_at", v).apply()

    var readCounter: Long
        get() = prefs.getLong("read_counter", 0)
        set(v) = prefs.edit().putLong("read_counter", v).apply()

    var writeCounter: Long
        get() = prefs.getLong("write_counter", 0)
        set(v) = prefs.edit().putLong("write_counter", v).apply()

    var rebootCounter: Long
        get() = prefs.getLong("reboot_counter", 0)
        set(v) = prefs.edit().putLong("reboot_counter", v).apply()

    val isSessionValid: Boolean
        get() = !sharedKeyHex.isNullOrEmpty() && System.currentTimeMillis() < expiresAt

    // --- Bulk operations ---

    fun savePumpDiscovery(serial: String, bleAddress: String) {
        val mac = PumpCrypto.serialToMac(serial)
        val btAddr = PumpCrypto.serialToBtAddress(serial).toHex()
        prefs.edit()
            .putString("serial", serial)
            .putString("mac", mac)
            .putString("bt_address", btAddr)
            .putString("ble_address", bleAddress)
            .apply()
    }

    fun saveCompletedSession(
        sharedKey: String,
        privateKey: String,
        challenge: String,
        pumpPublicKey: String,
        serverNonce: String
    ) {
        prefs.edit()
            .putString("shared_key", sharedKey)
            .putString("private_key", privateKey)
            .putString("challenge", challenge)
            .putString("pump_public_key", pumpPublicKey)
            .putString("server_nonce", serverNonce)
            .putLong("expires_at", System.currentTimeMillis() + 28L * 24 * 3600 * 1000)
            .putLong("read_counter", 0)
            .putLong("write_counter", 0)
            .putLong("reboot_counter", 0)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
