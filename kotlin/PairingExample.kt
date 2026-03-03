package com.ypsopump.sdk.example

import com.ypsopump.sdk.*

/**
 * Ejemplo de uso del flujo completo de pairing desde una Activity/ViewModel.
 *
 * Prerequisitos:
 *   - Dependencias Gradle:
 *       implementation("io.grpc:grpc-okhttp:1.62.0")
 *       implementation("io.grpc:grpc-protobuf-lite:1.62.0")
 *       implementation("io.grpc:grpc-stub:1.62.0")
 *
 *   - Proto compilation en build.gradle:
 *       protobuf {
 *           protoc { artifact = "com.google.protobuf:protoc:3.25.1" }
 *           plugins { grpc { artifact = "io.grpc:protoc-gen-grpc-java:1.62.0" } }
 *           generateProtoTasks { all().forEach { it.plugins { grpc {} } } }
 *       }
 *
 *   - Copiar ypsomed_service.proto a app/src/main/proto/
 *
 *   - Permisos en AndroidManifest.xml:
 *       <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
 *       <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
 *       <uses-permission android:name="android.permission.INTERNET" />
 *
 *   - Para cripto X25519: implementation("org.whispersystems:curve25519-android:0.5.0")
 *     o usar BouncyCastle / Tink
 */

/*
 * Flujo completo de pairing (pseudo-código con BLE genérico):
 *
 * // --- Inicializar componentes ---
 * val session = PumpSessionManager(context)
 * val grpc = YpsomedGrpcClient()
 * val integrity = IntegrityTokenClient(
 *     serverUrl = "https://your-server.com",
 *     apiKey = "your-api-key"
 * )
 * val orchestrator = PumpPairingOrchestrator(session, grpc, integrity)
 *
 * // --- Paso 1: Descubrir bomba por BLE ---
 * // BLE scan: buscar dispositivo con nombre "YpsoPump_XXXXXXX"
 * val pumpName = "YpsoPump_1234567"
 * val serial = pumpName.removePrefix("YpsoPump_")
 * val bleAddress = device.address  // "AA:BB:CC:DD:EE:FF"
 *
 * // Preparar datos de descubrimiento
 * val discovery = orchestrator.prepareDiscovery(serial, bleAddress)
 *
 * // --- Paso 2: Conectar BLE y autenticar ---
 * // gatt.connect(bleAddress)
 * // gatt.writeCharacteristic(CHAR_AUTH_PASSWORD, discovery.password)
 * // sleep(500ms)
 *
 * // --- Paso 3: Leer challenge + pump public key ---
 * // val pubKeyData = readExtendedCharacteristic(CHAR_REQUEST_PUBLIC_KEY, CHAR_EXTENDED_READ)
 * // pubKeyData debe ser >= 64 bytes:
 * //   challenge = pubKeyData[0..31]  (32 bytes)
 * //   pumpPublicKey = pubKeyData[32..63]  (32 bytes)
 * // val challengeHex = challenge.toHexString()
 * // val pumpPublicKeyHex = pumpPublicKey.toHexString()
 *
 * // --- Paso 4: Generar keypair X25519 local ---
 * // val appKeyPair = X25519.generateKeyPair()
 * // val appPublicKeyHex = appKeyPair.publicKey.toHexString()
 * // val appPrivateKeyHex = appKeyPair.privateKey.toHexString()
 *
 * // --- Paso 5: Key exchange (gRPC + REST + gRPC) ---
 * // Esta llamada hace:
 * //   1. gRPC NonceRequest → server_nonce
 * //   2. REST → Play Integrity token
 * //   3. gRPC EncryptKey → encrypted_bytes
 * // val exchange = orchestrator.performKeyExchange(
 * //     challengeHex, pumpPublicKeyHex, appPublicKeyHex
 * // )
 *
 * // --- Paso 6: Escribir encrypted_bytes en la bomba ---
 * // writeExtendedCharacteristic(CHAR_WRITE_CHALLENGE, exchange.encryptedBytes)
 *
 * // --- Paso 7: Computar shared key localmente ---
 * // val sharedSecret = X25519.computeSharedSecret(appKeyPair.privateKey, pumpPublicKey)
 * // val sharedKey = HChaCha20.derive(sharedSecret, nonce = ByteArray(16))
 * // val sharedKeyHex = sharedKey.toHexString()
 *
 * // --- Paso 8: Confirmar y persistir ---
 * // orchestrator.confirmPairing(sharedKeyHex, appPrivateKeyHex)
 *
 * // ¡Pairing completado! Session válida 28 días.
 * // session.isSessionValid == true
 * // session.sharedKey contiene la clave para XChaCha20-Poly1305
 * // session.readCounter / writeCounter / rebootCounter inicializados a 0
 *
 *
 * // --- Lectura extendida de características BLE ---
 * // Protocolo de framing: 20 bytes por frame
 * // Header byte: (frame_index << 4) | total_frames
 * //
 * // fun readExtendedCharacteristic(firstUuid: UUID, extUuid: UUID): ByteArray {
 * //     val first = gatt.readCharacteristic(firstUuid)
 * //     val header = first[0].toInt()
 * //     val totalFrames = header and 0x0F
 * //     val frames = mutableListOf(first)
 * //     repeat(totalFrames - 1) {
 * //         frames.add(gatt.readCharacteristic(extUuid))
 * //     }
 * //     return frames.flatMap { it.drop(1) }.toByteArray()  // skip header byte
 * // }
 * //
 * // fun writeExtendedCharacteristic(uuid: UUID, data: ByteArray) {
 * //     val total = maxOf(1, (data.size + 18) / 19)
 * //     for (i in 0 until total) {
 * //         val chunk = data.sliceArray(i * 19 until minOf((i + 1) * 19, data.size))
 * //         val header = (((i + 1) shl 4) and 0xF0) or (total and 0x0F)
 * //         val frame = byteArrayOf(header.toByte()) + chunk
 * //         gatt.writeCharacteristic(uuid, frame)
 * //     }
 * // }
 */
