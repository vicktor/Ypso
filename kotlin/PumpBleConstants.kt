package com.ypsopump.sdk

import java.util.UUID

/**
 * UUIDs de características BLE del YpsoPump.
 * Referencia: pump/constants.py
 */
object PumpBleConstants {

    // --- Servicio de autenticación y cripto ---
    const val SERVICE_CRYPTO = "fb349b5f-8000-0080-0010-0000feda0002"

    // --- Autenticación ---
    val CHAR_AUTH_PASSWORD: UUID = uuid("669a0c20-0008-969e-e211-fcbeb2147bc5")

    // --- Versiones ---
    val CHAR_MASTER_VERSION: UUID = uuid("669a0c20-0008-969e-e211-fcbeb0147bc5")
    val CHAR_BASE_VERSION: UUID = uuid("669a0c20-0008-969e-e211-fcbee23b7bc5")
    val CHAR_SETTINGS_VERSION: UUID = uuid("669a0c20-0008-969e-e211-fcbee33b7bc5")
    val CHAR_HISTORY_VERSION: UUID = uuid("669a0c20-0008-969e-e211-fcbee43b7bc5")

    // --- Sistema ---
    val CHAR_SYSTEM_DATE: UUID = uuid("669a0c20-0008-969e-e211-fcbedc3b7bc5")
    val CHAR_SYSTEM_TIME: UUID = uuid("669a0c20-0008-969e-e211-fcbedd3b7bc5")
    val CHAR_EXTENDED_READ: UUID = uuid("669a0c20-0008-969e-e211-fcff000000ff")

    // --- Pairing (key exchange) ---
    val CHAR_REQUEST_PUBLIC_KEY: UUID = uuid("669a0c20-0008-969e-e211-fcff0000000a")
    val CHAR_WRITE_CHALLENGE: UUID = uuid("669a0c20-0008-969e-e211-fcff0000000b")

    // --- Bolus ---
    val CHAR_BOLUS_START_STOP: UUID = uuid("669a0c20-0008-969e-e211-fcbee18b7bc5")
    val CHAR_BOLUS_STATUS: UUID = uuid("669a0c20-0008-969e-e211-fcbee28b7bc5")
    val CHAR_SYSTEM_STATUS: UUID = uuid("669a0c20-0008-969e-e211-fcbee48b7bc5")
    val CHAR_BOLUS_NOTIFICATION: UUID = uuid("669a0c20-0008-969e-e211-fcbee58b7bc5")

    // --- Basal ---
    val CHAR_BASAL_INDEX: UUID = uuid("669a0c20-0008-969e-e211-fcbeb3147bc5")
    val CHAR_BASAL_VALUE: UUID = uuid("669a0c20-0008-969e-e211-fcbeb4147bc5")

    // --- History: Events ---
    val CHAR_EVENTS_COUNT: UUID = uuid("669a0c20-0008-969e-e211-fcbecb3b7bc5")
    val CHAR_EVENTS_INDEX: UUID = uuid("669a0c20-0008-969e-e211-fcbecc3b7bc5")
    val CHAR_EVENTS_VALUE: UUID = uuid("669a0c20-0008-969e-e211-fcbecd3b7bc5")

    // --- History: Alerts ---
    val CHAR_ALERTS_COUNT: UUID = uuid("669a0c20-0008-969e-e211-fcbec83b7bc5")
    val CHAR_ALERTS_INDEX: UUID = uuid("669a0c20-0008-969e-e211-fcbec93b7bc5")
    val CHAR_ALERTS_VALUE: UUID = uuid("669a0c20-0008-969e-e211-fcbeca3b7bc5")

    // --- History: System ---
    val CHAR_SYSTEM_HISTORY_COUNT: UUID = uuid("86a5a431-d442-2c8d-304b-19ee355571fc")
    val CHAR_SYSTEM_HISTORY_INDEX: UUID = uuid("381ddce9-e934-b4ae-e345-eb87283db426")
    val CHAR_SYSTEM_HISTORY_VALUE: UUID = uuid("ae3022af-2ec8-bf88-e64c-da68c9a3891a")

    // --- BLE Frame Protocol ---
    /** Max data per BLE frame (1 byte header + 19 bytes data) */
    const val FRAME_DATA_SIZE = 19
    const val FRAME_MAX_SIZE = 20

    private fun uuid(s: String): UUID = UUID.fromString(s)
}
