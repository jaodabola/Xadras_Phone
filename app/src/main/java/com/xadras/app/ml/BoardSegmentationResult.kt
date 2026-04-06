package com.xadras.app.ml

import android.graphics.Bitmap

/**
 * Resultado da segmentação do tabuleiro de xadrez pela rede YOLO.
 *
 * Contém a imagem top-down rectificada e os 64 recortes de quadrados,
 * prontos para classificação de peças quando o modelo estiver disponível.
 */
data class BoardSegmentationResult(
    /** Imagem top-down do tabuleiro (560×560 px). */
    val topDownImage: Bitmap,
    /** 64 recortes de quadrados, indexados por nome algébrico (ex: "e4"). */
    val squares: Map<String, SquareCrop>,
    /** Os 4 cantos do tabuleiro na imagem original [x0,y0, x1,y1, x2,y2, x3,y3]. */
    val corners: FloatArray,
    /** Confiança da deteção YOLO (0–1). */
    val confidence: Float
)

/**
 * Estado possível de um quadrado após classificação de peças.
 */
enum class SquareState {
    EMPTY,
    WHITE,
    BLACK
}

/**
 * Recorte de um quadrado individual do tabuleiro.
 */
data class SquareCrop(
    /** Nome algébrico do quadrado (ex: "a1", "h8"). */
    val name: String,
    /** Coluna do quadrado (0-7, a→h). */
    val col: Int,
    /** Linha do quadrado (0-7, 1→8). */
    val row: Int,
    /** Bitmap recortado do quadrado (70×70 px). */
    val bitmap: Bitmap,
    /** Estado classificado do quadrado (vazio, branco, preto). */
    var state: SquareState = SquareState.EMPTY
)
