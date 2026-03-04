package com.ypsopump.app

import java.util.UUID

object PumpBleConstants {
    val CHAR_AUTH_PASSWORD: UUID = uuid("669a0c20-0008-969e-e211-fcbeb2147bc5")
    val CHAR_MASTER_VERSION: UUID = uuid("669a0c20-0008-969e-e211-fcbeb0147bc5")
    val CHAR_EXTENDED_READ: UUID = uuid("669a0c20-0008-969e-e211-fcff000000ff")
    val CHAR_REQUEST_PUBLIC_KEY: UUID = uuid("669a0c20-0008-969e-e211-fcff0000000a")
    val CHAR_WRITE_CHALLENGE: UUID = uuid("669a0c20-0008-969e-e211-fcff0000000b")
    val CHAR_BOLUS_START_STOP: UUID = uuid("669a0c20-0008-969e-e211-fcbee18b7bc5")
    val CHAR_BOLUS_STATUS: UUID = uuid("669a0c20-0008-969e-e211-fcbee28b7bc5")
    val CHAR_SYSTEM_STATUS: UUID = uuid("669a0c20-0008-969e-e211-fcbee48b7bc5")

    private fun uuid(s: String): UUID = UUID.fromString(s)
}
