package com.xadras.app.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xadras.app.data.api.WsEvent
import com.xadras.app.data.local.TokenManager
import com.xadras.app.data.repository.GameRepository
import com.xadras.app.ml.ChessBoardDetector
import com.xadras.app.ml.FenConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado da interface do ecrã de câmara.
 */
data class CameraUiState(
    /** FEN atualmente detetado. */
    val currentFen: String = "",
    /** Nível de confiança da deteção (0–1). */
    val confidence: Float = 0f,
    /** Indica se a câmara está ativa e a processar frames. */
    val isStreaming: Boolean = false,
    /** Indica se a ligação WebSocket está ativa. */
    val isConnected: Boolean = false,
    /** Session ID do browser ao qual estamos ligados. */
    val sessionId: String? = null,
    /** Mensagem de estado para apresentar ao utilizador. */
    val statusMessage: String = "Aponte a câmara para o tabuleiro",
    /** Indica se estamos a transmitir FEN para o website. */
    val isBroadcasting: Boolean = false
)

/**
 * ViewModel para o ecrã de câmara.
 *
 * Fluxo principal:
 *   CameraX frame → ChessBoardDetector → FenConverter → REST/WS → Browser
 *
 * Utiliza o endpoint POST /api/game/live-board/fen/ para enviar FEN,
 * identificando a sessão do browser pelo session_id.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val detector: ChessBoardDetector,
    private val fenConverter: FenConverter,
    private val gameRepository: GameRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Limiar mínimo de confiança antes de enviar o FEN. */
    private val CONFIDENCE_THRESHOLD = 0.75f

    /** Intervalo mínimo (ms) entre inferências consecutivas. */
    private val INFERENCE_INTERVAL_MS = 500L

    private var inferenceJob: Job? = null
    private var lastSentFen: String = ""

    // ─── Inferência ──────────────────────────────────────────────────────────

    /**
     * Processar um frame da câmara.
     * Executa a deteção de peças e, se estiver em modo de transmissão,
     * envia o FEN para o backend.
     */
    fun processFrame(bitmap: Bitmap) {
        // Saltar se já está a processar um frame anterior
        if (inferenceJob?.isActive == true) return

        inferenceJob = viewModelScope.launch {
            delay(INFERENCE_INTERVAL_MS)

            val result = detector.detect(bitmap) ?: return@launch
            if (result.confidence < CONFIDENCE_THRESHOLD) return@launch
            if (!fenConverter.isValidPosition(result.board)) return@launch

            val fen = fenConverter.toFen(result.board)
            _uiState.update { it.copy(currentFen = fen, confidence = result.confidence) }

            // Enviar apenas se o FEN mudou e estamos em modo de transmissão
            if (fen != lastSentFen && _uiState.value.isBroadcasting) {
                transmitFen(fen)
                lastSentFen = fen
            }
        }
    }

    // ─── Controlo de transmissão ──────────────────────────────────────────────

    /**
     * Iniciar transmissão para o browser via session_id.
     * O utilizador introduz o código de sessão mostrado no browser.
     *
     * @param sessionId Código de sessão de 6 caracteres do browser.
     */
    fun startBroadcast(sessionId: String) {
        viewModelScope.launch {
            val token = tokenManager.accessToken.firstOrNull() ?: run {
                _uiState.update { it.copy(statusMessage = "Inicie sessão para transmitir") }
                return@launch
            }

            // Ligar ao WebSocket de live board
            gameRepository.connectToSession(sessionId, token)

            _uiState.update {
                it.copy(
                    isStreaming = true,
                    isBroadcasting = true,
                    sessionId = sessionId,
                    statusMessage = "A transmitir para sessão $sessionId..."
                )
            }

            observeWebSocket()
        }
    }

    /**
     * Modo local — visualizar o tabuleiro no telemóvel sem transmitir.
     */
    fun startLocalView() {
        _uiState.update {
            it.copy(
                isStreaming = true,
                isBroadcasting = false,
                statusMessage = "Modo local — tabuleiro no telemóvel"
            )
        }
    }

    /**
     * Parar a transmissão e desligar do WebSocket.
     */
    fun stopBroadcast() {
        gameRepository.disconnect()
        _uiState.update {
            it.copy(
                isStreaming = false,
                isBroadcasting = false,
                isConnected = false,
                sessionId = null,
                statusMessage = "Transmissão terminada"
            )
        }
    }

    // ─── Eventos do WebSocket ────────────────────────────────────────────────

    private fun observeWebSocket() {
        viewModelScope.launch {
            gameRepository.webSocketManager.events.collect { event ->
                when (event) {
                    is WsEvent.Connected ->
                        _uiState.update { it.copy(isConnected = true, statusMessage = "Ligado ✓") }
                    is WsEvent.Disconnected ->
                        _uiState.update { it.copy(isConnected = false, statusMessage = "Desligado") }
                    is WsEvent.Error ->
                        _uiState.update { it.copy(isConnected = false, statusMessage = "Erro: ${event.throwable.message}") }
                    else -> Unit
                }
            }
        }
    }

    // ─── Transmissão de FEN ──────────────────────────────────────────────────

    /**
     * Enviar o FEN para o backend.
     * Utiliza REST como método principal (mais fiável).
     * O backend retransmite para o browser via channel layer.
     */
    private fun transmitFen(fen: String) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            gameRepository.sendFenRest(fen, sessionId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameRepository.disconnect()
    }
}