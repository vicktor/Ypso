package com.ypsopump.app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

@SuppressLint("MissingPermission")
class PumpBleManager(private val context: Context) {

    private val adapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var gatt: BluetoothGatt? = null
    private val writeMutex = Mutex()

    // Callback continuations
    private var connectCont: CancellableContinuation<Boolean>? = null
    private var readCont: CancellableContinuation<ByteArray>? = null
    private var writeCont: CancellableContinuation<Boolean>? = null
    private var discoverCont: CancellableContinuation<Boolean>? = null

    var onLog: ((String) -> Unit)? = null

    // --- Scan ---

    data class ScanResultData(val name: String, val address: String, val serial: String)

    suspend fun scanForPump(timeoutMs: Long = 10_000): ScanResultData? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val scanner = adapter.bluetoothLeScanner ?: run {
                    cont.resume(null) {}
                    return@suspendCancellableCoroutine
                }

                val filter = ScanFilter.Builder().setDeviceName(null).build()
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()

                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult) {
                        val name = result.device.name ?: return
                        if (name.startsWith("YpsoPump_")) {
                            scanner.stopScan(this)
                            val serial = name.removePrefix("YpsoPump_")
                            onLog?.invoke("Found: $name (${result.device.address})")
                            if (cont.isActive) {
                                cont.resume(ScanResultData(name, result.device.address, serial)) {}
                            }
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        onLog?.invoke("Scan failed: $errorCode")
                        if (cont.isActive) cont.resume(null) {}
                    }
                }

                onLog?.invoke("Scanning for YpsoPump...")
                scanner.startScan(listOf(filter), settings, callback)

                // Timeout
                val job = CoroutineScope(Dispatchers.Main).launch {
                    delay(timeoutMs)
                    scanner.stopScan(callback)
                    if (cont.isActive) {
                        onLog?.invoke("Scan timeout")
                        cont.resume(null) {}
                    }
                }

                cont.invokeOnCancellation {
                    scanner.stopScan(callback)
                    job.cancel()
                }
            }
        }

    // --- Connect ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onLog?.invoke("Connected, discovering services...")
                g.discoverServices()
            } else {
                onLog?.invoke("Disconnected (status=$status)")
                connectCont?.let { if (it.isActive) it.resume(false) {} }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                onLog?.invoke("Services discovered (${g.services.size} services)")
                discoverCont?.let { if (it.isActive) it.resume(true) {} }
                connectCont?.let { if (it.isActive) it.resume(true) {} }
            } else {
                connectCont?.let { if (it.isActive) it.resume(false) {} }
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCont?.let { if (it.isActive) it.resume(value) {} }
            } else {
                readCont?.let { if (it.isActive) it.resume(byteArrayOf()) {} }
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeCont?.let { if (it.isActive) it.resume(status == BluetoothGatt.GATT_SUCCESS) {} }
        }
    }

    suspend fun connect(address: String): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            connectCont = cont
            val device = adapter.getRemoteDevice(address)
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            cont.invokeOnCancellation { disconnect() }
        }
    }

    // --- Read / Write ---

    private fun findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        gatt?.services?.forEach { service ->
            service.getCharacteristic(uuid)?.let { return it }
        }
        return null
    }

    suspend fun readCharacteristic(uuid: UUID): ByteArray = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            readCont = cont
            val char = findCharacteristic(uuid)
            if (char == null) {
                onLog?.invoke("Characteristic not found: $uuid")
                cont.resume(byteArrayOf()) {}
                return@suspendCancellableCoroutine
            }
            gatt?.readCharacteristic(char)
        }
    }

    suspend fun writeCharacteristic(uuid: UUID, value: ByteArray): Boolean =
        writeMutex.withLock {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    writeCont = cont
                    val char = findCharacteristic(uuid)
                    if (char == null) {
                        onLog?.invoke("Characteristic not found: $uuid")
                        cont.resume(false) {}
                        return@suspendCancellableCoroutine
                    }
                    gatt?.writeCharacteristic(
                        char, value,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                }
            }
        }

    // --- Extended Read (multi-frame protocol) ---

    suspend fun readExtended(firstUuid: UUID, extUuid: UUID = PumpBleConstants.CHAR_EXTENDED_READ): ByteArray {
        val first = readCharacteristic(firstUuid)
        if (first.isEmpty()) return byteArrayOf()
        val header = first[0].toInt() and 0xFF
        val totalFrames = (header and 0x0F).let { if (it == 0) 1 else it }
        val frames = mutableListOf(first)
        repeat(totalFrames - 1) {
            frames.add(readCharacteristic(extUuid))
        }
        // Merge: skip header byte from each frame
        return frames.flatMap { it.drop(1) }.toByteArray()
    }

    // --- Extended Write (multi-frame protocol) ---

    suspend fun writeExtended(uuid: UUID, data: ByteArray) {
        val frames = PumpCrypto.chunkPayload(data)
        for (frame in frames) {
            writeCharacteristic(uuid, frame)
            delay(50)
        }
    }

    // --- Disconnect ---

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }
}
