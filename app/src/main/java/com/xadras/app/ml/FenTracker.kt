package com.xadras.app.ml

import android.util.Log
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rastreador Inteligente de FEN com Auto-Correção de Jogadas (Takeback).
 *
 * Capacidades:
 * 1. Classificação Visual → Regras de Xadrez (filtra mãos/oclusões)
 * 2. Auto-Alinhamento de Rotação da Câmara (0º/90º/180º/270º)
 * 3. Temporal Smoothing (confirmar jogada por N frames consecutivos)
 * 4. Takeback Automático: Se a câmara detetar que uma jogada anterior
 *    foi desfeita fisicamente na mesa, o sistema desfaz internamente
 *    até 2 jogadas e tenta re-explicar o estado visual com uma jogada
 *    legal alternativa. O histórico de FENs é corrigido em tempo real.
 */
@Singleton
class FenTracker @Inject constructor() {

    companion object {
        private const val TAG = "FenTracker"

        /** Frames consecutivos para confirmar uma jogada (1 = imediato, protegido por regras). */
        private const val CHANGE_FRAMES = 1

        /** Máximo de jogadas a desfazer ao tentar um takeback. */
        private const val MAX_UNDO_DEPTH = 2

        /** Score mínimo absoluto para aceitar um TAKEBACK (precisa de mais certeza). */
        private const val TAKEBACK_ACCEPT_SCORE = 52

        /** Score para considerar que NADA mudou (1 casa de ruído tolerada). */
        private const val NO_CHANGE_SCORE = 63

        /** Score para aceitar um takeback puro (posição recuada sem nova jogada).
         *  Mais baixo que NO_CHANGE porque o classificador pode ter ruído. */
        private const val PURE_TAKEBACK_SCORE = 60

        /** Margem mínima de melhoria para aceitar uma jogada forward. */
        private const val FORWARD_MARGIN = 1
    }

    // ─── Estado Interno (Memória Verdadeira do Jogo) ─────────────────────────

    private val board = Board()
    var currentFen: String = board.fen
        private set

    /** Histórico completo de FENs da partida (para enviar ao servidor). */
    val fenHistory: MutableList<String> = mutableListOf(board.fen)

    /** Histórico de movimentos internos (para poder desfazer). */
    private val moveHistory: MutableList<Move> = mutableListOf()

    val statusText: String
        get() {
            val moveNum = board.moveCounter
            val turn = if (board.sideToMove == com.github.bhlangonijr.chesslib.Side.WHITE) "Brancas" else "Pretas"
            return "Jogada $moveNum ($turn) - $pendingCount/$CHANGE_FRAMES conf."
        }

    val isCalibrated: Boolean = true

    // ─── Estado Transitório do Smoothing de Oclusão ──────────────────────────

    private var pendingMoveStr: String? = null
    private var pendingMove: Move? = null
    private var pendingCount: Int = 0
    private var pendingUndoDepth: Int = 0

    // Offset de Rotação (0=0º, 1=90º, 2=180º, 3=270º)
    private var boardRotation = 0
    private var pendingRotation = -1
    private var rotationConfirmCount = 0
    private val ROTATION_CONFIRM_FRAMES = 4  // Exigir 4 frames (~1s) antes de rodar

    // ─── Processamento Principal por Frame ───────────────────────────────────

    fun update(squaresMap: Map<String, SquareCrop>): String? {
        if (squaresMap.size < 64) return currentFen

        // ── 1. Calcular scores para todas as 4 rotações ─────────────────────
        val scores = IntArray(4)
        val predictionsByRot = Array(4) { rot ->
            val preds = extractPredictedStates(squaresMap, rot)
            scores[rot] = scoreBoardAgainstPrediction(board, preds)
            preds
        }

        val bestRotScore = scores.maxOrNull() ?: 0
        val bestRot = scores.indexOf(bestRotScore)
        val currentRotScore = scores[boardRotation]

        Log.d(TAG, "📊 Scores: rot0=${scores[0]} rot1=${scores[1]} rot2=${scores[2]} rot3=${scores[3]} | Atual=$boardRotation($currentRotScore) Melhor=$bestRot($bestRotScore)")

        // ── 2. Auto-Alinhamento de Rotação (RELATIVO) ───────────────────────
        // A rotação correta tem SEMPRE o score mais alto.
        // Se outra rotação pontua significativamente melhor → câmara rodou.
        val rotationMargin = bestRotScore - currentRotScore

        if (bestRot != boardRotation && rotationMargin >= 4) {
            if (pendingRotation == bestRot) {
                rotationConfirmCount++
                if (rotationConfirmCount >= ROTATION_CONFIRM_FRAMES) {
                    Log.i(TAG, "📷 [ROTAÇÃO ACEITE] $boardRotation → $bestRot (margem=$rotationMargin, score=$bestRotScore)")
                    boardRotation = bestRot
                    pendingRotation = -1
                    rotationConfirmCount = 0
                    resetPending()
                }
            } else {
                pendingRotation = bestRot
                rotationConfirmCount = 1
                Log.d(TAG, "📷 [Rotação pendente] candidata=$bestRot margem=$rotationMargin (1/$ROTATION_CONFIRM_FRAMES)")
            }
        } else {
            if (pendingRotation != -1) Log.d(TAG, "📷 [Rotação cancelada] margem insuficiente ($rotationMargin)")
            pendingRotation = -1
            rotationConfirmCount = 0
        }

        // BLOQUEIO: Se uma rotação está pendente, NÃO processar jogadas.
        if (pendingRotation != -1) {
            return currentFen
        }

        // ── 3. Usar a matriz visual retificada ──────────────────────────────
        val predictedStates = predictionsByRot[boardRotation]
        val currentScore = scores[boardRotation]

        // Nada mudou? (perfeito ou 1 casa de ruído)
        if (currentScore >= NO_CHANGE_SCORE) {
            resetPending()
            return currentFen
        }

        // ── 4. TAKEBACK primeiro (prioridade!) ──────────────────────────────
        // Se o score atual é muito baixo, é mais provável ser takeback do que forward.
        for (undoDepth in 1..MAX_UNDO_DEPTH) {
            val prevIndex = fenHistory.size - 1 - undoDepth
            if (prevIndex < 0) break

            val testBoard = Board()
            testBoard.loadFromFen(fenHistory[prevIndex])
            val recededScore = scoreBoardAgainstPrediction(testBoard, predictedStates)

            Log.d(TAG, "🔄 Takeback depth=$undoDepth: recededScore=$recededScore (precisa ≥$PURE_TAKEBACK_SCORE)")

            // Takeback puro (posição voltou atrás)
            if (recededScore >= PURE_TAKEBACK_SCORE && recededScore > currentScore + 1) {
                Log.i(TAG, "🔄 [TAKEBACK PURO] depth=$undoDepth score=$recededScore")
                return confirmTakeback(undoDepth, newMove = null)
            }

            // Takeback + nova jogada diferente
            val takebackResult = findBestLegalMove(testBoard, predictedStates)
            if (takebackResult != null && takebackResult.score >= TAKEBACK_ACCEPT_SCORE && takebackResult.score > currentScore + 2) {
                Log.i(TAG, "🔄 [TAKEBACK + JOGADA] depth=$undoDepth move=${takebackResult.move} score=${takebackResult.score}")
                return confirmTakeback(undoDepth, newMove = takebackResult.move)
            }
        }

        // ── 5. Tentar jogada forward (normal) ───────────────────────────────
        val forwardResult = findBestLegalMove(board, predictedStates)

        if (forwardResult != null && forwardResult.score > currentScore + FORWARD_MARGIN) {
            Log.i(TAG, "✅ [FORWARD] ${forwardResult.move} score=${forwardResult.score} (atual=$currentScore, margem=${forwardResult.score - currentScore})")
            return confirmMove(forwardResult.move, undoDepth = 0)
        }

        // ── 6. Nada explica a imagem → Oclusão ─────────────────────────────
        Log.d(TAG, "🚫 [Oclusão] currentScore=$currentScore, bestForward=${forwardResult?.score ?: -1}")
        resetPending()

        return currentFen
    }

    // ─── Confirmação de Jogada Normal (Forward) ─────────────────────────────

    private fun confirmMove(move: Move, undoDepth: Int): String? {
        val moveStr = move.toString()
        
        if (pendingMoveStr == moveStr && pendingUndoDepth == undoDepth) {
            pendingCount++
            if (pendingCount >= CHANGE_FRAMES) {
                // Aplicar undo se necessário (takeback)
                if (undoDepth > 0) {
                    applyUndo(undoDepth)
                }

                // Aplicar a jogada (usar o Move do pendingMove que foi criado no board correto,
                // ou recriá-lo a partir do board atual caso tenha havido undo)
                val actualMove = Move(move.from, move.to)
                board.doMove(actualMove)
                currentFen = board.fen
                moveHistory.add(actualMove)
                fenHistory.add(currentFen)

                if (undoDepth > 0) {
                    Log.i(TAG, "🔄 [Takeback + Jogada]: Desfeitas $undoDepth jogadas → Nova: $actualMove → $currentFen")
                } else {
                    Log.i(TAG, "🤖 [Movimento]: $actualMove → $currentFen")
                }

                resetPending()
            }
        } else {
            pendingMoveStr = moveStr
            pendingMove = move
            pendingUndoDepth = undoDepth
            pendingCount = 1
        }
        return currentFen
    }

    // ─── Confirmação de Takeback ─────────────────────────────────────────────

    private fun confirmTakeback(undoDepth: Int, newMove: Move?): String? {
        if (newMove != null) {
            return confirmMove(newMove, undoDepth)
        }

        // Takeback puro (jogador desfez mas ainda não jogou de novo)
        if (pendingMoveStr == null && pendingMove == null && pendingUndoDepth == undoDepth) {
            pendingCount++
            if (pendingCount >= CHANGE_FRAMES) {
                applyUndo(undoDepth)
                Log.i(TAG, "🔄 [Takeback Puro]: Desfeitas $undoDepth jogadas → $currentFen")
                resetPending()
            }
        } else {
            pendingMoveStr = null
            pendingMove = null
            pendingUndoDepth = undoDepth
            pendingCount = 1
        }
        return currentFen
    }

    // ─── Aplicar Undo no Board e Históricos ─────────────────────────────────

    private fun applyUndo(depth: Int) {
        for (i in 0 until depth) {
            if (fenHistory.size > 1 && moveHistory.isNotEmpty()) {
                moveHistory.removeAt(moveHistory.lastIndex)
                fenHistory.removeAt(fenHistory.lastIndex)
            }
        }
        // Reconstruir o board a partir do último FEN válido (seguro, sem undoMove)
        board.loadFromFen(fenHistory.last())
        currentFen = board.fen
    }

    // ─── Reset ──────────────────────────────────────────────────────────────

    fun reset() {
        board.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        currentFen = board.fen
        moveHistory.clear()
        fenHistory.clear()
        fenHistory.add(currentFen)
        boardRotation = 0
        resetPending()
        Log.i(TAG, "Tracker de FEN resetado para posição inicial.")
    }

    private fun resetPending() {
        pendingMoveStr = null
        pendingMove = null
        pendingUndoDepth = 0
        pendingCount = 0
    }

    // ─── Utilitários Internos ────────────────────────────────────────────────

    private data class MoveCandidate(val move: Move, val score: Int)

    /**
     * Encontra a jogada legal com melhor score visual a partir de um board.
     * Usa doMove/undoMove no mesmo objeto para evitar GC pressure
     * (instanciar centenas de Board() por frame causava lag no Android).
     */
    private fun findBestLegalMove(testBoard: Board, predicted: Map<Square, SquareState>): MoveCandidate? {
        var bestMove: Move? = null
        var bestScore = -1

        val legalMoves = testBoard.legalMoves()
        for (move in legalMoves) {
            testBoard.doMove(move)
            val score = scoreBoardAgainstPrediction(testBoard, predicted)
            testBoard.undoMove()
            
            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return if (bestMove != null) MoveCandidate(bestMove, bestScore) else null
    }

    /**
     * Compara um board contra o mapa de estados classificados pela IA.
     * +1 ponto por cada casa que coincide. Score máximo = 64.
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
     * aplicando virtualmente uma rotação no ângulo da ótica do espectador.
     */
    private fun extractPredictedStates(
        squaresMap: Map<String, SquareCrop>,
        rotation: Int
    ): Map<Square, SquareState> {
        val result = mutableMapOf<Square, SquareState>()
        for ((name, crop) in squaresMap) {
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
