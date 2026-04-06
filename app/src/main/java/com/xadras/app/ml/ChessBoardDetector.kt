package com.xadras.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orquestrador do pipeline de deteção de tabuleiro.
 *
 * Inspirado no pipeline.py do Harmonica Chessboard.
 * Cada passo é delegado a um componente especializado:
 *
 *   1. YoloBoardModel  — Inferência TFLite (YOLO segmentação)
 *   2. MaskProcessor   — Máscara → 4 Cantos (approxPolyDP)
 *   3. BoardStabilizer — EMA temporal + rejeição de saltos
 *   4. BoardWarper     — Homografia → top-down → 64 quadrados
 *
 * Pipeline:
 *   Frame → YOLO → Máscara → Cantos → Estabilizar → Warp → Quadrados
 */
@Singleton
class ChessBoardDetector @Inject constructor(private val context: Context) {

    companion object {
        private const val TAG = "BoardDetector"
    }

    // ─── Componentes do Pipeline ─────────────────────────────────────────────
    private val yoloModel = YoloBoardModel(context)
    private val maskProcessor = MaskProcessor(yoloModel)
    private val stabilizer = BoardStabilizer()
    private val warper = BoardWarper()
    private val piecesClassifier = PiecesClassifier(context)

    /** Último valor de confiança para debug na UI. */
    val debugLastConfidence: Float get() = yoloModel.debugLastConfidence

    // ─── Inicialização ───────────────────────────────────────────────────────

    fun initialize() {
        yoloModel.initialize()
        piecesClassifier.initialize()
    }
    
    fun isReady(): Boolean = yoloModel.isReady()

    // ─── Pipeline Principal ──────────────────────────────────────────────────

    /**
     * Processar um frame da câmara e detetar o tabuleiro de xadrez.
     *
     * @param bitmap Frame da câmara (qualquer tamanho).
     * @return Resultado com top-down, 64 quadrados e cantos, ou null.
     */
    fun detect(bitmap: Bitmap): BoardSegmentationResult? {
        // 1. Inferência YOLO
        val yoloResult = yoloModel.runInference(bitmap)

        // 2. Extrair cantos da máscara
        var corners: FloatArray? = null
        if (yoloResult != null) {
            corners = maskProcessor.findCorners(yoloResult, bitmap.width, bitmap.height)
        }

        // 3. Fallback: reutilizar últimos cantos se a IA piscar
        if (corners == null) {
            if (stabilizer.hasState) {
                stabilizer.markFail()
                corners = stabilizer.lastCorners
                if (corners != null) {
                    Log.d(TAG, "Fallback para últimos cantos estáveis")
                }
            }
            if (corners == null) return null
        }

        // 4. Estabilizar (EMA + rejeição de saltos)
        val stableCorners = stabilizer.updateCorners(corners)

        // 5. Transformar para top-down (UMA ÚNICA homografia)
        val topDown = warper.warpToTopDown(bitmap, stableCorners) ?: return null

        // 6. Extrair 64 quadrados
        val squares = warper.extractSquares(topDown)

        // 7. Classificar peças (White / Black / Empty)
        piecesClassifier.classifySquares(squares)

        return BoardSegmentationResult(topDown, squares, stableCorners, yoloResult?.confidence ?: 0f)
    }

    // ─── Reset ───────────────────────────────────────────────────────────────

    fun resetStabilizer() = stabilizer.reset()

    fun close() {
        yoloModel.close()
        piecesClassifier.close()
    }
}