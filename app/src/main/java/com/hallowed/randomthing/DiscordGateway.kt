package com.hallowed.randomthing

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DiscordChannel(val id: String, val name: String, val type: Int)
data class DiscordGuild(val id: String, val name: String, val channels: List<DiscordChannel>)
data class DiscordMessage(val id: String, val author: String, val content: String, val timestamp: String)
data class DiscordMessage(val id: String, val author: String, val content: String, val timestamp: String)

class DiscordGateway(
    private val token: String,
    private val onStatus: (String) -> Unit,
    private val onGuild: (DiscordGuild) -> Unit,
    private val onGuildRemoved: (String) -> Unit,
    private val onFailure: (String) -> Unit,
    private val onMessage: (DiscordMessage) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null
    private val closed = AtomicBoolean(false)
    @Volatile private var sequence: Long? = null

    fun connect() {
        val request = Request.Builder()
            .url("wss://gateway.discord.gg/?v=10&encoding=json")
            .build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus("Conexão aberta. Aguardando o Discord...")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handlePayload(webSocket, JSONObject(text))
                } catch (_: Exception) {
                    onFailure("Não foi possível interpretar uma resposta do Discord.")
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!closed.get()) onFailure("Falha na conexão. Verifique a internet e o token.")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) onFailure("Conexão encerrada pelo Discord.")
            }
        })
    }

    private fun handlePayload(webSocket: WebSocket, payload: JSONObject) {
        val op = payload.optInt("op")
        if (payload.has("s") && !payload.isNull("s")) sequence = payload.optLong("s")
        when (op) {
            10 -> {
                val interval = payload.getJSONObject("d").getLong("heartbeat_interval")
                identify(webSocket)
                startHeartbeat(webSocket, interval)
            }
            1 -> sendHeartbeat(webSocket)
            11 -> { /* Heartbeat acknowledged. */ }
        }
        if (op != 0) return
        when (payload.optString("t")) {
            "READY" -> onStatus("Conectado! Carregando servidores...")
            "GUILD_CREATE" -> onGuild(parseGuild(payload.getJSONObject("d")))
            "MESSAGE_CREATE" -> {
                val data = payload.getJSONObject("d")
                val author = data.optJSONObject("author")
                onMessage(DiscordMessage(
                    id = data.optString("id"),
                    author = author?.optString("global_name")?.takeIf { !it.isNullOrBlank() }
                        ?: author?.optString("username", "Desconhecido") ?: "Desconhecido",
                    content = data.optString("content"),
                    timestamp = data.optString("timestamp")
                ))
            }
            "MESSAGE_CREATE" -> {
                val data = payload.getJSONObject("d")
                val author = data.optJSONObject("author")
                onMessage(DiscordMessage(
                    id = data.optString("id"),
                    author = author?.optString("global_name")?.takeIf { !it.isNullOrBlank() }
                        ?: author?.optString("username", "Desconhecido") ?: "Desconhecido",
                    content = data.optString("content"),
                    timestamp = data.optString("timestamp")
                ))
            }
            "GUILD_DELETE" -> {
                val data = payload.getJSONObject("d")
                if (!data.optBoolean("unavailable", false)) onGuildRemoved(data.optString("id"))
            }
        }
    }

    private fun identify(webSocket: WebSocket) {
        val data = JSONObject().apply {
            put("token", token)
            put("intents", 33793)
            put("properties", JSONObject().apply {
                put("os", "android")
                put("browser", "DiscordBotManager")
                put("device", "Android")
            })
        }
        webSocket.send(JSONObject().apply {
            put("op", 2)
            put("d", data)
        }.toString())
    }

    private fun startHeartbeat(webSocket: WebSocket, interval: Long) {
        Thread {
            try {
                while (!closed.get()) {
                    Thread.sleep(interval)
                    if (!closed.get()) sendHeartbeat(webSocket)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }.apply {
            name = "DiscordGatewayHeartbeat"
            isDaemon = true
            start()
        }
    }

    private fun sendHeartbeat(webSocket: WebSocket) {
        webSocket.send(JSONObject().apply {
            put("op", 1)
            put("d", sequence ?: JSONObject.NULL)
        }.toString())
    }

    private fun parseGuild(data: JSONObject): DiscordGuild {
        val channelArray = data.optJSONArray("channels") ?: JSONArray()
        val channels = buildList {
            for (index in 0 until channelArray.length()) {
                val channel = channelArray.optJSONObject(index) ?: continue
                add(DiscordChannel(
                    id = channel.optString("id"),
                    name = channel.optString("name", "canal"),
                    type = channel.optInt("type", -1)
                ))
            }
        }
        return DiscordGuild(
            id = data.optString("id"),
            name = data.optString("name", "Servidor"),
            channels = channels
        )
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            socket?.close(1000, "App encerrado")
            socket = null
            client.dispatcher.executorService.shutdown()
        }
    }
}
