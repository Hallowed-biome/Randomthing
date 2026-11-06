package com.hallowed.randomthing

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var listContainer: LinearLayout
    private var gateway: DiscordGateway? = null
    private var api: DiscordApi? = null
    private val io = Executors.newSingleThreadExecutor()
    private val guilds = linkedMapOf<String, DiscordGuild>()
    private var selectedGuild: DiscordGuild? = null
    private var selectedChannel: DiscordChannel? = null
    private var messageInput: EditText? = null
    private var messageList: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
        }
        val title = TextView(this).apply { text = "Discord Bot Manager"; textSize = 22f }
        tokenInput = EditText(this).apply {
            hint = "Bot Token"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        connectButton = Button(this).apply { text = "Conectar"; setOnClickListener { connectBot() } }
        statusText = TextView(this).apply {
            text = "Insira o token do bot para conectar."
            textSize = 14f
            setPadding(0, 8, 0, 12)
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(listContainer) }
        root.addView(title)
        root.addView(tokenInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 })
        root.addView(connectButton)
        root.addView(statusText)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun connectBot() {
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) { statusText.text = "Digite o token do bot."; return }
        gateway?.close()
        guilds.clear()
        selectedGuild = null
        selectedChannel = null
        api = DiscordApi(token)
        listContainer.removeAllViews()
        connectButton.isEnabled = false
        statusText.text = "Conectando ao Discord..."
        gateway = DiscordGateway(
            token = token,
            onStatus = { message -> runOnUiThread {
                statusText.text = message
                if (message.startsWith("Conectado")) connectButton.isEnabled = true
            } },
            onGuild = { guild -> runOnUiThread { guilds[guild.id] = guild; if (selectedGuild == null) showGuilds() } },
            onGuildRemoved = { id -> runOnUiThread { guilds.remove(id); if (selectedGuild == null) showGuilds() } },
            onFailure = { message -> runOnUiThread { statusText.text = message; connectButton.isEnabled = true } },
            onMessage = { message -> runOnUiThread {
                if (selectedChannel?.id == message.channelId) addMessage(message)
            } }
        ).also { it.connect() }
    }

    private fun showGuilds() {
        selectedGuild = null
        selectedChannel = null
        listContainer.removeAllViews()
        addSectionTitle("Servidores (${guilds.size})")
        if (guilds.isEmpty()) { addText("Aguardando a lista de servidores do bot..."); return }
        guilds.values.sortedBy { it.name.lowercase() }.forEach { guild ->
            listContainer.addView(Button(this).apply { text = guild.name; setOnClickListener { showChannels(guild) } })
        }
    }

    private fun showChannels(guild: DiscordGuild) {
        selectedGuild = guild
        selectedChannel = null
        listContainer.removeAllViews()
        addText("Servidor: ${guild.name}")
        listContainer.addView(Button(this).apply { text = "Voltar aos servidores"; setOnClickListener { showGuilds() } })
        val channels = guild.channels.filter { it.type == 0 || it.type == 5 }.sortedBy { it.name.lowercase() }
        addSectionTitle("Canais (${channels.size})")
        if (channels.isEmpty()) { addText("Nenhum canal de texto disponível para este bot."); return }
        channels.forEach { channel ->
            listContainer.addView(Button(this).apply {
                text = "# ${channel.name}"
                setOnClickListener { showMessages(guild, channel) }
            })
        }
    }

    private fun showMessages(guild: DiscordGuild, channel: DiscordChannel) {
        selectedGuild = guild
        selectedChannel = channel
        listContainer.removeAllViews()
        addText("Servidor: ${guild.name}  •  #${channel.name}")
        listContainer.addView(Button(this).apply { text = "Voltar aos canais"; setOnClickListener { showChannels(guild) } })
        messageList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listContainer.addView(messageList)
        addText("Carregando as últimas 50 mensagens...")
        messageInput = EditText(this).apply { hint = "Escreva uma mensagem"; minLines = 1; maxLines = 4 }
        listContainer.addView(messageInput)
        listContainer.addView(Button(this).apply {
            text = "Enviar mensagem"
            setOnClickListener { sendCurrentMessage(channel) }
        })
        io.execute {
            try {
                val messages = api?.getMessages(channel.id).orEmpty()
                runOnUiThread {
                    if (selectedChannel?.id == channel.id) {
                        messageList?.removeAllViews()
                        if (messages.isEmpty()) addMessage(DiscordMessage("", "Discord", "Nenhuma mensagem encontrada.", ""))
                        else messages.forEach { addMessage(it) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { if (selectedChannel?.id == channel.id) addText("Não foi possível carregar: ${e.message}") }
            }
        }
    }

    private fun sendCurrentMessage(channel: DiscordChannel) {
        val content = messageInput?.text?.toString()?.trim().orEmpty()
        if (content.isBlank()) { statusText.text = "Digite uma mensagem antes de enviar."; return }
        if (content.length > 2000) { statusText.text = "A mensagem deve ter no máximo 2000 caracteres."; return }
        messageInput?.isEnabled = false
        io.execute {
            try {
                api?.sendMessage(channel.id, content)
                runOnUiThread {
                    if (selectedChannel?.id == channel.id) {
                        messageInput?.setText("")
                        statusText.text = "Mensagem enviada!"
                    }
                    messageInput?.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread { statusText.text = e.message ?: "Falha ao enviar."; messageInput?.isEnabled = true }
            }
        }
    }

    private fun addMessage(message: DiscordMessage) {
        val target = messageList ?: return
        target.addView(TextView(this).apply {
            text = "${message.author}: ${message.content}"
            textSize = 14f
            setPadding(0, 6, 0, 6)
        })
    }

    private fun addSectionTitle(value: String) {
        listContainer.addView(TextView(this).apply { text = value; textSize = 18f; setPadding(0, 12, 0, 8) })
    }

    private fun addText(value: String) {
        listContainer.addView(TextView(this).apply { text = value; textSize = 14f; setPadding(0, 8, 0, 8) })
    }

    override fun onDestroy() {
        gateway?.close()
        io.shutdownNow()
        super.onDestroy()
    }
}
