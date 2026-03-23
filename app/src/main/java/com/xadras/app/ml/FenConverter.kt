package com.xadras.app.ml

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FenConverter @Inject constructor() {

    /**
     * Convert an 8×8 board (array of [PieceLabels]) to a full FEN string.
     *
     * @param board      8×8 array, board[0] = rank 8 (black's back rank)
     * @param activeColor 'w' or 'b' — defaults to 'w' (white to move)
     * @param castling   castling availability string (e.g. "KQkq")
     * @param enPassant  en passant target square (e.g. "e3") or "-"
     * @param halfMove   half-move clock (for 50-move rule)
     * @param fullMove   full-move counter
     */
    fun toFen(
        board: Array<IntArray>,
        activeColor: Char = 'w',
        castling: String = "KQkq",
        enPassant: String = "-",
        halfMove: Int = 0,
        fullMove: Int = 1
    ): String {
        val ranks = mutableListOf<String>()

        for (row in 0 until 8) {
            val sb = StringBuilder()
            var emptyCount = 0

            for (col in 0 until 8) {
                val label = board[row][col]
                if (label == PieceLabels.EMPTY) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(PieceLabels.toFenChar(label))
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            ranks.add(sb.toString())
        }

        val piecePlacement = ranks.joinToString("/")
        return "$piecePlacement $activeColor $castling $enPassant $halfMove $fullMove"
    }

    /**
     * Quick check: does the detected board look like a valid chess position?
     * Checks that both kings are present.
     */
    fun isValidPosition(board: Array<IntArray>): Boolean {
        var whiteKings = 0
        var blackKings = 0
        for (row in board) for (cell in row) {
            if (cell == PieceLabels.W_KING) whiteKings++
            if (cell == PieceLabels.B_KING) blackKings++
        }
        return whiteKings == 1 && blackKings == 1
    }
}