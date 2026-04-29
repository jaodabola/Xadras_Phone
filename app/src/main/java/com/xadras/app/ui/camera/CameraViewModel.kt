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
    val isBroadcasting: Boolean = false,
    /** Indica se o tabuleiro foi detetado no último frame. */
    val boardDetected: Boolean = false,
    /** Número de quadrados extraídos com sucesso. */
    val squaresDetected: Int = 0,
    /** Indica se o modelo YOLO está carregado e pronto. */
    val modelReady: Boolean = false,
    /** Mensagem de carregamento do modelo. */
    val loadingMessage: String = "A carregar o modelo de deteção",
    /** Texto do estado do tracker (jogada N / turno). */
    val trackerStatus: String = "",
    /** Indica se o tracker está calibrado e a rastrear. */
    val isTracking: Boolean = false,
    /** Sinaliza as coordenadas do tabuleiro originais se existirem [x1, y1, x2, y2, x3, y3, x4, y4] */
    val boardCorners: FloatArray? = null,
    /** Largura interna da frame analisada (para calculo de AR) */
    val imageWidth: Int = 0,
    /** Altura interna da frame analisada (para calculo de AR) */
    val imageHeight: Int = 0
)

/**
 * ViewModel para o ecrã de câmara.
 *
 * Pipeline:
 *   1. Modelo YOLO carrega (loading screen visível)
 *   2. CameraX frame → ChessBoardDetector (YOLO segmentação)
 *   3. 64 quadrados → FenConverter (FenTracker internamente)
 *   4. Auto-calibração de threshold no primeiro frame
 *   5. Deteção diferencial de movimentos + validação chesslib
 *   6. FEN atualizado → REST/WS → Browser
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

    /**
     * Delay artificial entre inferências (ms).
     * Definido a 0 para máxima velocidade — o ritmo real de captura
     * é controlado pelo intervalo de 250ms no CameraFragment.
     * Aumentar apenas se o telemóvel sobreaquecer.
     */
    private val INFERENCE_INTERVAL_MS = 0L

    private var inferenceJob: Job? = null
    private var lastSentFen: String = ""

    init {
        // Verificar se o modelo já está carregado (pode ser se o singleton persistiu)
        checkModelState()
    }

    // ─── Carregamento do modelo ──────────────────────────────────────────────

    /**
     * Verificar o estado do modelo e atualizar a UI.
     * O modelo pode já estar carregado se o singleton fez initialize()
     * no AppModule, ou pode precisar de tempo.
     */
    private fun checkModelState() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMessage = "A inicializar o modelo YOLO...") }

            // Polling para verificar quando o modelo fica pronto
            // (a inicialização assíncrona ocorre no AppModule)
            var attempts = 0
            while (!detector.isReady() && attempts < 100) {
                delay(100)
                attempts++
                if (attempts % 10 == 0) {
                    _uiState.update {
                        it.copy(loadingMessage = "A carregar o modelo de deteção...")
                    }
                }
            }

            if (detector.isReady()) {
                _uiState.update {
                    it.copy(
                        modelReady = true,
                        loadingMessage = ""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        loadingMessage = "Falha ao carregar o modelo",
                        statusMessage = "Erro: modelo não disponível"
                    )
                }
            }
        }
    }

    // ─── Inferência ──────────────────────────────────────────────────────────

    /**
     * Processar um frame da câmara.
     *
     * 1. Deteção YOLO do tabuleiro (segmentação)
     * 2. Extração dos 64 quadrados
     * 3. FenTracker: auto-calibração + deteção diferencial + validação chesslib
     * 4. Envio de FEN se em modo de transmissão
     */
    fun processFrame(bitmap: Bitmap) {
        // Saltar se o modelo não está pronto ou já está a processar
        if (!_uiState.value.modelReady) return
        if (inferenceJob?.isActive == true) return
        inferenceJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            if (INFERENCE_INTERVAL_MS > 0L) delay(INFERENCE_INTERVAL_MS)

            // Executar deteção de tabuleiro via YOLO
            val result = detector.detect(bitmap)

            if (result != null) {
                if (fenConverter.isValidPosition(result.squares)) {
                    val fen = fenConverter.toFen(result.squares)
                    val trackerStatus = fenConverter.getStatusText()
                    val isTracking = fenConverter.isTracking()

                    _uiState.update {
                        it.copy(
                            boardDetected = true,
                            squaresDetected = result.squares.size,
                            confidence = result.confidence,
                            currentFen = fen,
                            trackerStatus = trackerStatus,
                            isTracking = isTracking,
                            statusMessage = if (isTracking) "Tabuleiro detetado ✓" else "A calibrar deteção...",
                            boardCorners = result.corners,
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height
                        )
                    }

                }

                // Enviar apenas se o FEN mudou e estamos em modo de transmissão
                val currentFen = _uiState.value.currentFen
                if (currentFen.isNotEmpty() && currentFen != lastSentFen && _uiState.value.isBroadcasting) {
                    transmitFen(currentFen)
                    lastSentFen = currentFen
                }
            } else {
                _uiState.update {
                    it.copy(
                        boardDetected = false,
                        statusMessage = "Nenhum tabuleiro (Max Conf: %.2f)".format(detector.debugLastConfidence),
                        boardCorners = null // Elimina a AR visualização se perder
                    )
                }
            }
        }
    }

    // ─── Controlo de transmissão ──────────────────────────────────────────────

    /**
     * Iniciar transmissão para o browser via session_id.
     */
    fun startBroadcast(sessionId: String) {
        viewModelScope.launch {
            val token = tokenManager.accessToken.firstOrNull() ?: run {
                _uiState.update { it.copy(statusMessage = "Inicie sessão para transmitir") }
                return@launch
            }

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

    /**
     * Reset do tracker — novo jogo.
     */
    fun resetTracker() {
        fenConverter.reset()
        lastSentFen = ""
        _uiState.update {
            it.copy(
                currentFen = fenConverter.getCurrentFen(),
                trackerStatus = "",
                isTracking = false,
                statusMessage = "Tracker reiniciado — posição inicial"
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

    private fun transmitFen(fen: String) {
        val sessionId = _uiState.value.sessionId ?: return
        // WebSocket direto — ~10x mais rápido que REST
        gameRepository.sendFenWs(fen, sessionId)
    }

    override fun onCleared() {
        super.onCleared()
        gameRepository.disconnect()
    }
}
