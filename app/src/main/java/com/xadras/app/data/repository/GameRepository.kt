package com.xadras.app.data.repository

import com.xadras.app.data.api.ApiService
import com.xadras.app.data.api.WebSocketManager
import com.xadras.app.data.model.FenRequest
import com.xadras.app.utils.Resource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de jogo.
 *
 * Gere o envio de FEN para o backend (REST e WebSocket)
 * e a ligação em tempo real via WebSocket.
 */
@Singleton
class GameRepository @Inject constructor(
    private val api: ApiService,
    val webSocketManager: WebSocketManager
) {

    /**
     * Enviar FEN via REST (modo sessão — telemóvel → backend → browser).
     *
     * @param fen       String FEN da posição detetada.
     * @param sessionId Identificador da sessão do browser.
     */
    suspend fun sendFenRest(fen: String, sessionId: String): Resource<Unit> {
        return try {
            val response = api.sendFen(FenRequest(fen, sessionId))
            if (response.isSuccessful) Resource.Success(Unit)
            else Resource.Error("Erro ao enviar FEN: ${response.code()}")
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    /**
     * Enviar FEN via WebSocket (durante transmissão em direto).
     */
    fun sendFenWs(fen: String, sessionId: String) {
        webSocketManager.sendFen(fen, sessionId)
    }

    /**
     * Ligar ao WebSocket de Live Board com um session_id.
     */
    fun connectToSession(sessionId: String, token: String) {
        webSocketManager.connectToLiveBoard(sessionId, token)
    }

    /**
     * Ligar ao WebSocket de um jogo específico.
     */
    fun connectToGame(gameId: Int, token: String) {
        webSocketManager.connectToGame(gameId, token)
    }

    /**
     * Desligar do WebSocket ativo.
     */
    fun disconnect() {
        webSocketManager.disconnect()
    }
}