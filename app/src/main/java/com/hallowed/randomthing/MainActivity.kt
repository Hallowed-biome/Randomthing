package com.hallowed.randomthing

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Discord Bot Manager"
            textSize = 22f
        }

        tokenInput = EditText(this).apply {
            hint = "Bot Token"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        connectButton = Button(this).apply {
            text = "Conectar"
            setOnClickListener { connectBot() }
        }

        statusText = TextView(this).apply {
            text = "Insira o token do bot para conectar."
            textSize = 15f
            setPadding(0, 20, 0, 0)
        }

        root.addView(title)
        root.addView(tokenInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })
        root.addView(connectButton)
        root.addView(statusText)
        setContentView(root)
    }

    private fun connectBot() {
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) {
            statusText.text = "Digite o token do bot."
            return
        }

        connectButton.isEnabled = false
        statusText.text = "Conectando..."

        executor.execute {
            val result = validateBotToken(token)
            runOnUiThread {
                connectButton.isEnabled = true
                statusText.text = result
            }
        }
    }

    private fun validateBotToken(token: String): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://discord.com/api/v10/users/@me")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bot $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                val bot = JSONObject(body)
                val username = bot.optString("username", "bot")
                "Conectado como $username. Próxima etapa: listar servidores e canais."
            } else if (responseCode == 401) {
                "Token inválido. Confira o token do bot."
            } else {
                "Falha na conexão (HTTP $responseCode)."
            }
        } catch (_: Exception) {
            "Não foi possível conectar. Verifique sua internet e tente novamente."
        } finally {
            connection?.disconnect()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
