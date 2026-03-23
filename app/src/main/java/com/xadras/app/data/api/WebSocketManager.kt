package com.xadras.app.data.api

import android.util.Log
import com.xadras.app.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// ─── Eventos emitidos pelo WebSocket ─────────────────────────────────────────

sealed class WsEvent {
    /** Ligação estabelecida com sucesso. */
    object Connected : WsEvent()
    /** Mensagem recebida do servidor. */
    data class MessageReceived(val text: String) : WsEvent()
    /** Erro na ligação. */
    data class Error(val throwable: Throwable) : WsEvent()
    /** Ligação terminada. */
    object Disconnected : WsEvent()
}

// ─── Gestor de WebSocket ─────────────────────────────────────────────────────

/**
 * Gestor de ligações WebSocket com o backend Django.
 *
 * Suporta dois modos:
 * 1. Jogo em tempo real: ws://<host>/ws/game/<gameId>/
 * 2. Live Board (sessão): ws://<host>/ws/live-board/?session=<sessionId>
 */
@Singleton
class WebSocketManager @Inject constructor(private val client: OkHttpClient) {

    private var webSocket: WebSocket? = null
    private val _events = Channel<WsEvent>(Channel.BUFFERED)

    /** Fluxo de eventos do WebSocket para observação pelo ViewModel. */
    val events: Flow<WsEvent> = _events.receiveAsFlow()

    /**
     * Ligar ao WebSocket de um jogo específico.
     * Utilizado para jogos online entre dois jogadores.
     */
    fun connectToGame(gameId: Int, token: String) {
        val url = "${BuildConfig.WS_BASE_URL}game/$gameId/?token=$token"
        connect(url, "game #$gameId")
    }

    /**
     * Ligar ao WebSocket de Live Board com um session_id.
     * Utilizado quando a app do telemóvel transmite FEN para o browser.
     */
    fun connectToLiveBoard(sessionId: String, token: String) {
        val url = "${BuildConfig.WS_BASE_URL}live-board/?session=$sessionId&token=$token"
        connect(url, "sessão $sessionId")
    }

    /**
     * Enviar FEN através do WebSocket ativo.
     * O consumer Django deve tratar o tipo "fen_update".
     */
    fun sendFen(fen: String, sessionId: String) {
        val payload = JSONObject().apply {
            put("type", "fen_update")
            put("fen", fen)
            put("session_id", sessionId)
        }.toString()
        webSocket?.send(payload)
        Log.d("WebSocket", "FEN enviado: $fen")
    }

    /** Verifica se existe uma ligação WebSocket ativa. */
    fun isConnected(): Boolean = webSocket != null

    /** Desligar a ligação WebSocket ativa. */
    fun disconnect() {
        webSocket?.close(1000, "Utilizador desligou")
        webSocket = null
    }

    // ─── Internos ────────────────────────────────────────────────────────────

    private fun connect(url: String, label: String) {
        // Fechar ligação anterior se existir
        webSocket?.close(1000, "Nova ligação")

        Log.d("WebSocket", "A ligar a $url ($label)")

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                _events.trySend(WsEvent.Connected)
                Log.d("WebSocket", "Ligado a $label")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                _events.trySend(WsEvent.MessageReceived(text))
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _events.trySend(WsEvent.Error(t))
                Log.e("WebSocket", "Erro ($label): ${t.message}")
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _events.trySend(WsEvent.Disconnected)
                Log.d("WebSocket", "Desligado ($label): $reason")
            }
        })
    }
}