package com.hallowed.randomthing

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var tokenInput: EditText
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var listContainer: LinearLayout
    private var gateway: DiscordGateway? = null
    private val guilds = linkedMapOf<String, DiscordGuild>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
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
            textSize = 14f
            setPadding(0, 8, 0, 12)
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply { addView(listContainer) }

        root.addView(title)
        root.addView(tokenInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 })
        root.addView(connectButton)
        root.addView(statusText)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun connectBot() {
        val token = tokenInput.text.toString().trim()
        if (token.isBlank()) {
            statusText.text = "Digite o token do bot."
            return
        }
        gateway?.close()
        guilds.clear()
        listContainer.removeAllViews()
        connectButton.isEnabled = false
        statusText.text = "Conectando ao Discord..."

        gateway = DiscordGateway(
            token = token,
            onStatus = { message ->
                runOnUiThread {
                    statusText.text = message
                    if (message.startsWith("Conectado")) connectButton.isEnabled = true
                }
            },
            onGuild = { guild ->
                runOnUiThread {
                    guilds[guild.id] = guild
                    showGuilds()
                }
            },
            onGuildRemoved = { guildId ->
                runOnUiThread {
                    guilds.remove(guildId)
                    showGuilds()
                }
            },
            onFailure = { message ->
                runOnUiThread {
                    statusText.text = message
                    connectButton.isEnabled = true
                }
            }
        ).also { it.connect() }
    }

    private fun showGuilds() {
        listContainer.removeAllViews()
        addSectionTitle("Servidores (${guilds.size})")
        if (guilds.isEmpty()) {
            addText("Aguardando a lista de servidores do bot...")
            return
        }
        guilds.values.sortedBy { it.name.lowercase() }.forEach { guild ->
            listContainer.addView(Button(this).apply {
                text = guild.name
                setOnClickListener { showChannels(guild) }
            })
        }
    }

    private fun showChannels(guild: DiscordGuild) {
        listContainer.removeAllViews()
        addText("Servidor: ${guild.name}")
        listContainer.addView(Button(this).apply {
            text = "Voltar aos servidores"
            setOnClickListener { showGuilds() }
        })
        val channels = guild.channels
            .filter { it.type == 0 || it.type == 5 }
            .sortedBy { it.name.lowercase() }
        addSectionTitle("Canais (${channels.size})")
        if (channels.isEmpty()) {
            addText("Nenhum canal de texto disponível para este bot.")
            return
        }
        channels.forEach { channel ->
            listContainer.addView(Button(this).apply {
                text = "# ${channel.name}"
                setOnClickListener {
                    listContainer.removeAllViews()
                    addText("Servidor: ${guild.name}")
                    addText("Canal: #${channel.name}")
                    addText("Canal selecionado. A próxima etapa será carregar as mensagens e habilitar o envio.")
                    listContainer.addView(Button(this@MainActivity).apply {
                        text = "Voltar aos canais"
                        setOnClickListener { showChannels(guild) }
                    })
                }
            })
        }
    }

    private fun addSectionTitle(value: String) {
        listContainer.addView(TextView(this).apply {
            text = value
            textSize = 18f
            setPadding(0, 12, 0, 8)
        })
    }

    private fun addText(value: String) {
        listContainer.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setPadding(0, 8, 0, 8)
        })
    }

    override fun onDestroy() {
        gateway?.close()
        gateway = null
        super.onDestroy()
    }
}
