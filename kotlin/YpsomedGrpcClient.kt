package com.ypsopump.sdk

import android.os.Build
import io.grpc.ManagedChannelBuilder
import io.proregia.bluetooth.proto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cliente gRPC para el servidor de Ypsomed (connect.ml.pr.sec01.proregia.io:8090).
 * Maneja NonceRequest y EncryptKey.
 */
class YpsomedGrpcClient {

    companion object {
        private const val GRPC_HOST = "connect.ml.pr.sec01.proregia.io"
        private const val GRPC_PORT = 8090

        private const val APP_NAME = "mylife app"
        private const val APP_PACKAGE = "net.sinovo.mylife.app"
        private const val LIBRARY_VERSION = "1.0.0.0"
    }

    private val channel by lazy {
        ManagedChannelBuilder
            .forAddress(GRPC_HOST, GRPC_PORT)
            .useTransportSecurity()
            .build()
    }

    private fun buildMetrics(): Metrics {
        return Metrics.newBuilder()
            .setPlatform("Android")
            .setModel(Build.MODEL)
            .setOsType("Phone")
            .setOsVersion(Build.VERSION.RELEASE)
            .setManufacturer(Build.MANUFACTURER)
            .setDeviceSerial("na")
            .setApplicationName(APP_NAME)
            .setApplicationPackage(APP_PACKAGE)
            .setLibraryVersion(LIBRARY_VERSION)
            .setXamarin(true)
            .build()
    }

    /**
     * Paso 1 del pairing: solicitar server nonce.
     *
     * @param btAddress BT address hex derivado del serial (6 bytes hex)
     * @param deviceId UUID único del dispositivo/app
     * @return server nonce como hex string
     */
    suspend fun getServerNonce(btAddress: String, deviceId: String): String =
        withContext(Dispatchers.IO) {
            val stub = NonceRequestGrpc.newBlockingStub(channel)
            val request = DeviceIdentifier.newBuilder()
                .setDeviceId(deviceId)
                .setBtAddress(btAddress)
                .setMetrics(buildMetrics())
                .build()
            val response = stub.send(request)
            response.serverNonce
        }

    /**
     * Paso 4 del pairing: intercambio de claves con Play Integrity token.
     *
     * @param btAddress BT address hex (6 bytes)
     * @param serverNonce Server nonce hex (24 bytes)
     * @param challenge Challenge hex del pump (32 bytes)
     * @param pumpPublicKey Pump public key hex (32 bytes)
     * @param appPublicKey App public key hex (32 bytes)
     * @param integrityToken Play Integrity token (JWT string)
     * @return encrypted_bytes hex (116 bytes) para escribir en la bomba
     */
    suspend fun encryptKeyRequest(
        btAddress: String,
        serverNonce: String,
        challenge: String,
        pumpPublicKey: String,
        appPublicKey: String,
        integrityToken: String
    ): String = withContext(Dispatchers.IO) {
        val stub = EncryptKeyGrpc.newBlockingStub(channel)
        val request = EncryptKeyRequest.newBuilder()
            .setChallenge(challenge.uppercase())
            .setPumpPublicKey(pumpPublicKey.uppercase())
            .setAppPublicKey(appPublicKey.uppercase())
            .setBtAddress(btAddress.uppercase())
            .setMessageAttestationObject(integrityToken)
            .setNonce(serverNonce.uppercase())
            .setMetrics(buildMetrics())
            .build()
        val response = stub.send(request)
        response.encryptedBytes
    }

    fun shutdown() {
        channel.shutdownNow()
    }
}
