package com.arsal.ragmobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Emulator -> Windows host localhost. Change to your PC LAN IP for a physical phone.
private const val BASE_URL = "http://10.0.2.2:8000/"

 data class ChatMessage(
    val role: String,
    val content: String,
)

data class SourceItem(
    val source: String,
    val chunkId: Int,
    val score: Double,
    val text: String,
)

data class ChatResult(
    val answer: String,
    val grounded: Boolean,
    val threshold: Double,
    val bestScore: Double,
    val sources: List<SourceItem>,
)

class RagApi {
    suspend fun chat(message: String, history: List<ChatMessage>): ChatResult = withContext(Dispatchers.IO) {
        val connection = (URL(BASE_URL + "chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val body = JSONObject().apply {
                put("message", message)
                put("history", JSONArray().apply {
                    history.takeLast(6).forEach { item ->
                        put(JSONObject().apply {
                            put("role", item.role)
                            put("content", item.content)
                        })
                    }
                })
            }

            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            if (status !in 200..299) {
                throw IllegalStateException("Backend error $status: $responseText")
            }

            val json = JSONObject(responseText)
            val sourcesJson = json.optJSONArray("sources") ?: JSONArray()
            val sources = buildList {
                for (i in 0 until sourcesJson.length()) {
                    val item = sourcesJson.getJSONObject(i)
                    add(
                        SourceItem(
                            source = item.getString("source"),
                            chunkId = item.getInt("chunk_id"),
                            score = item.getDouble("score"),
                            text = item.getString("text"),
                        )
                    )
                }
            }

            ChatResult(
                answer = json.getString("answer"),
                grounded = json.getBoolean("grounded"),
                threshold = json.getDouble("threshold"),
                bestScore = json.getDouble("best_score"),
                sources = sources,
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL(BASE_URL + "health").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        try {
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }
}
