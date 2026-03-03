package com.ypsopump.sdk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Cliente para el servidor REST de Play Integrity token.
 * Llama a POST /api/v1/integrity-token con el nonce del servidor Ypsomed.
 */
class IntegrityTokenClient(
    private val serverUrl: String,  // e.g. "https://my-server.com"
    private val apiKey: String
) {

    /**
     * Solicita un Play Integrity token al servidor.
     *
     * @param nonceHex Server nonce en hex (24 bytes del gRPC Ypsomed)
     * @return Play Integrity token (JWT string)
     * @throws IntegrityTokenException si falla
     */
    suspend fun getToken(nonceHex: String): String = withContext(Dispatchers.IO) {
        val url = URL("$serverUrl/api/v1/integrity-token")
        val conn = url.openConnection() as HttpURLConnection

        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 120_000  // Frida puede tardar

            val body = JSONObject().put("nonce", nonceHex).toString()
            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw IntegrityTokenException("Server returned $code: $error")
            }

            val json = JSONObject(responseBody)
            json.getString("token")
        } finally {
            conn.disconnect()
        }
    }
}

class IntegrityTokenException(message: String) : Exception(message)
