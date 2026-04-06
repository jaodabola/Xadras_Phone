package com.xadras.app.ml

/**
 * Configuração centralizada do pipeline de deteção de tabuleiro.
 * Inspirado no config.py do Harmonica Chessboard.
 *
 * Todas as constantes partilhadas entre componentes ficam aqui.
 */
object BoardConfig {

    // ─── Modelo YOLO ─────────────────────────────────────────────────────────
    const val MODEL_FILE = "board_detect.tflite"
    const val INPUT_SIZE = 640
    const val CONF_THRESHOLD = 0.25f

    // ─── Classificador de Peças ──────────────────────────────────────────────
    const val PIECES_MODEL_FILE = "Piece_Detector.tflite"
    const val PIECES_INPUT_SIZE = 64

    // ─── Máscara de Segmentação ──────────────────────────────────────────────
    const val PROTO_SIZE = 160          // Resolução dos protótipos YOLO
    const val NUM_MASK_COEFFS = 32      // Coeficientes por deteção

    // ─── Renderização ────────────────────────────────────────────────────────
    const val BOARD_PX = 560            // Tamanho do top-down em pixéis
    const val SQUARE_PX = 70            // 560 / 8

    // ─── Estabilizador ───────────────────────────────────────────────────────
    const val EMA_ALPHA = 0.35f         // Convergência rápida (igual ao harmonica)
    const val MAX_LOST_FRAMES = 15      // Meio segundo de memória (~30fps)
    const val MAX_CORNER_JUMP_PX = 80f  // Rejeitar saltos > 80px entre frames

    // ─── Notação de Xadrez ───────────────────────────────────────────────────
    val COL_NAMES = charArrayOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')
    val ROW_NAMES = charArrayOf('1', '2', '3', '4', '5', '6', '7', '8')
}
