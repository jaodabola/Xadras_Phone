package com.xadras.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Piece label mapping — must match the order used when training the model.
 * Empty = 0, wP=1, wN=2, wB=3, wR=4, wQ=5, wK=6,
 *           bP=7, bN=8, bB=9, bR=10, bQ=11, bK=12
 */
object PieceLabels {
    const val EMPTY = 0
    // white pieces
    const val W_PAWN   = 1; const val W_KNIGHT = 2; const val W_BISHOP = 3
    const val W_ROOK   = 4; const val W_QUEEN  = 5; const val W_KING   = 6
    // black pieces
    const val B_PAWN   = 7; const val B_KNIGHT = 8; const val B_BISHOP = 9
    const val B_ROOK   = 10; const val B_QUEEN = 11; const val B_KING  = 12

    fun toFenChar(label: Int): Char = when (label) {
        W_PAWN -> 'P'; W_KNIGHT -> 'N'; W_BISHOP -> 'B'
        W_ROOK -> 'R'; W_QUEEN  -> 'Q'; W_KING   -> 'K'
        B_PAWN -> 'p'; B_KNIGHT -> 'n'; B_BISHOP -> 'b'
        B_ROOK -> 'r'; B_QUEEN  -> 'q'; B_KING   -> 'k'
        else   -> '.'
    }
}

data class DetectionResult(
    /** 8×8 array of piece label IDs, row 0 = rank 8 (black side) */
    val board: Array<IntArray>,
    val confidence: Float
)

@Singleton
class ChessBoardDetector @Inject constructor(private val context: Context) {

    // ⚠️ Place your trained model at: app/src/main/assets/chess.tflite
    private val MODEL_FILE = "chess.tflite"

    // Input size expected by the model (adjust to match your training config)
    private val INPUT_SIZE = 640
    private val NUM_CLASSES = 13      // 0 (empty) + 12 piece types
    private val GRID_SIZE = 8         // 8×8 board

    private var interpreter: Interpreter? = null

    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
        .build()

    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply { numThreads = 4 }
            interpreter = Interpreter(model, options)
            Log.d("ChessDetector", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("ChessDetector", "Failed to load model: ${e.message}")
        }
    }

    /**
     * Run inference on a camera bitmap.
     *
     * Adapt the output parsing below to match your model's actual output shape.
     * Current assumption: model outputs a [1, 8, 8, 13] tensor (one-hot per square).
     */
    fun detect(bitmap: Bitmap): DetectionResult? {
        val interp = interpreter ?: return null

        // Prepare input
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val inputBuffer: ByteBuffer = processedImage.buffer

        // Output buffer: [1][8][8][13]
        val output = Array(1) { Array(GRID_SIZE) { Array(GRID_SIZE) { FloatArray(NUM_CLASSES) } } }

        interp.run(inputBuffer, output)

        // Parse output: pick argmax per square
        val board = Array(GRID_SIZE) { row ->
            IntArray(GRID_SIZE) { col ->
                val scores = output[0][row][col]
                scores.indices.maxByOrNull { scores[it] } ?: PieceLabels.EMPTY
            }
        }

        // Average top-class confidence as a proxy for overall confidence
        val avgConfidence = board.flatMap { row ->
            row.map { label ->
                output[0][board.indexOf(row)][row.indexOf(label)][label]
            }
        }.average().toFloat()

        return DetectionResult(board, avgConfidence)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}