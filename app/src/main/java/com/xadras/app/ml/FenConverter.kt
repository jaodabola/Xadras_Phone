package com.xadras.app.ml

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conversor de recortes de quadrados para notação FEN.
 *
 * Integra-se com o [FenTracker] para rastrear movimentos,
 * auto-corrigir takebacks, e manter o histórico completo
 * de FENs da partida para envio ao servidor.
 *
 * Pipeline:
 *   Frame → 64 quadrados classificados → FenTracker.update() → FEN validado
 */
@Singleton
class FenConverter @Inject constructor(
    private val tracker: FenTracker
) {

    /**
     * Processar um frame de 64 quadrados e atualizar o FEN.
     *
     * Delega ao FenTracker que:
     * 1. Auto-alinha a rotação da câmara (0º/90º/180º/270º)
     * 2. Valida movimentos via chesslib
     * 3. Deteta e corrige takebacks (até 2 jogadas para trás)
     * 4. Atualiza o FEN e o histórico interno
     *
     * @param squares Mapa de nome algébrico → recorte do quadrado
     * @return String FEN atualizado
     */
    fun toFen(squares: Map<String, SquareCrop>): String {
        return tracker.update(squares) ?: tracker.currentFen
    }

    /** Obter o FEN atual sem processar um novo frame. */
    fun getCurrentFen(): String = tracker.currentFen

    /** Obter texto de estado para mostrar na UI. */
    fun getStatusText(): String = tracker.statusText

    /** Verificar se o tracker está calibrado e a rastrear. */
    fun isTracking(): Boolean = tracker.isCalibrated

    /**
     * Obter o histórico completo de FENs da partida.
     * Útil para enviar ao servidor e permitir revisão posterior.
     * Este histórico é auto-corrigido em tempo real pelo takeback.
     */
    fun getFenHistory(): List<String> = tracker.fenHistory.toList()

    /** Número total de jogadas registadas na partida. */
    fun getMoveCount(): Int = tracker.fenHistory.size - 1

    /** Verificar se a posição detetada parece válida. */
    fun isValidPosition(squares: Map<String, SquareCrop>): Boolean {
        return squares.size == 64
    }

    /**
     * Reset do tracker — volta à posição inicial.
     * Útil quando se inicia um novo jogo.
     */
    fun reset() {
        tracker.reset()
    }
}