package com.xadras.app.ml

import android.util.Log
import kotlin.math.sqrt

/**
 * Estabilizador temporal de cantos do tabuleiro.
 *
 * Tradução direta do stabilizer.py do Harmonica Chessboard.
 * Suaviza cantos com EMA, rejeita saltos grandes, e mantém
 * o último estado válido durante falhas breves de deteção.
 */
class BoardStabilizer {

    companion object {
        private const val TAG = "BoardStabilizer"
    }

    private var smoothedCorners: FloatArray? = null
    private var failCount: Int = 0

    /** Indica se existe um estado anterior válido. */
    val hasState: Boolean get() = smoothedCorners != null

    /** Último estado válido dos cantos. */
    val lastCorners: FloatArray? get() = smoothedCorners?.clone()

    /**
     * Actualizar os cantos com EMA (Exponential Moving Average).
     *
     * Lógica inspirada no stabilizer.py:
     * - Primeiro frame: aceitar directamente
     * - Frames seguintes: EMA com alpha = 0.35
     * - Rejeitar saltos > MAX_CORNER_JUMP_PX (os cantos ficam parados)
     *
     * @return Cantos suavizados (sempre 8 floats).
     */
    fun updateCorners(newCorners: FloatArray): FloatArray {
        val current = smoothedCorners

        if (current == null) {
            // Primeiro frame — aceitar directamente
            smoothedCorners = newCorners.clone()
            failCount = 0
            return newCorners.clone()
        }

        // Verificar consistência: rejeitar saltos grandes (glitches de 1 frame)
        // Mas se o salto se mantiver por 3 frames seguidos, o utilizador mexeu mesmo a câmara!
        val maxJump = computeMaxCornerJump(current, newCorners)
        if (maxJump > BoardConfig.MAX_CORNER_JUMP_PX) {
            Log.w(TAG, "Salto rejeitado: %.1f px (max: %.1f)".format(maxJump, BoardConfig.MAX_CORNER_JUMP_PX))
            failCount++
            if (failCount >= 3) {
                Log.i(TAG, "Câmara ajustada pelo utilizador. A fazer snap para nova posição.")
                reset()
                return updateCorners(newCorners) // Faz snap automático imediato
            }
            return current.clone()
        }

        // Aplicar EMA: alpha * novo + (1 - alpha) * anterior
        val alpha = BoardConfig.EMA_ALPHA
        for (i in 0 until 8) {
            current[i] = alpha * newCorners[i] + (1f - alpha) * current[i]
        }

        failCount = 0
        return current.clone()
    }

    /**
     * Marcar uma frame sem deteção.
     * Após MAX_LOST_FRAMES consecutivos, resetar o estado.
     */
    fun markFail() {
        failCount++
        if (failCount >= BoardConfig.MAX_LOST_FRAMES) {
            Log.w(TAG, "Reset após $failCount frames perdidos")
            reset()
        }
    }

    /** Reset completo do estabilizador. */
    fun reset() {
        smoothedCorners = null
        failCount = 0
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    private fun computeMaxCornerJump(a: FloatArray, b: FloatArray): Float {
        var maxDist = 0f
        for (i in 0 until 4) {
            val dx = a[i * 2] - b[i * 2]
            val dy = a[i * 2 + 1] - b[i * 2 + 1]
            val dist = sqrt(dx * dx + dy * dy)
            if (dist > maxDist) maxDist = dist
        }
        return maxDist
    }
}
