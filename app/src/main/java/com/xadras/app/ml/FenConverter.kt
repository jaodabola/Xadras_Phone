package com.xadras.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Conversor de recortes de quadrados para notação FEN.
 *
 * Integra-se com o [FenTracker] para rastrear movimentos
 * via deteção diferencial de ocupância e validação por regras de xadrez.
 *
 * Pipeline:
 *   Frame → 64 quadrados → FenTracker.update() → FEN validado
 *
 * O FenTracker auto-calibra o threshold de ocupância no primeiro frame,
 * usando o conhecimento de que ranks 1-2 e 7-8 estão ocupados
 * e ranks 3-6 estão vazios na posição inicial.
 */
@Singleton
class FenConverter @Inject constructor(
    private val tracker: FenTracker
) {

    /** Indica se o modelo de classificação de peças está disponível. */
    private var pieceModelLoaded = false

    /**
     * Processar um frame de 64 quadrados e atualizar o FEN.
     *
     * Delega ao FenTracker que:
     * 1. Auto-calibra o threshold no primeiro frame
     * 2. Deteta mudanças de ocupância frame-a-frame
     * 3. Valida movimentos via chesslib
     * 4. Atualiza o FEN interno
     *
     * @param squares Mapa de nome algébrico → recorte do quadrado
     * @return String FEN atualizado
     */
    fun toFen(squares: Map<String, SquareCrop>): String {
        return tracker.update(squares) ?: tracker.currentFen
    }

    /**
     * Obter o FEN atual sem processar um novo frame.
     */
    fun getCurrentFen(): String = tracker.currentFen

    /**
     * Obter texto de estado para mostrar na UI.
     */
    fun getStatusText(): String = tracker.statusText

    /**
     * Verificar se o tracker está calibrado e a rastrear.
     */
    fun isTracking(): Boolean = tracker.isCalibrated

    /**
     * Verificar se a posição detetada parece válida.
     *
     * Verifica se o número de quadrados é 64 — a validação
     * real dos movimentos é feita pelo FenTracker via chesslib.
     */
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

    /**
     * Indica se o modelo de classificação de peças está carregado.
     * Enquanto não estiver, o FEN é gerado por deteção diferencial.
     */
    fun isPieceModelLoaded(): Boolean = pieceModelLoaded
}