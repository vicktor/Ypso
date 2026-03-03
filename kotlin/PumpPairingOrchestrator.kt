package com.ypsopump.sdk

import android.util.Log

/**
 * Orquesta el flujo completo de pairing con la bomba YpsoPump.
 *
 * Flujo (la app controla el BLE, el servidor provee el Play Integrity token):
 *
 * 1. App descubre bomba por BLE → serial + ble_address
 * 2. App se conecta BLE, escribe password en CHAR_AUTH_PASSWORD
 * 3. App lee challenge (32B) + pump_public_key (32B) de CHAR_REQUEST_PUBLIC_KEY
 * 4. App pide server_nonce a Ypsomed gRPC (NonceRequest)
 * 5. App pide Play Integrity token al servidor REST
 * 6. App llama EncryptKey gRPC con token + datos de la bomba
 * 7. App escribe encrypted_bytes en CHAR_WRITE_CHALLENGE
 * 8. App computa shared_key localmente (X25519 + HChaCha20)
 * 9. App persiste sesión con contadores a 0
 *
 * Este orquestador maneja los pasos 4-6 y 8-9.
 * La app debe manejar BLE (pasos 1-3 y 7) externamente.
 */
class PumpPairingOrchestrator(
    private val session: PumpSessionManager,
    private val grpcClient: YpsomedGrpcClient,
    private val integrityClient: IntegrityTokenClient
) {

    companion object {
        private const val TAG = "PumpPairing"
    }

    /**
     * Resultado del paso de preparación (pre-BLE).
     * Contiene datos necesarios para autenticar con la bomba.
     */
    data class DiscoveryResult(
        val mac: String,
        val password: ByteArray,
        val btAddress: String,
        val deviceId: String
    )

    /**
     * Paso 1: Preparar datos tras descubrir la bomba.
     * Llamar antes de conectar por BLE.
     *
     * @param serial Serial de la bomba (de nombre BLE "YpsoPump_XXXXXXX")
     * @param bleAddress Dirección BLE descubierta
     * @return Datos necesarios para autenticar con la bomba por BLE
     */
    fun prepareDiscovery(serial: String, bleAddress: String): DiscoveryResult {
        session.savePumpDiscovery(serial, bleAddress)
        val mac = session.mac!!
        val password = session.computePassword(mac)
        val btAddress = session.btAddress!!
        val deviceId = session.getOrCreateDeviceId()

        Log.d(TAG, "Pump: $serial, MAC: $mac, BT: $btAddress")
        return DiscoveryResult(mac, password, btAddress, deviceId)
    }

    /**
     * Resultado del intercambio de claves (post-BLE read, pre-BLE write).
     */
    data class ExchangeResult(
        val encryptedBytes: ByteArray
    )

    /**
     * Paso 2: Intercambio de claves completo.
     * Llamar después de leer challenge + pump_public_key de la bomba por BLE.
     *
     * Ejecuta:
     *   - gRPC NonceRequest → server_nonce
     *   - REST → Play Integrity token
     *   - gRPC EncryptKey → encrypted_bytes
     *
     * @param challengeHex Challenge hex (32 bytes leído de la bomba)
     * @param pumpPublicKeyHex Pump public key hex (32 bytes leído de la bomba)
     * @param appPublicKeyHex App public key hex (32 bytes, generado localmente)
     * @return encrypted_bytes para escribir en CHAR_WRITE_CHALLENGE de la bomba
     */
    suspend fun performKeyExchange(
        challengeHex: String,
        pumpPublicKeyHex: String,
        appPublicKeyHex: String
    ): ExchangeResult {
        val btAddress = session.btAddress
            ?: throw IllegalStateException("No BT address. Call prepareDiscovery first.")
        val deviceId = session.getOrCreateDeviceId()

        // Guardar datos del pump
        session.savePumpKeyExchangeData(challengeHex, pumpPublicKeyHex)

        // 1. Obtener server nonce vía gRPC
        Log.d(TAG, "Requesting server nonce...")
        val serverNonceHex = grpcClient.getServerNonce(btAddress, deviceId)
        session.saveServerNonce(serverNonceHex)
        Log.d(TAG, "Server nonce: ${serverNonceHex.take(16)}...")

        // 2. Obtener Play Integrity token del servidor REST
        Log.d(TAG, "Requesting Play Integrity token...")
        val integrityToken = integrityClient.getToken(serverNonceHex)
        Log.d(TAG, "Integrity token: ${integrityToken.take(40)}...")

        // 3. Llamar EncryptKey gRPC
        Log.d(TAG, "Requesting encrypt key...")
        val encryptedBytesHex = grpcClient.encryptKeyRequest(
            btAddress = btAddress,
            serverNonce = serverNonceHex,
            challenge = challengeHex,
            pumpPublicKey = pumpPublicKeyHex,
            appPublicKey = appPublicKeyHex,
            integrityToken = integrityToken
        )
        Log.d(TAG, "Encrypted bytes received (${encryptedBytesHex.length / 2} bytes)")

        return ExchangeResult(
            encryptedBytes = encryptedBytesHex.hexToByteArray()
        )
    }

    /**
     * Paso 3: Confirmar pairing tras escribir encrypted_bytes en la bomba.
     * Computa shared_key y persiste la sesión con contadores a 0.
     *
     * @param sharedKeyHex Shared key hex (32 bytes, computado localmente via X25519 + HChaCha20)
     * @param privateKeyHex App private key hex (para regenerar shared key si expira)
     */
    fun confirmPairing(sharedKeyHex: String, privateKeyHex: String) {
        val challenge = session.challenge
            ?: throw IllegalStateException("No challenge. Call performKeyExchange first.")
        val pumpPublicKey = session.pumpPublicKey
            ?: throw IllegalStateException("No pump public key.")
        val serverNonce = session.serverNonce
            ?: throw IllegalStateException("No server nonce.")

        session.saveCompletedSession(
            sharedKey = sharedKeyHex,
            challenge = challenge,
            pumpPublicKey = pumpPublicKey,
            serverNonce = serverNonce,
            privateKeyHex = privateKeyHex
        )

        Log.i(TAG, "Pairing complete. Session valid for 28 days.")
    }
}

private fun String.hexToByteArray(): ByteArray {
    check(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
