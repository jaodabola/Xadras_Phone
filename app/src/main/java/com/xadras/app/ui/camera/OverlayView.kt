package com.xadras.app.ui.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Vista transparente sobreposta ao Preview da Câmara para desenhar
 * Realidade Aumentada (AR), nomeadamente os cantos do tabuleiro e caixas.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cornerPaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 6f
        alpha = 150
        isAntiAlias = true
    }

    private var corners: FloatArray? = null
    private var scaleFactor: Float = 1f
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    /**
     * Atualiza os cantos detetados (tamanho original da imagem do modelo)
     * e os Fatores de Escala para converter para o tamanho da View no ecrã.
     */
    fun updateCorners(detectedCorners: FloatArray?, imageWidth: Int, imageHeight: Int) {
        if (detectedCorners == null || detectedCorners.size != 8 || width == 0 || height == 0) {
            this.corners = null
            invalidate()
            return
        }

        // CameraX PreviewView utiliza FILL_CENTER (CenterCrop) por defeito.
        // A escala deve manter as proporções preservadas.
        val scale = kotlin.math.max(
            width.toFloat() / imageWidth.toFloat(),
            height.toFloat() / imageHeight.toFloat()
        )

        this.scaleFactor = scale
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        this.offsetX = (width - scaledW) / 2f
        this.offsetY = (height - scaledH) / 2f

        this.corners = detectedCorners.clone()
        invalidate()
    }

    fun clear() {
        corners = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val c = corners ?: return

        // Mapear os 4 cantos preservando o aspeto através do CENTER_CROP da câmara
        val x1 = c[0] * scaleFactor + offsetX; val y1 = c[1] * scaleFactor + offsetY
        val x2 = c[2] * scaleFactor + offsetX; val y2 = c[3] * scaleFactor + offsetY
        val x3 = c[4] * scaleFactor + offsetX; val y3 = c[5] * scaleFactor + offsetY
        val x4 = c[6] * scaleFactor + offsetX; val y4 = c[7] * scaleFactor + offsetY

        // Apenas desenhar os vértices com as bolas Magentas (A pedido do utilizador)

        // Desenhar os vértices com as bolas Magentas
        val radius = 16f
        canvas.drawCircle(x1, y1, radius, cornerPaint)
        canvas.drawCircle(x2, y2, radius, cornerPaint)
        canvas.drawCircle(x3, y3, radius, cornerPaint)
        canvas.drawCircle(x4, y4, radius, cornerPaint)
    }
}
