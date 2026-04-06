package com.xadras.app.ml

import android.util.Log
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Processador de Máscara YOLO → 4 Cantos do Tabuleiro.
 *
 * Inspirado no board_detector.py do Harmonica Chessboard.
 * Usa approxPolyDP com epsilon progressivo para extrair os 4 cantos,
 * com fallback para minAreaRect. Sorting geométrico estável que não
 * troca cantos quando o tabuleiro roda.
 */
class MaskProcessor(private val model: YoloBoardModel) {

    companion object {
        private const val TAG = "MaskProcessor"
    }

    /**
     * Reconstruir a máscara de segmentação e encontrar os 4 cantos.
     *
     * @return FloatArray de 8 valores [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y]
     *         em coordenadas da imagem original, ou null se falhar.
     */
    fun findCorners(yoloResult: YoloBoardModel.YoloResult, origW: Int, origH: Int): FloatArray? {
        val mask = reconstructMask(yoloResult)
        return extractCornersFromMask(mask, origW.toFloat(), origH.toFloat())
    }

    // ─── Reconstrução da Máscara ─────────────────────────────────────────────

    private fun reconstructMask(result: YoloBoardModel.YoloResult): FloatArray {
        val protoBuffer = result.protoBuffer
        protoBuffer.rewind()

        val mask = FloatArray(BoardConfig.PROTO_SIZE * BoardConfig.PROTO_SIZE)

        // 1. sigmoid(coeffs · prototypes) → 160×160
        for (pixelIdx in 0 until BoardConfig.PROTO_SIZE * BoardConfig.PROTO_SIZE) {
            var sum = 0f
            for (c in 0 until BoardConfig.NUM_MASK_COEFFS) {
                val protoVal = readProtoValue(protoBuffer, c, pixelIdx)
                sum += result.maskCoeffs[c] * protoVal
            }
            mask[pixelIdx] = sigmoid(sum)
        }

        // 2. Recortar ao bounding box
        cropMaskToBBox(mask, result)

        return mask
    }

    private fun cropMaskToBBox(mask: FloatArray, result: YoloBoardModel.YoloResult) {
        var fx1 = result.x1; var fy1 = result.y1
        var fx2 = result.x2; var fy2 = result.y2

        if (max(fx1, fx2) <= 1.01f && max(fy1, fy2) <= 1.01f) {
            fx1 *= BoardConfig.PROTO_SIZE; fy1 *= BoardConfig.PROTO_SIZE
            fx2 *= BoardConfig.PROTO_SIZE; fy2 *= BoardConfig.PROTO_SIZE
        } else {
            val scale = BoardConfig.PROTO_SIZE.toFloat() / BoardConfig.INPUT_SIZE
            fx1 *= scale; fy1 *= scale; fx2 *= scale; fy2 *= scale
        }

        val bx1 = min(fx1, fx2).toInt().coerceIn(0, BoardConfig.PROTO_SIZE - 1)
        val bx2 = max(fx1, fx2).toInt().coerceIn(0, BoardConfig.PROTO_SIZE - 1)
        val by1 = min(fy1, fy2).toInt().coerceIn(0, BoardConfig.PROTO_SIZE - 1)
        val by2 = max(fy1, fy2).toInt().coerceIn(0, BoardConfig.PROTO_SIZE - 1)

        for (y in 0 until BoardConfig.PROTO_SIZE) {
            for (x in 0 until BoardConfig.PROTO_SIZE) {
                if (x < bx1 || x > bx2 || y < by1 || y > by2) {
                    mask[y * BoardConfig.PROTO_SIZE + x] = 0f
                }
            }
        }
    }

    private fun readProtoValue(buffer: ByteBuffer, channel: Int, pixelIdx: Int): Float {
        val offset = if (model.protoNHWC) {
            (pixelIdx * BoardConfig.NUM_MASK_COEFFS + channel) * 4
        } else {
            (channel * BoardConfig.PROTO_SIZE * BoardConfig.PROTO_SIZE + pixelIdx) * 4
        }
        return buffer.getFloat(offset)
    }

    // ─── Extração de Cantos (approxPolyDP — Harmonica Style) ─────────────────

    /**
     * Extrair 4 cantos da máscara usando approxPolyDP com epsilon progressivo.
     * Idêntico à lógica do _find_4_corners do harmonica board_detector.py.
     */
    private fun extractCornersFromMask(mask: FloatArray, origW: Float, origH: Float): FloatArray? {
        val scaleX = origW / BoardConfig.PROTO_SIZE
        val scaleY = origH / BoardConfig.PROTO_SIZE

        // Converter máscara float → Mat binário OpenCV
        val maskMat = Mat(BoardConfig.PROTO_SIZE, BoardConfig.PROTO_SIZE, CvType.CV_8UC1)
        val byteMask = ByteArray(BoardConfig.PROTO_SIZE * BoardConfig.PROTO_SIZE)
        var pixelCount = 0

        for (i in mask.indices) {
            if (mask[i] > 0.5f) {
                byteMask[i] = 255.toByte()
                pixelCount++
            } else {
                byteMask[i] = 0
            }
        }

        if (pixelCount < 100) {
            Log.w(TAG, "Máscara demasiado pequena: $pixelCount píxeis")
            maskMat.release()
            return null
        }

        maskMat.put(0, 0, byteMask)

        // Encontrar contornos
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(maskMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        maskMat.release()

        if (contours.isEmpty()) return null

        // Maior contorno
        var largestContour = contours[0]
        var maxArea = Imgproc.contourArea(largestContour)
        for (i in 1 until contours.size) {
            val area = Imgproc.contourArea(contours[i])
            if (area > maxArea) {
                maxArea = area
                largestContour = contours[i]
            }
        }

        val polygon = MatOfPoint2f(*largestContour.toArray())

        // approxPolyDP com epsilon progressivo (idêntico ao harmonica)
        val perimeter = Imgproc.arcLength(polygon, true)
        var corners4: Array<Point>? = null

        for (epsFrac in doubleArrayOf(0.02, 0.03, 0.04, 0.05, 0.06, 0.08, 0.10)) {
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(polygon, approx, epsFrac * perimeter, true)
            val pts = approx.toArray()
            approx.release()
            if (pts.size == 4) {
                corners4 = pts
                break
            }
        }

        // Fallback: minAreaRect (como no harmonica)
        if (corners4 == null) {
            val rect = Imgproc.minAreaRect(polygon)
            val boxPts = arrayOfNulls<Point>(4)
            rect.points(boxPts)
            corners4 = boxPts.filterNotNull().toTypedArray()
        }

        polygon.release()

        if (corners4.size != 4) return null

        // Sorting estável (idêntico ao harmonica _sort_corners_clockwise)
        val sorted = sortCornersClockwise(corners4)

        // Converter para coordenadas da imagem original
        return floatArrayOf(
            sorted[0].x.toFloat() * scaleX, sorted[0].y.toFloat() * scaleY,  // TL
            sorted[1].x.toFloat() * scaleX, sorted[1].y.toFloat() * scaleY,  // TR
            sorted[2].x.toFloat() * scaleX, sorted[2].y.toFloat() * scaleY,  // BR
            sorted[3].x.toFloat() * scaleX, sorted[3].y.toFloat() * scaleY   // BL
        )
    }

    /**
     * Ordenar 4 cantos em sentido horário: TL, TR, BR, BL.
     *
     * Algoritmo do harmonica _sort_corners_clockwise:
     * 1. Separar em top (Y < centróide) e bottom (Y >= centróide)
     * 2. Top: sort por X ascendente → [TL, TR]
     * 3. Bottom: sort por X descendente → [BR, BL]
     *
     * Este método é geometricamente estável e nunca troca cantos
     * mesmo quando o tabuleiro está na diagonal.
     */
    private fun sortCornersClockwise(corners: Array<Point>): Array<Point> {
        val cx = corners.map { it.x }.average()
        val cy = corners.map { it.y }.average()

        val top = corners.filter { it.y < cy }.sortedBy { it.x }
        val bottom = corners.filter { it.y >= cy }.sortedByDescending { it.x }

        // Se a separação top/bottom não deu 2+2, fallback por ângulo
        if (top.size != 2 || bottom.size != 2) {
            val sorted = corners.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
            return sorted.toTypedArray()
        }

        // TL, TR, BR, BL
        return arrayOf(top[0], top[1], bottom[0], bottom[1])
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}
