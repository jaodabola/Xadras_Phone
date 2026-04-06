package com.xadras.app.ml

import android.graphics.*
import android.util.Log
import kotlin.math.min

/**
 * Transformador de perspetiva e extrator de quadrados.
 *
 * Inspirado no renderer.py do Harmonica Chessboard.
 * Uma ÚNICA homografia dos cantos originais para a vista top-down,
 * sem dupla transformação (eliminando lag e acumulação de erro).
 */
class BoardWarper {

    companion object {
        private const val TAG = "BoardWarper"
    }

    /**
     * Transformar a região do tabuleiro numa imagem top-down 560×560 px.
     *
     * @param bitmap Frame original da câmara.
     * @param corners 8 floats [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y].
     * @return Bitmap 560×560 top-down ou null se a homografia falhar.
     */
    fun warpToTopDown(bitmap: Bitmap, corners: FloatArray): Bitmap? {
        val matrix = Matrix()
        val dst = floatArrayOf(
            0f, 0f,                                                         // TL
            BoardConfig.BOARD_PX.toFloat(), 0f,                             // TR
            BoardConfig.BOARD_PX.toFloat(), BoardConfig.BOARD_PX.toFloat(), // BR
            0f, BoardConfig.BOARD_PX.toFloat()                              // BL
        )

        val success = matrix.setPolyToPoly(corners, 0, dst, 0, 4)
        if (!success) {
            Log.w(TAG, "Falha ao calcular homografia")
            return null
        }

        val warped = Bitmap.createBitmap(BoardConfig.BOARD_PX, BoardConfig.BOARD_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return warped
    }

    /**
     * Dividir a imagem top-down em 64 quadrados de 70×70 px.
     * Nomeação algébrica: a1 = canto inferior-esquerdo, h8 = canto superior-direito.
     *
     * @return Mapa de nome algébrico → SquareCrop
     */
    fun extractSquares(topDown: Bitmap): Map<String, SquareCrop> {
        val squares = mutableMapOf<String, SquareCrop>()

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val chessRow = 7 - row  // row=0 (topo) = rank 8
                val name = "${BoardConfig.COL_NAMES[col]}${BoardConfig.ROW_NAMES[chessRow]}"

                val x = col * BoardConfig.SQUARE_PX
                val y = row * BoardConfig.SQUARE_PX
                val w = min(BoardConfig.SQUARE_PX, topDown.width - x)
                val h = min(BoardConfig.SQUARE_PX, topDown.height - y)

                if (w <= 0 || h <= 0) continue

                val crop = Bitmap.createBitmap(topDown, x, y, w, h)
                squares[name] = SquareCrop(name, col, chessRow, crop)
            }
        }
        return squares
    }
}
