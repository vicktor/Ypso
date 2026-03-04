package com.ypsopump.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {

    // ---- CONFIG: Editar estos valores ----
    private val INTEGRITY_SERVER_URL = "http://YOUR_SERVER:8000"
    private val INTEGRITY_API_KEY = "YOUR_API_KEY"
    // --------------------------------------

    private lateinit var ble: PumpBleManager
    private lateinit var session: PumpSessionManager
    private lateinit var grpc: YpsomedGrpcClient
    private lateinit var integrity: IntegrityTokenClient

    private var scanResult: PumpBleManager.ScanResultData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ble = PumpBleManager(this)
        session = PumpSessionManager(this)
        grpc = YpsomedGrpcClient()
        integrity = IntegrityTokenClient(INTEGRITY_SERVER_URL, INTEGRITY_API_KEY)

        requestPermissions()

        setContent {
            var log by remember { mutableStateOf("=== YpsoPump App ===\n") }
            var scanning by remember { mutableStateOf(false) }
            var pairing by remember { mutableStateOf(false) }
            var testing by remember { mutableStateOf(false) }
            val scrollState = rememberScrollState()
            val scope = rememberCoroutineScope()

            fun appendLog(msg: String) {
                log += "$msg\n"
            }

            ble.onLog = { msg -> appendLog(msg) }

            // Auto-scroll on new log
            LaunchedEffect(log) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            MaterialTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    scanning = true
                                    scanResult = null
                                    appendLog("\n--- SCAN ---")
                                    try {
                                        scanResult = ble.scanForPump()
                                        if (scanResult == null) appendLog("No pump found")
                                    } catch (e: Exception) {
                                        appendLog("Scan error: ${e.message}")
                                    }
                                    scanning = false
                                }
                            },
                            enabled = !scanning && !pairing
                        ) { Text("Scan") }

                        Button(
                            onClick = {
                                scope.launch {
                                    pairing = true
                                    appendLog("\n--- PAIRING ---")
                                    try {
                                        doPairing(scanResult!!) { appendLog(it) }
                                        appendLog("PAIRING OK!")
                                    } catch (e: Exception) {
                                        appendLog("PAIRING FAILED: ${e.message}")
                                        e.printStackTrace()
                                    }
                                    pairing = false
                                }
                            },
                            enabled = scanResult != null && !pairing && !scanning
                        ) { Text("Pair") }

                        Button(
                            onClick = {
                                scope.launch {
                                    testing = true
                                    appendLog("\n--- TEST BOLUS ---")
                                    try {
                                        doTestBolus { appendLog(it) }
                                        appendLog("BOLUS TEST OK!")
                                    } catch (e: Exception) {
                                        appendLog("BOLUS FAILED: ${e.message}")
                                    }
                                    testing = false
                                }
                            },
                            enabled = session.isSessionValid && !testing && !pairing && !scanning
                        ) { Text("Test Bolus") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = log,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }

    private suspend fun doPairing(pump: PumpBleManager.ScanResultData, log: (String) -> Unit) {
        // 1. Save discovery
        session.savePumpDiscovery(pump.serial, pump.address)
        val mac = session.mac!!
        val btAddress = session.btAddress!!
        val deviceId = session.deviceId
        val password = PumpCrypto.computePassword(mac)
        log("Serial: ${pump.serial}")
        log("MAC: $mac")
        log("BT Address: $btAddress")
        log("Password: ${password.toHex()}")

        // 2. Generate X25519 keypair
        val (privateKey, publicKey) = PumpCrypto.generateX25519KeyPair()
        val pubKeyHex = publicKey.toHex()
        log("App Public Key: $pubKeyHex")

        // 3. Connect BLE
        log("Connecting BLE...")
        val connected = ble.connect(pump.address)
        if (!connected) throw RuntimeException("BLE connection failed")
        log("Connected!")
        delay(1000)

        try {
            // 4. Authenticate
            log("Authenticating...")
            ble.writeCharacteristic(PumpBleConstants.CHAR_AUTH_PASSWORD, password)
            delay(500)
            log("Authenticated")

            // 5. Read versions
            val masterVer = ble.readCharacteristic(PumpBleConstants.CHAR_MASTER_VERSION)
            log("Master version: ${String(masterVer).trim('\u0000')}")

            // 6. Read challenge + pump public key (extended read, 64 bytes)
            log("Reading pump public key & challenge...")
            val pubKeyData = ble.readExtended(PumpBleConstants.CHAR_REQUEST_PUBLIC_KEY)
            if (pubKeyData.size < 64) throw RuntimeException("pubKeyData too short: ${pubKeyData.size}")
            val challenge = pubKeyData.copyOfRange(0, 32)
            val pumpPublicKey = pubKeyData.copyOfRange(32, 64)
            log("Challenge: ${challenge.toHex()}")
            log("Pump PubKey: ${pumpPublicKey.toHex()}")

            // 7. Get server nonce (gRPC to Ypsomed)
            log("Getting server nonce (gRPC)...")
            val serverNonceHex = grpc.getServerNonce(btAddress, deviceId)
            log("Server Nonce: $serverNonceHex")
            session.serverNonceHex = serverNonceHex

            // 8. Get Play Integrity token (REST to our server)
            log("Getting Play Integrity token...")
            val integrityToken = integrity.getToken(serverNonceHex)
            log("Token: ${integrityToken.take(50)}...")

            // 9. EncryptKey (gRPC to Ypsomed)
            log("Calling EncryptKey (gRPC)...")
            val encryptedBytesHex = grpc.encryptKeyRequest(
                btAddressHex = btAddress,
                serverNonceHex = serverNonceHex,
                challengeHex = challenge.toHex(),
                pumpPublicKeyHex = pumpPublicKey.toHex(),
                appPublicKeyHex = pubKeyHex,
                integrityToken = integrityToken
            )
            val encryptedBytes = encryptedBytesHex.hexToBytes()
            log("Encrypted bytes: ${encryptedBytes.size} bytes")

            // 10. Write encrypted challenge response to pump
            log("Writing challenge response to pump...")
            ble.writeExtended(PumpBleConstants.CHAR_WRITE_CHALLENGE, encryptedBytes)
            log("Challenge written!")

            // 11. Compute shared key
            val sharedKey = PumpCrypto.computeSharedKey(privateKey, pumpPublicKey)
            log("Shared Key: ${sharedKey.toHex()}")

            // 12. Persist session
            session.saveCompletedSession(
                sharedKey = sharedKey.toHex(),
                privateKey = privateKey.toHex(),
                challenge = challenge.toHex(),
                pumpPublicKey = pumpPublicKey.toHex(),
                serverNonce = serverNonceHex
            )
            log("Session saved (28 days, counters=0)")
        } finally {
            ble.disconnect()
            log("BLE disconnected")
        }
    }

    private suspend fun doTestBolus(log: (String) -> Unit) {
        val sharedKey = session.sharedKeyHex?.hexToBytes()
            ?: throw RuntimeException("No shared key")
        val bleAddr = session.bleAddress
            ?: throw RuntimeException("No BLE address")
        val mac = session.mac!!
        val password = PumpCrypto.computePassword(mac)

        // Build 0.0 UI bolus (safe test — zero units)
        val payload = PumpCrypto.buildStartBolusPayload(totalUnits = 0.0, durationMinutes = 0)
        val withCrc = payload + PumpCrypto.crc16(payload)
        log("Payload (plain): ${withCrc.toHex()}")

        // Encrypt
        val writeCounter = session.writeCounter + 1
        val encrypted = PumpCrypto.encrypt(withCrc, sharedKey, session.rebootCounter, writeCounter)
        session.writeCounter = writeCounter
        log("Encrypted: ${encrypted.size} bytes")

        // Connect and send
        log("Connecting BLE...")
        val connected = ble.connect(bleAddr)
        if (!connected) throw RuntimeException("BLE connection failed")

        try {
            delay(1000)
            log("Authenticating...")
            ble.writeCharacteristic(PumpBleConstants.CHAR_AUTH_PASSWORD, password)
            delay(500)

            log("Sending encrypted bolus command...")
            ble.writeExtended(PumpBleConstants.CHAR_BOLUS_START_STOP, encrypted)
            log("Bolus command sent!")

            // Read status to verify
            delay(500)
            log("Reading bolus status...")
            val statusRaw = ble.readExtended(PumpBleConstants.CHAR_BOLUS_STATUS)
            log("Status raw: ${statusRaw.toHex()} (${statusRaw.size} bytes)")
        } finally {
            ble.disconnect()
            log("Disconnected")
        }
    }

    private fun requestPermissions() {
        val needed = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
                .launch(needed.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ble.disconnect()
        grpc.shutdown()
    }
}
