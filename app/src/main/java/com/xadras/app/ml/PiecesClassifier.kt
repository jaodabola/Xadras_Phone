package com.xadras.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Classificador Leve de Peças de Xadrez (Empty vs Black vs White).
 *
 * Utiliza o modelo YOLOv26-cls (Piece_Detector.tflite) num loop muito rápido
 * para classificar o estado de 64 recortes (squares).
 */
class PiecesClassifier(private val context: Context) {

    companion object {
        private const val TAG = "PiecesClassifier"
        
        // Classes assumidas pela ordem do modelo. 
        // IMPORTANTE: Ajustar consoante a ordem alfabética que o export gerou.
        private const val CLASS_BLACK = 0
        private const val CLASS_EMPTY = 1 // Usualmente "Empty" fica em 1 ou 2 dependendo do alfabeto. Assumimos: Black=0, Empty=1, White=2 alfabeticamente. 
        private const val CLASS_WHITE = 2
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var inputBuffer: ByteBuffer? = null
    private var outputArray = Array(1) { FloatArray(3) } // Shape: [1, 3]

    /**
     * Inicializar o classificador de peças.
     */
    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, BoardConfig.PIECES_MODEL_FILE)
            val options = Interpreter.Options()

            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            } catch (t: Throwable) {
                options.setNumThreads(4)
                Log.w(TAG, "GPU delegate falhou para PiecesClassifier: ${t.message}")
            }

            interpreter = Interpreter(model, options)
            
            inputBuffer = ByteBuffer.allocateDirect(
                1 * BoardConfig.PIECES_INPUT_SIZE * BoardConfig.PIECES_INPUT_SIZE * 3 * 4
            ).apply { order(ByteOrder.nativeOrder()) }
            
            Log.d(TAG, "PiecesClassifier inicializado com sucesso.")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao carregar modelo PiecesClassifier: ${t.message}", t)
        }
    }

    /**
     * Classificar todos os 64 quadrados num loop super-rápido.
     * Atualiza a propriedade 'state' de cada SquareCrop in-place.
     */
    fun classifySquares(squares: Map<String, SquareCrop>) {
        val interp = interpreter ?: return

        for ((_, square) in squares) {
            // 1. Extrair região de interesse (ROI) - Parallax e Inner Padding
            val innerCrop = extractBottomInnerCrop(square.bitmap)
            
            // 2. Redimensionar para 64x64
            val resized = Bitmap.createScaledBitmap(
                innerCrop, 
                BoardConfig.PIECES_INPUT_SIZE, 
                BoardConfig.PIECES_INPUT_SIZE, 
                true
            )
            
            // 3. Normalizar e converter para ByteBuffer (Float32: 0..1)
            preprocessBitmap(resized, inputBuffer!!)
            
            // 4. Inferência [1, 64, 64, 3] -> [1, 3]
            interp.run(inputBuffer!!, outputArray)
            
            // 5. Encontrar a classe com maior probabilidade
            val confidences = outputArray[0]
            val maxIdx = confidences.indices.maxByOrNull { confidences[it] } ?: -1
            
            // 6. Mapear para SquareState
            square.state = parseState(maxIdx)
            
            // Limpeza de memória dos bitmaps temporários
            if (resized !== innerCrop) resized.recycle()
            if (innerCrop !== square.bitmap) innerCrop.recycle()
        }
    }

    /**
     * Aplica "Inner Padding" e "Bottom Half Focus" para remover parallax e linhas grelha.
     */
    private fun extractBottomInnerCrop(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height

        // Cortar 15% de cada lado (ignorar bordas esq/dir) = largura central 70%
        val startX = (w * 0.15f).toInt()
        val cropW = (w * 0.70f).toInt()

        // Focar na metade inferior (ignorar a "cabeça" das peças para evitar parallax vertical)
        // Ignorar os 35% de cima e 5% de baixo (sombras da linha da frente)
        val startY = (h * 0.35f).toInt()
        val cropH = (h * 0.60f).toInt()

        return Bitmap.createBitmap(src, startX, startY, cropW, cropH)
    }

    private fun preprocessBitmap(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            // O YOLO-cls usa RGB normalizado entre [0, 1]
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }
        buffer.rewind()
    }

    private fun parseState(idx: Int): SquareState {
        // Mapear os indices YOLO (Ajustar a ordem de acordo com as classes que lá tens)
        // Normalmente YOLO ordena alfabeticamente: 0: Black, 1: Empty, 2: White
        return when (idx) {
            CLASS_BLACK -> SquareState.BLACK
            CLASS_WHITE -> SquareState.WHITE
            CLASS_EMPTY -> SquareState.EMPTY
            else -> SquareState.EMPTY
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
