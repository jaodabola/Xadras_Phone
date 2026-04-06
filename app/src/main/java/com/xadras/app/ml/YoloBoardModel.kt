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
 * Modelo YOLO TFLite para deteção de tabuleiro.
 *
 * Responsabilidade única: carregar o modelo e executar inferência.
 * Extraído do monolítico ChessBoardDetector.
 */
class YoloBoardModel(private val context: Context) {

    companion object {
        private const val TAG = "YoloBoardModel"
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var outputBuffers: HashMap<Int, Any>? = null
    private var inputBuffer: ByteBuffer? = null

    // Dimensões dos tensores de saída detetadas em runtime
    var detTensorIdx = 0; private set
    var protoTensorIdx = 1; private set
    var numDetections = 0; private set
    var detFeatures = 0; private set
    var detTransposed = false; private set
    var protoNHWC = true; private set

    /** Último valor de confiança para debug. */
    var debugLastConfidence = 0f; private set

    // ─── Inicialização ───────────────────────────────────────────────────────

    fun initialize() {
        try {
            val model = FileUtil.loadMappedFile(context, BoardConfig.MODEL_FILE)
            val options = Interpreter.Options()

            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU delegate ativado")
            } catch (t: Throwable) {
                options.setNumThreads(4)
                Log.w(TAG, "GPU delegate falhou, a usar CPU: ${t.message}")
            }

            interpreter = Interpreter(model, options)
            identifyOutputTensors()
            outputBuffers = prepareOutputBuffers(interpreter!!)
            Log.d(TAG, "Modelo carregado com sucesso")
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao carregar modelo: ${t.message}", t)
        }
    }

    fun isReady(): Boolean = interpreter != null

    // ─── Inferência ──────────────────────────────────────────────────────────

    /**
     * Resultado contendo a melhor deteção + acesso ao buffer de protótipos.
     */
    data class YoloResult(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val confidence: Float,
        val maskCoeffs: FloatArray,
        val protoBuffer: ByteBuffer
    )

    /**
     * Executar inferência YOLO no bitmap.
     * @return YoloResult com a melhor deteção, ou null se nenhuma encontrada.
     */
    fun runInference(bitmap: Bitmap): YoloResult? {
        val interp = interpreter ?: return null
        val buffers = outputBuffers ?: return null

        if (inputBuffer == null) {
            inputBuffer = ByteBuffer.allocateDirect(
                1 * BoardConfig.INPUT_SIZE * BoardConfig.INPUT_SIZE * 3 * 4
            ).apply { order(ByteOrder.nativeOrder()) }
        }

        preprocessBitmap(bitmap, inputBuffer!!)

        (buffers[detTensorIdx] as ByteBuffer).rewind()
        (buffers[protoTensorIdx] as ByteBuffer).rewind()

        interp.runForMultipleInputsOutputs(arrayOf(inputBuffer!!), buffers)

        val detBuffer = buffers[detTensorIdx] as ByteBuffer
        val protoBuffer = buffers[protoTensorIdx] as ByteBuffer

        return parseBestDetection(detBuffer, protoBuffer)
    }

    // ─── Privados ────────────────────────────────────────────────────────────

    private fun identifyOutputTensors() {
        val interp = interpreter ?: return
        for (i in 0 until interp.outputTensorCount) {
            val shape = interp.getOutputTensor(i).shape()
            Log.d(TAG, "Output[$i]: shape=${shape.toList()}")

            if (shape.any { it == BoardConfig.PROTO_SIZE }) {
                protoTensorIdx = i
                protoNHWC = shape.last() == BoardConfig.NUM_MASK_COEFFS
            } else {
                detTensorIdx = i
                if (shape.size >= 3) {
                    if (shape[1] < shape[2]) {
                        detTransposed = true
                        detFeatures = shape[1]
                        numDetections = shape[2]
                    } else {
                        detTransposed = false
                        numDetections = shape[1]
                        detFeatures = shape[2]
                    }
                }
            }
        }
        Log.d(TAG, "Deteções: idx=$detTensorIdx, dets=$numDetections, feats=$detFeatures, transposto=$detTransposed")
        Log.d(TAG, "Protótipos: idx=$protoTensorIdx, NHWC=$protoNHWC")
    }

    private fun prepareOutputBuffers(interp: Interpreter): HashMap<Int, Any> {
        val map = HashMap<Int, Any>()
        for (i in 0 until interp.outputTensorCount) {
            val tensor = interp.getOutputTensor(i)
            val buffer = ByteBuffer.allocateDirect(tensor.numBytes())
            buffer.order(ByteOrder.nativeOrder())
            map[i] = buffer
        }
        return map
    }

    private fun preprocessBitmap(bitmap: Bitmap, buffer: ByteBuffer) {
        val resized = Bitmap.createScaledBitmap(bitmap, BoardConfig.INPUT_SIZE, BoardConfig.INPUT_SIZE, true)
        buffer.rewind()
        val pixels = IntArray(BoardConfig.INPUT_SIZE * BoardConfig.INPUT_SIZE)
        resized.getPixels(pixels, 0, BoardConfig.INPUT_SIZE, 0, 0, BoardConfig.INPUT_SIZE, BoardConfig.INPUT_SIZE)
        for (pixel in pixels) {
            buffer.putFloat(Color.red(pixel) / 255f)
            buffer.putFloat(Color.green(pixel) / 255f)
            buffer.putFloat(Color.blue(pixel) / 255f)
        }
        buffer.rewind()
        if (resized !== bitmap) resized.recycle()
    }

    private fun parseBestDetection(detBuffer: ByteBuffer, protoBuffer: ByteBuffer): YoloResult? {
        detBuffer.rewind()

        var bestConf = 0f
        var bestIdx = -1

        for (i in 0 until numDetections) {
            val conf = readDetectionValue(detBuffer, i, 4)
            if (conf > bestConf) {
                bestConf = conf
                bestIdx = i
            }
        }

        debugLastConfidence = bestConf
        if (bestIdx < 0 || bestConf < BoardConfig.CONF_THRESHOLD) return null

        val x1 = readDetectionValue(detBuffer, bestIdx, 0)
        val y1 = readDetectionValue(detBuffer, bestIdx, 1)
        val x2 = readDetectionValue(detBuffer, bestIdx, 2)
        val y2 = readDetectionValue(detBuffer, bestIdx, 3)

        val maskStart = detFeatures - BoardConfig.NUM_MASK_COEFFS
        val coeffs = FloatArray(BoardConfig.NUM_MASK_COEFFS)
        for (j in 0 until BoardConfig.NUM_MASK_COEFFS) {
            coeffs[j] = readDetectionValue(detBuffer, bestIdx, maskStart + j)
        }

        protoBuffer.rewind()
        return YoloResult(x1, y1, x2, y2, bestConf, coeffs, protoBuffer)
    }

    private fun readDetectionValue(buffer: ByteBuffer, detIdx: Int, featIdx: Int): Float {
        val offset = if (detTransposed) {
            (featIdx * numDetections + detIdx) * 4
        } else {
            (detIdx * detFeatures + featIdx) * 4
        }
        return buffer.getFloat(offset)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
