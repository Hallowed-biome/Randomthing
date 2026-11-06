package com.hallowed.randomthing

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DiscordApi(private val token: String) {
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    fun getMessages(channelId: String): List<DiscordMessage> {
        val request = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId/messages?limit=50")
            .header("Authorization", "Bot $token")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorText(response.code, body))
            val array = JSONArray(body)
            return buildList {
                for (i in array.length() - 1 downTo 0) {
                    val item = array.optJSONObject(i) ?: continue
                    val author = item.optJSONObject("author")
                    add(DiscordMessage(
                        id = item.optString("id"),
                        author = author?.optString("global_name")?.takeIf { it.isNotBlank() }
                            ?: author?.optString("username", "Desconhecido") ?: "Desconhecido",
                        content = item.optString("content").ifBlank { "[Mensagem sem texto]" },
                        timestamp = item.optString("timestamp")
                    ))
                }
            }
        }
    }

    fun sendMessage(channelId: String, content: String) {
        val body = JSONObject().put("content", content).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://discord.com/api/v10/channels/$channelId/messages")
            .header("Authorization", "Bot $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(errorText(response.code, responseBody))
        }
    }

    private fun errorText(code: Int, body: String): String {
        val detail = try { JSONObject(body).optString("message") } catch (_: Exception) { "" }
        return when (code) {
            401 -> "Token inválido ou revogado."
            403 -> "O bot não tem permissão para ler ou enviar mensagens neste canal."
            404 -> "Canal não encontrado ou inacessível."
            else -> "Erro do Discord ($code): $detail"
        }
    }
}
