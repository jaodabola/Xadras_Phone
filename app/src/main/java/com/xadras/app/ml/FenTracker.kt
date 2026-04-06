package com.xadras.app.ml

import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rastreador Inteligente de FEN.
 * 
 * Este módulo funde a Classificação Visual do modelo IA com as regras lógicas de xadrez,
 * de modo a filtrar ativamente ruído visual (ex: a mão do utilizador a interferir).
 * 
 * Lógica de Prevenção de Oclusão (Ghosts):
 * 1. Compara o mundo físico 64 classificado com a memória interna (tabuleiro).
 * 2. Se detetar desalinhamentos (fantasma/mão à frente), tenta explicar
 *    o desalinhamento através das jogadas válidas de xadrez (`board.legalMoves()`).
 * 3. Se nenhuma jogada válida conseguir explicar o "lixo" que a câmara viu (ex: uma mão), 
 *    descarta o frame assumindo ser oclusão passageira.
 * 4. Ao confirmar a mesma jogada legal por 2 frames consecutivos (temporal smoothing em 1fps), aceita a jogada.
 */
@Singleton
class FenTracker @Inject constructor() {

    companion object {
        private const val TAG = "FenTracker"

        // Em 1 FPS da câmara, são precisos 2 frames (~2 segundos) sustentados
        // a visualizar a mesma jogada legal para registar e enviar via API sem falso-positivos
        private const val CHANGE_FRAMES = 2 
    }

    // ─── Estado Interno (Memória Verdadeira do Jogo) ─────────────────────────

    private val board = Board()
    var currentFen: String = board.fen
        private set

    val statusText: String
        get() {
            val moveNum = board.moveCounter
            val turn = if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) "Brancas" else "Pretas"
            return "Jogada $moveNum ($turn) - $pendingCount/$CHANGE_FRAMES conf."
        }

    val isCalibrated: Boolean = true // Agora auto-calibra automaticamente por inteligência de classificador


    // ─── Estado Transitório do Smoothing de Oclusão ──────────────────────────

    private var pendingMove: Move? = null
    private var pendingCount: Int = 0

    // Offset de Rotação (0=0º, 1=90º, 2=180º, 3=270º)
    private var boardRotation = 0

    // ─── Processamento Principal por Frame ───────────────────────────────────

    fun update(squaresMap: Map<String, SquareCrop>): String? {
        if (squaresMap.size < 64) return currentFen

        // 1. Procurar Oclusões ou Rotações da Câmara (Auto-Alinhamento do Eixo Físico)
        val scores = IntArray(4)
        val predictionsByRot = Array(4) { rot ->
            val preds = extractPredictedStates(squaresMap, rot)
            scores[rot] = scoreBoardAgainstPrediction(board, preds)
            preds
        }

        val bestRotScore = scores.maxOrNull() ?: 0
        val bestRot = scores.indexOf(bestRotScore)

        // Limite dinâmico: Se estamos no início de um jogo (Posição Inicial), somos muito flexíveis
        // porque basta perceber qual a grande massa de peças e de que lado está. (Pode ter falhas do YOLO)
        // Se já estamos a meio do jogo, exigimos precisão quase perfeita (58/64) para não rodar a matriz acidentalmente.
        val isStartingPos = board.fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
        val thresholdForRotation = if (isStartingPos) 38 else 58

        // Se a perspetiva mudou radicalmente mas há uma rotação que faz match ótimo
        if (bestRot != boardRotation && bestRotScore >= thresholdForRotation) {
            Log.i(TAG, "📷 [Auto-Alinhamento] Eixo Trancado! Rotação: $bestRot (Score: $bestRotScore/64). Limite: $thresholdForRotation")
            boardRotation = bestRot
            resetPending()
        }

        // 2. Usar a matriz visual retificada contra o ângulo atual
        val predictedStates = predictionsByRot[boardRotation]
        val currentScore = scores[boardRotation]

        // Se a pontuação for muito alta (e.g. 62/64), é porque o jogo mal mudou, 
        // e se alguma casa difere, é uma oclusão pequena, então ignoramos.
        if (currentScore >= 62) {
            resetPending()
            return currentFen
        }

        // 3. Testa todas as JOGADAS VÁLIDAS DO XADREZ na posição atual e vê e resolve o mistério visual
        var bestMove: Move? = null
        var bestScore = -1

        for (move in board.legalMoves()) {
            val testBoard = board.clone()
            testBoard.doMove(move)
            
            val score = scoreBoardAgainstPrediction(testBoard, predictedStates)

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        // 4. Aceitar, Rejeitar ou Aguardar
        // Se a melhor jogada explicar a imagem melhor do que não jogar nada 
        // em pleno (pelo menos mais 2 pontos e > 60 score total significa match de contexto brutal)
        if (bestMove != null && bestScore > currentScore + 1) {
            if (pendingMove == bestMove) {
                pendingCount++
                if (pendingCount >= CHANGE_FRAMES) {
                    
                    // EFETUAR JOGADA TOTALMENTE VALIDADA E LIVRE DE OCLUSÕES E RUÍDOS DE MÃO DA CÂMARA!
                    board.doMove(bestMove)
                    currentFen = board.fen
                    Log.i(TAG, "🤖 [A.I. Movimento Sucesso]: $bestMove → $currentFen")
                    resetPending()
                }
            } else {
                // Primeira vez que vemos esta jogada, vamos aguardar pela frame seguinte p/ confirmar
                pendingMove = bestMove
                pendingCount = 1
            }
        } else {
            // Nem o board atual nem qualquer jogada explica o lixo que lá apareceu. 
            // Oclusão severa identificada => Descarta Lixo.
            Log.d(TAG, "🚫 [A.I. Oclusão / Ruído]: Mão ou ruído detetado, não obedece a regras. Ignorando quadro.")
            resetPending()
        }

        return currentFen
    }

    fun reset() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        currentFen = board.fen
        resetPending()
        Log.i(TAG, "Tracker de FEN resetado para posição inicial.")
    }

    private fun resetPending() {
        pendingMove = null
        pendingCount = 0
    }

    // ─── Utilitários Internos ────────────────────────────────────────────────

    /**
     * Compara um testBoard de xadrez absoluto contra o mapa de estados classificados 
     * pela rede neuronal, conferindo +1 ponto por cada acerto. O score máximo é 64.
     */
    private fun scoreBoardAgainstPrediction(testBoard: Board, predicted: Map<Square, SquareState>): Int {
        var score = 0
        for ((sq, visualState) in predicted) {
            val piece = testBoard.getPiece(sq)
            
            val expectedState = when {
                piece == Piece.NONE -> SquareState.EMPTY
                piece.name.contains("WHITE") -> SquareState.WHITE
                piece.name.contains("BLACK") -> SquareState.BLACK
                else -> SquareState.EMPTY
            }

            if (expectedState == visualState) {
                score++
            }
        }
        return score
    }

    private fun parseSquare(name: String): Square? {
        return try {
            Square.valueOf(name.uppercase())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Mapeia os crops visuais para a matriz do tabuleiro (A1 a H8), 
     * aplicando virtualmente uma rotação no ângulo da ótica do espectador (0º, 90º, 180º, 270º).
     */
    private fun extractPredictedStates(
        squaresMap: Map<String, SquareCrop>,
        rotation: Int
    ): Map<Square, SquareState> {
        val result = mutableMapOf<Square, SquareState>()
        for ((name, crop) in squaresMap) {
            
            // Coluna (a-h) = 0-7, Linha (1-8) = 0-7 original do array
            val fileIdx = name[0] - 'a'
            val rankIdx = name[1] - '1'

            val mapped = when (rotation) {
                0 -> Pair(fileIdx, rankIdx)
                1 -> Pair(rankIdx, 7 - fileIdx)
                2 -> Pair(7 - fileIdx, 7 - rankIdx)
                3 -> Pair(7 - rankIdx, fileIdx)
                else -> Pair(fileIdx, rankIdx)
            }
            
            val newFile = 'a' + mapped.first
            val newRank = '1' + mapped.second
            
            val sqName = "$newFile$newRank".uppercase()
            val sq = parseSquare(sqName)
            if (sq != null) {
                result[sq] = crop.state
            }
        }
        return result
    }
}
