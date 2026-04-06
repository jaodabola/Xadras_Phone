package com.xadras.app.ml

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Alinhador de Grelha OpenCV.
 * 
 * Este módulo usa deteção Canny e Transformada de Hough para encontrar as linhas
 * fortes do tabuleiro e apertar a box estritamente aos limites jogáveis
 * (removendo a borda de madeira), ancorando a visão à física da lente.
 */
class OpenCVGridAligner {

    private val lastCorners = FloatArray(8)
    private var hasCorners = false
    private var lostFrames = 0

    companion object {
        const val MAX_LOST_FRAMES = 15 // Meio segundo de memória
    }

    /**
     * Aplica alinhamento OpenCV a uma imagem recortada superficialmente pelo YOLO.
     * Retorna os [TL_x, TL_y, TR_x, TR_y, BR_x, BR_y, BL_x, BL_y] corrigidos,
     * ou os anteriores se a frame falhar temporariamente.
     */
    fun alignGrid(bitmap: Bitmap): FloatArray? {
        val mat = Mat()
        val gray = Mat()
        val edges = Mat()
        val lines = Mat()

        try {
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            // Blur para remover ruído da madeira e reflexos
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 1.5)

            // Canny Edge Detection responsivo
            Imgproc.Canny(gray, edges, 50.0, 150.0)

            // Hough Lines Probabilistic
            // O tabuleiro tem grandes linhas verticais e horizontais (as grelhas)
            Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180.0, 50, 40.0, 10.0)

            // Se encontrou linhas, agrupá-las em "Horizontais" e "Verticais"
            val hLines = mutableListOf<DoubleArray>()
            val vLines = mutableListOf<DoubleArray>()

            for (i in 0 until lines.rows()) {
                val vec = lines.get(i, 0)
                if (vec != null && vec.size >= 4) {
                    val x1 = vec[0]
                    val y1 = vec[1]
                    val x2 = vec[2]
                    val y2 = vec[3]

                    val angle = abs(atan2(y2 - y1, x2 - x1) * 180.0 / Math.PI)
                    if (angle < 20 || angle > 160) {
                        hLines.add(vec)
                    } else if (angle > 70 && angle < 110) {
                        vLines.add(vec)
                    }
                }
            }

            // Se for estruturado, calcula intersecções min/max (o quadrado perfeito)
            if (hLines.isNotEmpty() && vLines.isNotEmpty()) {
                val minX = vLines.minOf { min(it[0], it[2]) }.toFloat()
                val maxX = vLines.maxOf { max(it[0], it[2]) }.toFloat()
                val minY = hLines.minOf { min(it[1], it[3]) }.toFloat()
                val maxY = hLines.maxOf { max(it[1], it[3]) }.toFloat()

                // Margin of error clipping
                val safeMinX = max(0f, minX)
                val safeMaxX = min(bitmap.width.toFloat(), maxX)
                val safeMinY = max(0f, minY)
                val safeMaxY = min(bitmap.height.toFloat(), maxY)

                // Validação de sanidade: a área tem de ser minimamente o tamanho do tabuleiro
                if ((safeMaxX - safeMinX) > bitmap.width * 0.5f && (safeMaxY - safeMinY) > bitmap.height * 0.5f) {
                    
                    lastCorners[0] = safeMinX; lastCorners[1] = safeMinY
                    lastCorners[2] = safeMaxX; lastCorners[3] = safeMinY
                    lastCorners[4] = safeMaxX; lastCorners[5] = safeMaxY
                    lastCorners[6] = safeMinX; lastCorners[7] = safeMaxY
                    
                    hasCorners = true
                    lostFrames = 0
                    return lastCorners.clone()
                }
            }
            
            // Falha na extração OpenCV perfeitamente limpa
            // Usar estado anterior se disponível
            if (hasCorners && lostFrames < MAX_LOST_FRAMES) {
                lostFrames++
                return lastCorners.clone()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Memory Leak Prevention - muito importante com NDK
            mat.release()
            gray.release()
            edges.release()
            lines.release()
        }

        hasCorners = false
        return null
    }

    fun reset() {
        hasCorners = false
        lostFrames = 0
    }
}
