package com.ypsopump.app

import android.os.Build
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.proregia.bluetooth.proto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YpsomedGrpcClient {

    companion object {
        private const val HOST = "connect.ml.pr.sec01.proregia.io"
        private const val PORT = 8090
    }

    private val channel: ManagedChannel by lazy {
        ManagedChannelBuilder.forAddress(HOST, PORT)
            .useTransportSecurity()
            .build()
    }

    private fun metrics(): Metrics = Metrics.newBuilder()
        .setPlatform("Android")
        .setModel(Build.MODEL)
        .setOsType("Phone")
        .setOsVersion(Build.VERSION.RELEASE)
        .setManufacturer(Build.MANUFACTURER)
        .setDeviceSerial("na")
        .setApplicationName("mylife app")
        .setApplicationPackage("net.sinovo.mylife.app")
        .setLibraryVersion("1.0.0.0")
        .setXamarin(true)
        .build()

    suspend fun getServerNonce(btAddressHex: String, deviceId: String): String =
        withContext(Dispatchers.IO) {
            val stub = NonceRequestGrpc.newBlockingStub(channel)
            val request = DeviceIdentifier.newBuilder()
                .setDeviceId(deviceId)
                .setBtAddress(btAddressHex)
                .setMetrics(metrics())
                .build()
            stub.send(request).serverNonce
        }

    suspend fun encryptKeyRequest(
        btAddressHex: String,
        serverNonceHex: String,
        challengeHex: String,
        pumpPublicKeyHex: String,
        appPublicKeyHex: String,
        integrityToken: String
    ): String = withContext(Dispatchers.IO) {
        val stub = EncryptKeyGrpc.newBlockingStub(channel)
        val request = EncryptKeyRequest.newBuilder()
            .setChallenge(challengeHex.uppercase())
            .setPumpPublicKey(pumpPublicKeyHex.uppercase())
            .setAppPublicKey(appPublicKeyHex.uppercase())
            .setBtAddress(btAddressHex.uppercase())
            .setMessageAttestationObject(integrityToken)
            .setNonce(serverNonceHex.uppercase())
            .setMetrics(metrics())
            .build()
        stub.send(request).encryptedBytes
    }

    fun shutdown() {
        if (::channel.isInitialized) channel.shutdownNow()
    }

    private val ManagedChannel.isInitialized: Boolean get() = true
}
