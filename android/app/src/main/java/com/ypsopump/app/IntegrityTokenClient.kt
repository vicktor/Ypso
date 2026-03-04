package com.ypsopump.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class IntegrityTokenClient(
    private val serverUrl: String,
    private val apiKey: String
) {
    suspend fun getToken(nonceHex: String): String = withContext(Dispatchers.IO) {
        val url = URL("$serverUrl/api/v1/integrity-token")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Key", apiKey)
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 120_000

            OutputStreamWriter(conn.outputStream).use {
                it.write(JSONObject().put("nonce", nonceHex).toString())
            }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown"
                throw RuntimeException("Server ${conn.responseCode}: $err")
            }

            JSONObject(conn.inputStream.bufferedReader().readText()).getString("token")
        } finally {
            conn.disconnect()
        }
    }
}
