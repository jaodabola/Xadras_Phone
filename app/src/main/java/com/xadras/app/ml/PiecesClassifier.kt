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
 * Classificador de Peças de Xadrez (Empty / Black / White).
 *
 * Utiliza o modelo Piece_Detector.tflite exportado com batch=64:
 * todos os 64 quadrados são classificados numa ÚNICA chamada TFLite,
 * eliminando o overhead de 64 chamadas individuais ao intérprete.
 *
 * Speedup esperado vs. batch=1 sequencial: 5–15×.
 *
 * Pipeline por frame:
 *   1. Pré-processar 64 crops → batchInputBuffer (pré-alocado)
 *   2. Uma única chamada interp.run() → [64, 3] probabilidades
 *   3. Mapear resultados de volta a cada SquareCrop.state
 */
class PiecesClassifier(private val context: Context) {

    companion object {
        private const val TAG = "PiecesClassifier"
        private const val BATCH_SIZE = 64

        // Ordem alfabética gerada pelo export YOLO: Black=0, Empty=1, White=2
        private const val CLASS_BLACK = 0
        private const val CLASS_EMPTY = 1
        private const val CLASS_WHITE = 2

        private val SZ = BoardConfig.PIECES_INPUT_SIZE  // 64
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // ── Buffers pré-alocados (zero alocações por frame) ──────────────────────

    /** Buffer de input: [64, 64, 64, 3] float32. */
    private val batchInputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(BATCH_SIZE * SZ * SZ * 3 * 4)
        .apply { order(ByteOrder.nativeOrder()) }

    /** Output: [64, 3] — uma linha de probabilidades por quadrado. */
    private val batchOutputArray = Array(BATCH_SIZE) { FloatArray(3) }

    /** Array de pixels reutilizável — evita new IntArray() em cada quadrado. */
    private val pixelBuffer = IntArray(SZ * SZ)

    /** Bitmap de trabalho fixo 64×64 — reutilizado por quadrado via Canvas. */
    private val workBitmap: Bitmap = Bitmap.createBitmap(SZ, SZ, Bitmap.Config.ARGB_8888)
    private val workCanvas = android.graphics.Canvas(workBitmap)
    private val workDst = android.graphics.RectF(0f, 0f, SZ.toFloat(), SZ.toFloat())

    // ─── Inicialização ────────────────────────────────────────────────────────

    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, BoardConfig.PIECES_MODEL_FILE)
            val options = Interpreter.Options().apply {
                try {
                    addDelegate(GpuDelegate().also { gpuDelegate = it })
                    Log.d(TAG, "GPU delegate ativado para inferência batch")
                } catch (t: Throwable) {
                    setNumThreads(4)
                    Log.w(TAG, "GPU delegate falhou, usando 4 threads CPU: ${t.message}")
                }
            }

            interpreter = Interpreter(model, options).also { interp ->
                // Confirmar que o modelo aceita batch=64
                val inputShape = interp.getInputTensor(0).shape()
                Log.d(TAG, "Tensor input shape: ${inputShape.toList()}")
                if (inputShape[0] != BATCH_SIZE) {
                    Log.e(TAG, "ERRO: modelo tem batch=${inputShape[0]}, esperado $BATCH_SIZE. " +
                            "Re-exporta com model.export(format='tflite', batch=64).")
                }
            }

            Log.d(TAG, "PiecesClassifier batch=$BATCH_SIZE inicializado com sucesso.")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao inicializar PiecesClassifier: ${t.message}", t)
        }
    }

    // ─── Classificação ────────────────────────────────────────────────────────

    /**
     * Classificar todos os 64 quadrados em UMA única chamada TFLite.
     *
     * Alocações por frame: apenas 64 innerCrop (Bitmap.createBitmap inevitável).
     * Todos os outros buffers são reutilizados.
     */
    fun classifySquares(squares: Map<String, SquareCrop>) {
        val interp = interpreter ?: return
        if (squares.size != BATCH_SIZE) {
            Log.w(TAG, "Esperados $BATCH_SIZE quadrados, recebidos ${squares.size}")
            return
        }

        val squareList = squares.values.toList()
        val cropsToRecycle = ArrayList<Bitmap>(BATCH_SIZE)

        batchInputBuffer.rewind()

        // ── Passo 1: Pré-processar todos os 64 crops para o buffer de batch ──
        for (square in squareList) {
            val innerCrop = extractBottomInnerCrop(square.bitmap)
            // Escalar para 64×64 via Canvas no workBitmap reutilizável
            workCanvas.drawBitmap(innerCrop, null, workDst, null)
            // Escrever pixels normalizados no buffer (sem rewind — escreve sequencialmente)
            appendBitmapToBuffer(workBitmap)
            if (innerCrop !== square.bitmap) cropsToRecycle.add(innerCrop)
        }
        batchInputBuffer.rewind()

        // ── Passo 2: UMA única inferência para 64 quadrados ──
        interp.run(batchInputBuffer, batchOutputArray)

        // ── Passo 3: Mapear resultados de volta a cada SquareCrop ──
        for (i in squareList.indices) {
            val confidences = batchOutputArray[i]
            val maxIdx = confidences.indices.maxByOrNull { confidences[it] } ?: CLASS_EMPTY
            squareList[i].state = parseState(maxIdx)
        }

        // Limpeza de crops temporários
        cropsToRecycle.forEach { it.recycle() }
    }

    // ─── Utilitários privados ─────────────────────────────────────────────────

    /**
     * Focar na zona inferior-central do quadrado para reduzir parallax.
     *  - Cortar 15% dos lados esq/dir
     *  - Ignorar 35% de topo (cabeça das peças) e 5% de baixo (sombras)
     */
    private fun extractBottomInnerCrop(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val startX = (w * 0.15f).toInt()
        val cropW  = (w * 0.70f).toInt().coerceAtLeast(1)
        val startY = (h * 0.35f).toInt()
        val cropH  = (h * 0.60f).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(src, startX, startY, cropW, cropH)
    }

    /**
     * Escrever pixels de src normalizados [0,1] no batchInputBuffer.
     * NÃO faz rewind — acumula sequencialmente para o batch.
     * Usa pixelBuffer pré-alocado para evitar new IntArray().
     */
    private fun appendBitmapToBuffer(src: Bitmap) {
        src.getPixels(pixelBuffer, 0, SZ, 0, 0, SZ, SZ)
        for (pixel in pixelBuffer) {
            batchInputBuffer.putFloat(Color.red(pixel)   / 255f)
            batchInputBuffer.putFloat(Color.green(pixel) / 255f)
            batchInputBuffer.putFloat(Color.blue(pixel)  / 255f)
        }
    }

    private fun parseState(idx: Int): SquareState = when (idx) {
        CLASS_BLACK -> SquareState.BLACK
        CLASS_WHITE -> SquareState.WHITE
        else        -> SquareState.EMPTY
    }

    // ─── Limpeza ─────────────────────────────────────────────────────────────

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        if (!workBitmap.isRecycled) workBitmap.recycle()
    }
}
