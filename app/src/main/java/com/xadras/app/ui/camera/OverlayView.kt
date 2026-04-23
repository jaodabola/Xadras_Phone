package com.xadras.app.ui.camera

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vista AR sobreposta ao Preview da Câmara.
 * Desenha os quatro cantos do tabuleiro detetado com estética
 * de Realidade Aumentada: suportes em L, contorno tracejado animado
 * e preenchimento semitransparente.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Cores ──────────────────────────────────────────────────────────────
    private val colorPrimary = Color.parseColor("#00E5FF")   // ciano AR
    private val colorAccent  = Color.parseColor("#FFFFFF")   // branco para os L
    private val colorFill    = Color.parseColor("#1A00E5FF") // ciano muito transparente

    // ── Pintas ─────────────────────────────────────────────────────────────

    /** Preenchimento semitransparente do interior do tabuleiro */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorFill
        style = Paint.Style.FILL
    }

    /** Contorno tracejado que percorre o perímetro */
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        alpha = 180
        pathEffect = DashPathEffect(floatArrayOf(18f, 10f), 0f)
    }

    /** Suportes em L nos cantos — linha exterior (halo branco) */
    private val bracketOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorAccent
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.SQUARE
    }

    /** Suportes em L nos cantos — linha interior (ciano) */
    private val bracketInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.SQUARE
    }

    /** Círculo de pulso no centro */
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorPrimary
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    // ── Estado ─────────────────────────────────────────────────────────────
    private var corners: FloatArray? = null
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /** Comprimento do braço de cada suporte em L (em dp) */
    private val bracketArmPx get() = 28f * resources.displayMetrics.density

    // ── Animação do dash offset ────────────────────────────────────────────
    private var dashOffset = 0f
    private val dashAnimator = ValueAnimator.ofFloat(0f, 280f).apply {
        duration = 2400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            dashOffset = it.animatedValue as Float
            borderPaint.pathEffect = DashPathEffect(floatArrayOf(18f, 10f), dashOffset)
            if (corners != null) invalidate()
        }
    }

    // ── Animação de pulso ──────────────────────────────────────────────────
    private var pulseRadius = 0f
    private var pulseAlpha = 0
    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            val t = anim.animatedValue as Float
            pulseRadius = t * 40f
            pulseAlpha  = ((1f - t) * 160).toInt()
            if (corners != null) invalidate()
        }
    }

    // ── API Pública ────────────────────────────────────────────────────────

    fun updateCorners(detectedCorners: FloatArray?, imageWidth: Int, imageHeight: Int) {
        if (detectedCorners == null || detectedCorners.size != 8 || width == 0 || height == 0) {
            corners = null; invalidate(); return
        }
        val scale = maxOf(width / imageWidth.toFloat(), height / imageHeight.toFloat())
        scaleFactor = scale
        offsetX = (width  - imageWidth  * scale) / 2f
        offsetY = (height - imageHeight * scale) / 2f
        corners = detectedCorners.clone()
        invalidate()
    }

    fun clear() { corners = null; invalidate() }

    // ── Ciclo de vida das animações ────────────────────────────────────────

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        dashAnimator.start()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        dashAnimator.cancel()
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    // ── Desenho ────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val c = corners ?: return

        val pts = mapCorners(c)
        val (x1, y1, x2, y2, x3, y3, x4, y4) = pts

        // 1. Preenchimento interior
        val fillPath = Path().apply {
            moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); lineTo(x4, y4); close()
        }
        canvas.drawPath(fillPath, fillPaint)

        // 2. Contorno tracejado animado
        canvas.drawPath(fillPath, borderPaint)

        // 3. Suportes em L em cada canto
        val corners2D = listOf(
            PointF(x1, y1) to listOf(PointF(x2, y2), PointF(x4, y4)),
            PointF(x2, y2) to listOf(PointF(x1, y1), PointF(x3, y3)),
            PointF(x3, y3) to listOf(PointF(x2, y2), PointF(x4, y4)),
            PointF(x4, y4) to listOf(PointF(x1, y1), PointF(x3, y3))
        )
        for ((corner, neighbors) in corners2D) {
            drawBracket(canvas, corner, neighbors[0], neighbors[1])
        }

        // 4. Pulso no centroide
        val cx = (x1 + x2 + x3 + x4) / 4f
        val cy = (y1 + y2 + y3 + y4) / 4f
        pulsePaint.alpha = pulseAlpha
        canvas.drawCircle(cx, cy, pulseRadius, pulsePaint)

        // Ponto central fixo
        pulsePaint.style = Paint.Style.FILL
        pulsePaint.alpha = 220
        canvas.drawCircle(cx, cy, 4f, pulsePaint)
        pulsePaint.style = Paint.Style.STROKE
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun mapCorners(c: FloatArray): FloatArray {
        fun mx(i: Int) = c[i * 2]     * scaleFactor + offsetX
        fun my(i: Int) = c[i * 2 + 1] * scaleFactor + offsetY
        return floatArrayOf(mx(0), my(0), mx(1), my(1), mx(2), my(2), mx(3), my(3))
    }

    /** Desenha um suporte em L num canto, orientado para o interior do quadrilátero. */
    private fun drawBracket(canvas: Canvas, corner: PointF, n1: PointF, n2: PointF) {
        val arm = bracketArmPx

        fun arm(toward: PointF): PointF {
            val dx = toward.x - corner.x
            val dy = toward.y - corner.y
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            return PointF(corner.x + dx / len * arm, corner.y + dy / len * arm)
        }

        val p1 = arm(n1)
        val p2 = arm(n2)

        // Halo branco
        canvas.drawLine(corner.x, corner.y, p1.x, p1.y, bracketOuterPaint)
        canvas.drawLine(corner.x, corner.y, p2.x, p2.y, bracketOuterPaint)
        // Linha ciano interior
        canvas.drawLine(corner.x, corner.y, p1.x, p1.y, bracketInnerPaint)
        canvas.drawLine(corner.x, corner.y, p2.x, p2.y, bracketInnerPaint)
    }

    /** Desestruturação de conveniência para 8 floats. */
    private operator fun FloatArray.component6() = this[5]
    private operator fun FloatArray.component7() = this[6]
    private operator fun FloatArray.component8() = this[7]
}